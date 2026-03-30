/**
 * ActionNode - 动作/决策节点组件
 * 矩形节点，根据 decision 显示不同颜色
 * PASS: 绿色, REJECT: 红色, MANUAL_REVIEW: 橙色
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import type { ActionNodeData } from '../../../types/flowConfig';

type ActionNodeProps = NodeProps<Node<ActionNodeData, 'action'>>;

const DECISION_COLORS: Record<string, { border: string; bg: string; handle: string }> = {
  PASS: { border: '#52c41a', bg: '#f6ffed', handle: '#52c41a' },
  REJECT: { border: '#ff4d4f', bg: '#fff2f0', handle: '#ff4d4f' },
  MANUAL_REVIEW: { border: '#faad14', bg: '#fffbe6', handle: '#faad14' },
};

const DECISION_LABELS: Record<string, string> = {
  PASS: '通过',
  REJECT: '拒绝',
  MANUAL_REVIEW: '人工审核',
};

function ActionNodeComponent({ data, isConnectable }: ActionNodeProps) {
  const colors = DECISION_COLORS[data.action] ?? DECISION_COLORS.REJECT;

  return (
    <div
      className="custom-node custom-node-action"
      style={{
        borderColor: colors.border,
        backgroundColor: colors.bg,
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={isConnectable}
        style={{ background: colors.handle, width: 10, height: 10 }}
      />

      <div className="custom-node-title">{data.label}</div>
      <div className="custom-node-detail">
        {DECISION_LABELS[data.action] ?? data.action}
        {data.reason ? `: ${data.reason}` : ''}
      </div>
    </div>
  );
}

export default memo(ActionNodeComponent);
