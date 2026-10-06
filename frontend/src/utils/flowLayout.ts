/**
 * 决策流图自动布局：ELK layered 分层算法（交叉最小化显著优于 dagre）。
 * 流向从左到右分层展开，分支在层内均匀分布；节点尺寸取 React Flow
 * 实测值（measured），无实测值时用兜底尺寸。ELK 布局为异步计算。
 */
import ELK from 'elkjs/lib/elk.bundled.js';
import type { FlowEdge, FlowNode } from '../types/flowConfig';

/** 未渲染节点的兜底尺寸（与 custom-node 样式的典型宽高相当） */
const FALLBACK_NODE_WIDTH = 160;
const FALLBACK_NODE_HEIGHT = 60;

/** 层内节点间距与层间距离 */
const NODE_SEPARATION = 80;
const LAYER_SEPARATION = 170;

const elk = new ELK();

export async function getLayoutedElements(
  nodes: FlowNode[],
  edges: FlowEdge[],
): Promise<{ nodes: FlowNode[]; edges: FlowEdge[] }> {
  if (nodes.length === 0) return { nodes, edges };

  const layout = await elk.layout({
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'RIGHT',
      'elk.edgeRouting': 'ORTHOGONAL',
      'elk.layered.spacing.nodeNodeBetweenLayers': String(LAYER_SEPARATION),
      'elk.spacing.nodeNode': String(NODE_SEPARATION),
      'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
      'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
      'elk.padding': '[top=40,left=40,bottom=40,right=40]',
    },
    children: nodes.map((node) => ({
      id: node.id,
      width: node.measured?.width ?? FALLBACK_NODE_WIDTH,
      height: node.measured?.height ?? FALLBACK_NODE_HEIGHT,
    })),
    edges: edges.map((edge) => ({
      id: edge.id,
      sources: [edge.source],
      targets: [edge.target],
      // 长边尽量拉直，减少 zigzag 与局部线纠缠
      layoutOptions: { 'elk.layered.priority.straightness': '10' },
    })),
  });

  /** 节点布局结果与 ELK 正交布线的折点（图坐标系，与节点 position 同系） */
  const positionsById = new Map(
    (layout.children ?? []).map((child) => [child.id, { x: child.x ?? 0, y: child.y ?? 0 }]),
  );

  const waypointsById = new Map<string, { x: number; y: number }[]>();
  for (const edge of layout.edges ?? []) {
    const section = edge.sections?.[0];
    const bends = [...(section?.bendPoints ?? [])];
    if (bends.length === 0) continue;
    // 仅存通道折点；首尾与真实 handle 高度的对齐在 WaypointEdge 内完成
    // （那里才有 React Flow 传入的精确 handle 坐标，条件节点是/否 handle 偏上/下）
    waypointsById.set(edge.id ?? '', bends);
  }

  // 旁路分支统一向下展开（流程图惯例）：偏离主链层（开始节点所在高度）超过
  // 半个节点高的节点若全部位于上方，以主链层为轴整体垂直镜像——节点与连线
  // 路点同步翻转，布局等价
  const BRANCH_AXIS_THRESHOLD = FALLBACK_NODE_HEIGHT / 2;
  const startNode = nodes.find((n) => (n.data as { nodeType?: string }).nodeType === 'start');
  const mainY = startNode ? (positionsById.get(startNode.id)?.y ?? null) : null;
  if (mainY !== null) {
    const deviants = (layout.children ?? []).filter(
      (c) => Math.abs((c.y ?? mainY) - mainY) > BRANCH_AXIS_THRESHOLD,
    );
    const hasAbove = deviants.some((c) => (c.y ?? mainY) < mainY - BRANCH_AXIS_THRESHOLD);
    const hasBelow = deviants.some((c) => (c.y ?? mainY) > mainY + BRANCH_AXIS_THRESHOLD);
    if (hasAbove && !hasBelow) {
      // 注意 positionsById 存的是拷贝值，必须翻转它本身（改 layout.children 无效）
      for (const pos of positionsById.values()) {
        pos.y = 2 * mainY - pos.y;
      }
      for (const points of waypointsById.values()) {
        for (const p of points) p.y = 2 * mainY - p.y;
      }
    }
  }

  const laidOut = nodes.map((node) => ({
    ...node,
    position: positionsById.get(node.id) ? { ...positionsById.get(node.id)! } : node.position,
  }));

  // 多入口节点（end/merge，各有 上/中/下 三个入点）：入边按来源高度排序分配入口，
  // 汇入线在节点前保持分散、只在入口处收拢；单入口节点清除 targetHandle（匹配无 id handle）
  const MULTI_INLET_TYPES = new Set(['end', 'merge']);
  const INLET_IDS = ['in-top', 'in-mid', 'in-bottom'];
  const nodeTypeOf = (node: FlowNode) => (node.data as { nodeType?: string }).nodeType ?? '';
  const laidOutById = new Map(laidOut.map((n) => [n.id, n]));
  const inletByEdgeId = new Map<string, string>();
  const incomingByTarget = new Map<string, FlowEdge[]>();
  for (const edge of edges) {
    if (!MULTI_INLET_TYPES.has(nodeTypeOf(laidOutById.get(edge.target) ?? ({} as FlowNode)))) continue;
    const group = incomingByTarget.get(edge.target) ?? [];
    group.push(edge);
    incomingByTarget.set(edge.target, group);
  }
  for (const [targetId, group] of incomingByTarget) {
    group.sort((a, b) => {
      const ya = laidOutById.get(a.source)?.position.y ?? 0;
      const yb = laidOutById.get(b.source)?.position.y ?? 0;
      return ya - yb;
    });
    group.forEach((edge, index) => {
      const inlet =
        group.length === 1
          ? INLET_IDS[1]
          : INLET_IDS[Math.round((index * (INLET_IDS.length - 1)) / (group.length - 1))];
      inletByEdgeId.set(edge.id, inlet);
    });
  }

  const laidOutEdges = edges.map((edge) => {
    const waypoints = waypointsById.get(edge.id);
    const withWaypoints = waypoints ? { ...edge, data: { ...edge.data, waypoints } } : edge;
    const inlet = inletByEdgeId.get(edge.id);
    return inlet
      ? { ...withWaypoints, targetHandle: inlet }
      : withWaypoints.targetHandle
        ? { ...withWaypoints, targetHandle: undefined }
        : withWaypoints;
  });

  return { nodes: laidOut, edges: laidOutEdges };
}
