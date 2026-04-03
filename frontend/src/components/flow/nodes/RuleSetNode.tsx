/**
 * RuleSetNode - 规则集节点组件
 * 拒绝优先模式：任一引用规则返回 REJECT 则立即拒绝，否则走"通过"分支
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { Tag } from 'antd';
import type { RuleSetNodeData } from '../../../types/flowConfig';

type RuleSetNodeProps = NodeProps<Node<RuleSetNodeData, 'ruleset'>>;

function RuleSetNodeComponent({ data, isConnectable }: RuleSetNodeProps) {
  const ruleKeys = data.ruleKeys ?? [];

  return (
    <div className="custom-node custom-node-ruleset">
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={isConnectable}
        style={{ background: '#722ed1', width: 10, height: 10 }}
      />

      <div className="custom-node-title">
        {data.label}
        <Tag
          color="red"
          style={{ marginLeft: 6, fontSize: 10 }}
        >
          拒绝优先
        </Tag>
      </div>

      {ruleKeys.length === 0 ? (
        <div className="custom-node-detail">双击选择引用规则</div>
      ) : (
        <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 2 }}>
          {ruleKeys.slice(0, 3).map((key) => (
            <div key={key} style={{ padding: '1px 0' }}>{key}</div>
          ))}
          {ruleKeys.length > 3 && (
            <div style={{ color: '#bbb' }}>...还有 {ruleKeys.length - 3} 个规则</div>
          )}
        </div>
      )}

      {/* 通过分支输出（无拒绝时继续） */}
      <Handle
        type="source"
        position={Position.Right}
        id="pass"
        style={{ top: '50%', background: '#52c41a', width: 10, height: 10 }}
        isConnectable={isConnectable}
      />
      <span
        style={{
          position: 'absolute',
          right: -28,
          top: '42%',
          fontSize: 10,
          color: '#52c41a',
          fontWeight: 600,
        }}
      >
        通过
      </span>
    </div>
  );
}

export default memo(RuleSetNodeComponent);
