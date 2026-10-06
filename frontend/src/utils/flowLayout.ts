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
  const sizeById = new Map(
    nodes.map((node) => [node.id, { width: node.measured?.width ?? FALLBACK_NODE_WIDTH, height: node.measured?.height ?? FALLBACK_NODE_HEIGHT }]),
  );

  const waypointsById = new Map<string, { x: number; y: number }[]>();
  for (const edge of layout.edges ?? []) {
    const section = edge.sections?.[0];
    const bends = [...(section?.bendPoints ?? [])];
    if (bends.length === 0) continue;
    // 首尾对齐 handle 高度（左右中点）：先水平出手柄再拐进通道，尾段同理，
    // 消除 handle 与通道折点不同高产生的斜线段。
    // 条件节点的 true/false 出线 handle 偏上/偏下属于已知近似，中点对齐已消除主斜线。
    const sourceId = edge.sources?.[0] ?? '';
    const targetId = edge.targets?.[0] ?? '';
    const sourceY = (positionsById.get(sourceId)?.y ?? 0) + (sizeById.get(sourceId)?.height ?? 0) / 2;
    const targetY = (positionsById.get(targetId)?.y ?? 0) + (sizeById.get(targetId)?.height ?? 0) / 2;
    const last = bends[bends.length - 1];
    bends.unshift({ x: bends[0].x, y: sourceY });
    bends.push({ x: last.x, y: targetY });
    waypointsById.set(edge.id ?? '', bends);
  }

  const laidOut = nodes.map((node) => ({
    ...node,
    position: positionsById.get(node.id) ? { ...positionsById.get(node.id)! } : node.position,
  }));
  const laidOutEdges = edges.map((edge) => {
    const waypoints = waypointsById.get(edge.id);
    return waypoints ? { ...edge, data: { ...edge.data, waypoints } } : edge;
  });

  return { nodes: laidOut, edges: laidOutEdges };
}
