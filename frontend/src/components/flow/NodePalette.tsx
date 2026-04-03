/**
 * NodePalette - 节点拖拽面板
 * 左侧面板，显示可拖拽的节点类型，用户可拖拽到画布上添加节点
 */

import { useCallback, type DragEvent } from 'react';
import { QuestionCircleOutlined, ThunderboltOutlined, ApartmentOutlined, StopOutlined, SafetyCertificateOutlined, MergeCellsOutlined } from '@ant-design/icons';

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
  {
    type: 'blacklist',
    label: '黑名单',
    description: '检查是否在黑名单中',
    icon: <StopOutlined style={{ color: '#ff4d4f', fontSize: 20 }} />,
  },
  {
    type: 'whitelist',
    label: '白名单',
    description: '检查是否在白名单中',
    icon: <SafetyCertificateOutlined style={{ color: '#52c41a', fontSize: 20 }} />,
  },
  {
    type: 'merge',
    label: '合并分支',
    description: '合并多个分支',
    icon: <MergeCellsOutlined style={{ color: '#8c8c8c', fontSize: 20 }} />,
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
