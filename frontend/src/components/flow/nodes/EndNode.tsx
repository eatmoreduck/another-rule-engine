/**
 * EndNode - 结束节点组件
 * 红色胶囊节点，左侧三个输入 Handle（上/中/下），
 * 多条路径汇入时自动排列会按来源方位分配入口，汇入线互不并拢
 */

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { PoweroffOutlined } from '@ant-design/icons';
import type { EndNodeData } from '../../../types/flowConfig';

type EndNodeProps = NodeProps<Node<EndNodeData, 'end'>>;

const INLET_HANDLE_STYLE = { background: '#ff4d4f', width: 10, height: 10 };

function EndNodeComponent({ data, isConnectable }: EndNodeProps) {
  return (
    <div className="custom-node custom-node-end">
      <Handle type="target" position={Position.Left} id="in-top" isConnectable={isConnectable}
        style={{ ...INLET_HANDLE_STYLE, top: '25%' }} />
      <Handle type="target" position={Position.Left} id="in-mid" isConnectable={isConnectable}
        style={INLET_HANDLE_STYLE} />
      <Handle type="target" position={Position.Left} id="in-bottom" isConnectable={isConnectable}
        style={{ ...INLET_HANDLE_STYLE, top: '75%' }} />
      <div className="custom-node-title">
        <PoweroffOutlined style={{ color: '#ff4d4f' }} />
        {data.label}
      </div>
      <div className="custom-node-detail">
        默认: {data.defaultAction}
      </div>
    </div>
  );
}

export default memo(EndNodeComponent);
