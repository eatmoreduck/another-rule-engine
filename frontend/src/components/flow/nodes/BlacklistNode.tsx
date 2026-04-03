import { memo } from 'react';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import { Tag } from 'antd';
import type { BlacklistNodeData } from '../../../types/flowConfig';
import { KEY_TYPE_LABELS } from '../../../types/flowConfig';

export default memo(function BlacklistNode({ data }: NodeProps<Node<BlacklistNodeData, 'blacklist'>>) {
  return (
    <div className="custom-node custom-node-blacklist">
      <Handle type="target" position={Position.Left}
        style={{ background: '#ff4d4f', width: 10, height: 10 }} />
      <div className="custom-node-title">{data.label}</div>
      <Tag color="red">黑名单</Tag>
      <div className="custom-node-detail">
        {KEY_TYPE_LABELS[data.keyType] ?? data.keyType ?? '未配置'}
      </div>
      <Handle type="source" position={Position.Right} id="pass"
        style={{ top: '50%', background: '#52c41a', width: 10, height: 10 }} />
      <span style={{ position: 'absolute', right: -28, top: '42%', fontSize: 10, color: '#52c41a', fontWeight: 600 }}>
        通过
      </span>
    </div>
  );
});
