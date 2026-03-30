/**
 * ConditionNode - 条件节点组件
 * 菱形风格矩形，背景淡黄色
 * 左侧 target Handle，右侧两个 source Handle（true/false 分支）
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import type { ConditionNodeData } from '../../../types/flowConfig';

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
  const opSymbol = OPERATOR_SYMBOLS[data.operator] ?? data.operator;

  return (
    <div className="custom-node custom-node-condition">
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={isConnectable}
        style={{ background: '#1890ff', width: 10, height: 10 }}
      />

      <div className="custom-node-title">{data.label}</div>
      <div className="custom-node-detail">
        {data.fieldName
          ? `${data.fieldName} ${opSymbol} ${data.threshold}`
          : '未配置条件'}
      </div>

      {/* True 分支输出 - 上方 */}
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

      {/* False 分支输出 - 下方 */}
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

export default memo(ConditionNodeComponent);
