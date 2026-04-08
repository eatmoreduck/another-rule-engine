/**
 * LogicGroupEditor - 递归逻辑组编辑器
 * 支持 AND/OR 逻辑切换，递归嵌套子条件和子组
 */

import { useCallback } from 'react';
import { Button, Segmented, Space } from 'antd';
import { PlusOutlined, DeleteOutlined, GroupOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type {
  ConditionNode,
  LogicGroup,
  ConditionTreeNode,
} from '../../../types/ruleConfig';
import { createConditionNode, createLogicGroup } from '../../../types/ruleConfig';
import ConditionNodeEditor from './ConditionNodeEditor';
import '../../../styles/editor.css';

export interface LogicGroupEditorProps {
  group: LogicGroup;
  onChange: (group: LogicGroup) => void;
  onRemove?: () => void;
  depth: number;
}

export default function LogicGroupEditor({
  group,
  onChange,
  onRemove,
  depth,
}: LogicGroupEditorProps) {
  const { t } = useTranslation();

  const handleLogicChange = useCallback(
    (value: string) => {
      onChange({ ...group, logic: value as 'AND' | 'OR' });
    },
    [group, onChange],
  );

  const handleAddCondition = useCallback(() => {
    const newChildren = [...group.children, createConditionNode()];
    onChange({ ...group, children: newChildren });
  }, [group, onChange]);

  const handleAddSubGroup = useCallback(() => {
    const newChildren = [...group.children, createLogicGroup()];
    onChange({ ...group, children: newChildren });
  }, [group, onChange]);

  const handleRemoveChild = useCallback(
    (index: number) => {
      const newChildren = group.children.filter((_, i) => i !== index);
      onChange({ ...group, children: newChildren });
    },
    [group, onChange],
  );

  const handleChildChange = useCallback(
    (index: number, updated: ConditionTreeNode) => {
      const newChildren = group.children.map((child, i) =>
        i === index ? updated : child,
      );
      onChange({ ...group, children: newChildren });
    },
    [group, onChange],
  );

  const isOr = group.logic === 'OR';
  const groupClass = isOr ? 'condition-group group-or' : 'condition-group';

  // 随嵌套深度递增的背景色调
  const bgOpacity = Math.min(0.02 + depth * 0.02, 0.1);
  const bgColor = isOr
    ? `rgba(250, 173, 20, ${bgOpacity})`
    : `rgba(24, 144, 255, ${bgOpacity})`;

  return (
    <div
      className={groupClass}
      style={{ background: bgColor }}
    >
      {/* 顶部控制栏 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <Segmented
          options={[
            { label: 'AND', value: 'AND' },
            { label: 'OR', value: 'OR' },
          ]}
          value={group.logic}
          onChange={handleLogicChange}
          size="small"
        />
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          onClick={handleAddCondition}
        >
          {t('ruleConfig.addSubCondition')}
        </Button>
        <Button
          type="dashed"
          size="small"
          icon={<GroupOutlined />}
          onClick={handleAddSubGroup}
        >
          {t('ruleConfig.addSubGroup')}
        </Button>
        {onRemove && (
          <Button
            type="text"
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={onRemove}
          >
            {t('ruleConfig.deleteGroup')}
          </Button>
        )}
      </div>

      {/* 子节点列表 */}
      <div style={{ paddingLeft: depth * 20 + 'px' }}>
        {group.children.map((child, index) => {
          if (child.type === 'condition') {
            return (
              <ConditionNodeEditor
                key={child.id}
                node={child as ConditionNode}
                onChange={(updated) => handleChildChange(index, updated)}
                onRemove={() => handleRemoveChild(index)}
              />
            );
          }
          return (
            <LogicGroupEditor
              key={child.id}
              group={child as LogicGroup}
              onChange={(updated) => handleChildChange(index, updated)}
              onRemove={() => handleRemoveChild(index)}
              depth={depth + 1}
            />
          );
        })}
      </div>
    </div>
  );
}
