/**
 * StartNode - 开始节点组件
 * 绿色圆形节点，只有右侧输出 Handle
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import type { StartNodeData } from '../../../types/flowConfig';

type StartNodeProps = NodeProps<Node<StartNodeData, 'start'>>;

function StartNodeComponent({ data, isConnectable }: StartNodeProps) {
  return (
    <div className="custom-node custom-node-start">
      <Handle
        type="source"
        position={Position.Right}
        isConnectable={isConnectable}
        style={{ background: '#52c41a', width: 10, height: 10 }}
      />
      <div className="custom-node-title">{data.label}</div>
    </div>
  );
}

export default memo(StartNodeComponent);
