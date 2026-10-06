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
const NODE_SEPARATION = 60;
const LAYER_SEPARATION = 130;

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
    edges: edges.map((edge) => ({ id: edge.id, sources: [edge.source], targets: [edge.target] })),
  });

  const positionsById = new Map(
    (layout.children ?? []).map((child) => [child.id, { x: child.x ?? 0, y: child.y ?? 0 }]),
  );

  /** ELK 正交布线的折点（图坐标系，与节点 position 同系）→ 边上的路点 */
  const waypointsById = new Map<string, { x: number; y: number }[]>();
  for (const edge of layout.edges ?? []) {
    const section = edge.sections?.[0];
    if (!section) continue;
    const points = [...(section.bendPoints ?? [])];
    // 末段收尾点指向目标节点边缘，作为最后一个路点使折线贴合端点
    if (points.length > 0) points.push(section.endPoint);
    if (points.length > 0) waypointsById.set(edge.id ?? '', points);
  }

  const laidOut = nodes.map((node) => ({ ...node, position: positionsById.get(node.id) ?? node.position }));
  const laidOutEdges = edges.map((edge) => {
    const waypoints = waypointsById.get(edge.id);
    return waypoints ? { ...edge, data: { ...edge.data, waypoints } } : edge;
  });

  return { nodes: laidOut, edges: laidOutEdges };
}
