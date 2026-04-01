/**
 * RuleSetNode - 规则集节点组件
 * 容器节点，引用已有规则（AND/OR），双击可编辑引用规则列表
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { Tag } from 'antd';
import type { RuleSetNodeData } from '../../../types/flowConfig';

type RuleSetNodeProps = NodeProps<Node<RuleSetNodeData, 'ruleset'>>;

function RuleSetNodeComponent({ data, isConnectable }: RuleSetNodeProps) {
  const ruleKeys = data.ruleKeys ?? [];
  const logic = data.logic ?? 'AND';

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
          color={logic === 'AND' ? 'blue' : 'orange'}
          style={{ marginLeft: 6, fontSize: 10 }}
        >
          {logic}
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

      {/* True 分支输出 */}
      <Handle
        type="source"
        position={Position.Right}
        id="true"
        style={{ top: '30%', background: '#52c41a', width: 10, height: 10 }}
        isConnectable={isConnectable}
      />
      <span
        style={{
          position: 'absolute',
          right: -28,
          top: '22%',
          fontSize: 10,
          color: '#52c41a',
          fontWeight: 600,
        }}
      >
        是
      </span>

      {/* False 分支输出 */}
      <Handle
        type="source"
        position={Position.Right}
        id="false"
        style={{ top: '70%', background: '#ff4d4f', width: 10, height: 10 }}
        isConnectable={isConnectable}
      />
      <span
        style={{
          position: 'absolute',
          right: -28,
          top: '62%',
          fontSize: 10,
          color: '#ff4d4f',
          fontWeight: 600,
        }}
      >
        否
      </span>
    </div>
  );
}

export default memo(RuleSetNodeComponent);
