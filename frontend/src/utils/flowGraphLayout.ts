import type { FlowNode } from '../types/flowConfig';

/**
 * 为流图节点补默认坐标。
 *
 * 后端 DSL 的 position 可空（API/脚本创建的流图无坐标属合法数据），
 * ReactFlow 渲染要求必有坐标——缺失时按网格顺序补默认布局，
 * 否则 getNodePositionWithOrigin 读取 position.x 直接崩溃。
 */
export function withDefaultPositions(nodes: FlowNode[]): FlowNode[] {
  return nodes.map((node, i) => ({
    ...node,
    position: node.position ?? { x: 80 + (i % 4) * 240, y: 60 + Math.floor(i / 4) * 150 },
  }));
}
