/**
 * 决策流图自动布局：dagre 分层算法（有向无环图专用）。
 * 流向从左到右分层展开，分支在层内均匀分布，连线总长最短化；
 * 节点尺寸取 React Flow 实测值（measured），无实测值时用兜底尺寸。
 */
import dagre from '@dagrejs/dagre';
import type { FlowEdge, FlowNode } from '../types/flowConfig';

/** 未渲染节点的兜底尺寸（与 custom-node 样式的典型宽高相当） */
const FALLBACK_NODE_WIDTH = 160;
const FALLBACK_NODE_HEIGHT = 60;

/** 层内节点最小间距（LR 布局中为垂直间距）与层间距离 */
const NODE_SEPARATION = 60;
const RANK_SEPARATION = 130;

export function getLayoutedElements(
  nodes: FlowNode[],
  edges: FlowEdge[],
): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const graph = new dagre.graphlib.Graph();
  graph.setDefaultEdgeLabel(() => ({}));
  graph.setGraph({ rankdir: 'LR', nodesep: NODE_SEPARATION, ranksep: RANK_SEPARATION, marginx: 40, marginy: 40 });

  for (const node of nodes) {
    const width = node.measured?.width ?? FALLBACK_NODE_WIDTH;
    const height = node.measured?.height ?? FALLBACK_NODE_HEIGHT;
    graph.setNode(node.id, { width, height });
  }
  for (const edge of edges) {
    graph.setEdge(edge.source, edge.target);
  }

  dagre.layout(graph);

  const laidOut = nodes.map((node) => {
    const { x, y, width, height } = graph.node(node.id);
    return { ...node, position: { x: x - width / 2, y: y - height / 2 } };
  });

  return { nodes: laidOut, edges };
}
