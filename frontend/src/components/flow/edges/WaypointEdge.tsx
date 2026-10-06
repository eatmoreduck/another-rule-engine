/**
 * WaypointEdge - 携带 ELK 正交布线折点的边。
 * 有路点时按折线走 ELK 规划的"专用通道"（不穿过节点）；
 * 无路点（初次加载、尚未执行自动排列）时回退为内置 smoothstep 阶梯线。
 */
import { memo } from 'react';
import { BaseEdge, getSmoothStepPath, type EdgeProps } from '@xyflow/react';

interface WaypointData {
  waypoints?: { x: number; y: number }[];
}

function WaypointEdgeComponent({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  style,
  data,
}: EdgeProps) {
  const channelPoints = (data as WaypointData | undefined)?.waypoints ?? [];

  let path: string;
  if (channelPoints.length > 0) {
    // 首尾对齐真实 handle 高度：先水平出手柄再拐进通道，尾段先回 handle 高度再水平进 handle，
    // 消除通道折点与 handle 不同高产生的斜线段（条件节点是/否 handle 偏上/下）。
    const first = channelPoints[0];
    const last = channelPoints[channelPoints.length - 1];
    const aligned = [
      { x: first.x, y: sourceY },
      ...channelPoints.slice(1, -1),
      { x: last.x, y: targetY },
    ];
    path = [
      `M ${sourceX},${sourceY}`,
      ...aligned.map((p) => `L ${p.x},${p.y}`),
      `L ${targetX},${targetY}`,
    ].join(' ');
  } else {
    path = getSmoothStepPath({ sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, borderRadius: 12 })[0];
  }

  return <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />;
}

export default memo(WaypointEdgeComponent);
