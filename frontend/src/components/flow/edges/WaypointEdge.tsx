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
  const waypoints = (data as WaypointData | undefined)?.waypoints ?? [];

  const path =
    waypoints.length > 0
      ? [
          `M ${sourceX},${sourceY}`,
          ...waypoints.map((p) => `L ${p.x},${p.y}`),
          `L ${targetX},${targetY}`,
        ].join(' ')
      : getSmoothStepPath({ sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, borderRadius: 12 })[0];

  return <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />;
}

export default memo(WaypointEdgeComponent);
