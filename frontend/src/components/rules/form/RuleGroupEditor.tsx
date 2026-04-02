/**
 * RuleGroupEditor - 单条规则编辑器
 * 包含条件树（条件节点或逻辑组）+ 动作 + 原因
 * 支持将单个条件转换为逻辑组
 */

import { useCallback } from 'react';
import { Input, Select, Button, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type {
  RuleGroup,
  Action,
  ConditionNode,
  LogicGroup,
} from '../../../types/ruleConfig';
import { ACTION_LABELS, createLogicGroup } from '../../../types/ruleConfig';
import ConditionNodeEditor from './ConditionNodeEditor';
import LogicGroupEditor from './LogicGroupEditor';
import '../../../styles/editor.css';

const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export interface RuleGroupEditorProps {
  rule: RuleGroup;
  index: number;
  onChange: (rule: RuleGroup) => void;
  onRemove: () => void;
}

export default function RuleGroupEditor({
  rule,
  index,
  onChange,
  onRemove,
}: RuleGroupEditorProps) {
  const handleActionChange = useCallback(
    (value: Action) => {
      onChange({ ...rule, action: value });
    },
    [rule, onChange],
  );

  const handleReasonChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onChange({ ...rule, reason: e.target.value });
    },
    [rule, onChange],
  );

  const handleConditionChange = useCallback(
    (condition: ConditionNode | LogicGroup) => {
      onChange({ ...rule, condition });
    },
    [rule, onChange],
  );

  const handleConvertToGroup = useCallback(() => {
    if (rule.condition.type === 'condition') {
      const group = createLogicGroup('AND', [rule.condition as ConditionNode]);
      onChange({ ...rule, condition: group });
    }
  }, [rule, onChange]);

  return (
    <div className="rule-group-card">
      {/* 规则头部 */}
      <div className="rule-group-header">
        <span style={{ fontWeight: 600, fontSize: 14 }}>
          规则 {index}
        </span>
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={onRemove}
        >
          删除规则
        </Button>
      </div>

      {/* 条件树编辑区域 */}
      <div style={{ marginBottom: 12 }}>
        {rule.condition.type === 'condition' ? (
          <>
            <ConditionNodeEditor
              node={rule.condition as ConditionNode}
              onChange={handleConditionChange}
              onRemove={() => { /* 根条件不允许单独删除 */ }}
            />
            <Button
              type="link"
              size="small"
              style={{ marginTop: 4, padding: 0 }}
              onClick={handleConvertToGroup}
            >
              转换为逻辑组
            </Button>
          </>
        ) : (
          <LogicGroupEditor
            group={rule.condition as LogicGroup}
            onChange={handleConditionChange}
            depth={0}
          />
        )}
      </div>

      {/* 动作和原因 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
        <span style={{ fontWeight: 500, whiteSpace: 'nowrap' }}>动作:</span>
        <Select
          value={rule.action}
          onChange={handleActionChange}
          options={ACTION_OPTIONS}
          style={{ width: 120 }}
        />
        <Input
          placeholder="原因说明"
          value={rule.reason}
          onChange={handleReasonChange}
          style={{ flex: 1 }}
        />
      </div>
    </div>
  );
}
