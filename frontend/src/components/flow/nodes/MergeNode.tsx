import { memo } from 'react';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import type { MergeNodeData } from '../../../types/flowConfig';

export default memo(function MergeNode({ data }: NodeProps<Node<MergeNodeData, 'merge'>>) {
  return (
    <div className="custom-node custom-node-merge">
      <Handle type="target" position={Position.Left}
        style={{ background: '#8c8c8c', width: 10, height: 10 }} />
      <div className="custom-node-title">{data.label}</div>
      <div className="custom-node-detail">合并分支</div>
      <Handle type="source" position={Position.Right}
        style={{ top: '50%', background: '#8c8c8c', width: 10, height: 10 }} />
    </div>
  );
});
