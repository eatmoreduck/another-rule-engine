/**
 * NodePalette - 节点拖拽面板
 * 左侧面板，显示可拖拽的节点类型，用户可拖拽到画布上添加节点
 */

import { useCallback, type DragEvent } from 'react';
import { QuestionCircleOutlined, ThunderboltOutlined, ApartmentOutlined, StopOutlined, SafetyCertificateOutlined, MergeCellsOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export default function NodePalette() {
  const { t } = useTranslation();

  const NODE_TYPES = [
    {
      type: 'condition',
      label: t('nodePalette.condition.label'),
      description: t('nodePalette.condition.description'),
      icon: <QuestionCircleOutlined style={{ color: '#1890ff', fontSize: 20 }} />,
    },
    {
      type: 'action',
      label: t('nodePalette.action.label'),
      description: t('nodePalette.action.description'),
      icon: <ThunderboltOutlined style={{ color: '#faad14', fontSize: 20 }} />,
    },
    {
      type: 'ruleset',
      label: t('nodePalette.ruleset.label'),
      description: t('nodePalette.ruleset.description'),
      icon: <ApartmentOutlined style={{ color: '#722ed1', fontSize: 20 }} />,
    },
    {
      type: 'blacklist',
      label: t('nodePalette.blacklist.label'),
      description: t('nodePalette.blacklist.description'),
      icon: <StopOutlined style={{ color: '#ff4d4f', fontSize: 20 }} />,
    },
    {
      type: 'whitelist',
      label: t('nodePalette.whitelist.label'),
      description: t('nodePalette.whitelist.description'),
      icon: <SafetyCertificateOutlined style={{ color: '#52c41a', fontSize: 20 }} />,
    },
    {
      type: 'merge',
      label: t('nodePalette.merge.label'),
      description: t('nodePalette.merge.description'),
      icon: <MergeCellsOutlined style={{ color: '#8c8c8c', fontSize: 20 }} />,
    },
  ];

  const onDragStart = useCallback((event: DragEvent, nodeType: string) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  }, []);

  return (
    <div className="node-palette">
      <div className="node-palette-title">{t('nodePalette.title')}</div>
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
