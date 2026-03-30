/**
 * EndNode - 结束节点组件
 * 红色圆形节点，只有左侧输入 Handle
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import type { EndNodeData } from '../../../types/flowConfig';

type EndNodeProps = NodeProps<Node<EndNodeData, 'end'>>;

function EndNodeComponent({ data, isConnectable }: EndNodeProps) {
  return (
    <div className="custom-node custom-node-end">
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={isConnectable}
        style={{ background: '#ff4d4f', width: 10, height: 10 }}
      />
      <div className="custom-node-title">{data.label}</div>
      <div className="custom-node-detail">
        默认: {data.defaultAction}
      </div>
    </div>
  );
}

export default memo(EndNodeComponent);
