/**
 * NodePalette - 节点拖拽面板
 * 左侧面板，显示可拖拽的节点类型，用户可拖拽到画布上添加节点
 */

import { useCallback, type DragEvent } from 'react';
import { QuestionCircleOutlined, ThunderboltOutlined, ApartmentOutlined } from '@ant-design/icons';

const NODE_TYPES = [
  {
    type: 'condition',
    label: '条件节点',
    description: '条件判断分支',
    icon: <QuestionCircleOutlined style={{ color: '#1890ff', fontSize: 20 }} />,
  },
  {
    type: 'action',
    label: '决策节点',
    description: '执行决策动作',
    icon: <ThunderboltOutlined style={{ color: '#faad14', fontSize: 20 }} />,
  },
  {
    type: 'ruleset',
    label: '规则集节点',
    description: '引用已有规则',
    icon: <ApartmentOutlined style={{ color: '#722ed1', fontSize: 20 }} />,
  },
];

export default function NodePalette() {
  const onDragStart = useCallback((event: DragEvent, nodeType: string) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  }, []);

  return (
    <div className="node-palette">
      <div className="node-palette-title">节点面板</div>
      {NODE_TYPES.map((item) => (
        <div
          key={item.type}
          className="node-palette-item"
          draggable
          onDragStart={(e) => onDragStart(e, item.type)}
        >
          {item.icon}
          <div>
            <div style={{ fontWeight: 500 }}>{item.label}</div>
            <div style={{ fontSize: 11, color: '#999' }}>{item.description}</div>
          </div>
        </div>
      ))}
    </div>
  );
}
