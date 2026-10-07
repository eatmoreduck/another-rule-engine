/**
 * ConditionNode - 条件节点组件
 * 多分支顺序匹配：每个分支一个右侧输出（按序标注），全部不命中走底部「兜底」出口
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { QuestionCircleOutlined } from '@ant-design/icons';
import type { ConditionNodeData } from '../../../types/flowConfig';
import { conditionElseHandle, normalizeConditionBranches } from '../../../types/flowConfig';

type ConditionNodeProps = NodeProps<Node<ConditionNodeData, 'condition'>>;

/** 运算符短符号映射 */
const OPERATOR_SYMBOLS: Record<string, string> = {
  EQ: '==',
  NE: '!=',
  GT: '>',
  GE: '>=',
  LT: '<',
  LE: '<=',
  CONTAINS: 'contains',
  NOT_CONTAINS: '!contains',
  IN: 'in',
  NOT_IN: '!in',
};

function ConditionNodeComponent({ data, isConnectable }: ConditionNodeProps) {
  const branches = normalizeConditionBranches(data);
  const elseId = conditionElseHandle(data);
  const total = branches.length + 1;
  const slotTop = (index: number) => `${((index + 1) / (total + 1)) * 100}%`;
  const summary = (b: (typeof branches)[number]) => {
    const op = OPERATOR_SYMBOLS[b.operator] ?? b.operator;
    return `${b.fieldName || '?'} ${op} ${b.threshold}`;
  };

  return (
    <div className="custom-node custom-node-condition">
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={isConnectable}
        style={{ background: '#1890ff', width: 10, height: 10 }}
      />

      <div className="custom-node-title">
        <QuestionCircleOutlined style={{ color: '#1890ff' }} />
        {data.label}
      </div>
      {branches.length === 0 ? (
        <div className="custom-node-detail">未配置条件</div>
      ) : (
        branches.map((branch, i) => (
          <div key={branch.id} className="custom-node-detail" style={{ textAlign: 'left', padding: '0 4px' }}>
            {i + 1}. {summary(branch)}
          </div>
        ))
      )}

      {branches.map((branch, i) => (
        <Handle
          key={branch.id}
          type="source"
          position={Position.Right}
          id={branch.id}
          style={{ top: slotTop(i), background: '#52c41a', width: 10, height: 10 }}
          isConnectable={isConnectable}
        />
      ))}
      {branches.map((_, i) => (
        <span
          key={`lbl-${i}`}
          style={{ position: 'absolute', right: -30, top: `calc(${slotTop(i)} - 6px)`, fontSize: 10, color: '#52c41a', fontWeight: 600 }}
        >
          条件{i + 1}
        </span>
      ))}

      {/* 兜底出口：所有条件都不命中时走这里 */}
      <Handle
        type="source"
        position={Position.Right}
        id={elseId}
        style={{ top: slotTop(branches.length), background: '#faad14', width: 10, height: 10 }}
        isConnectable={isConnectable}
      />
      <span
        style={{
          position: 'absolute',
          right: -30,
          top: `calc(${slotTop(branches.length)} - 6px)`,
          fontSize: 10,
          color: '#faad14',
          fontWeight: 600,
        }}
      >
        兜底
      </span>
    </div>
  );
}

export default memo(ConditionNodeComponent);
