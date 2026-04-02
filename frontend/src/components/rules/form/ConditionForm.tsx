/**
 * ConditionForm - 单规则条件编辑器
 * 一条规则 = 一个条件树 (AND/OR 嵌套) + 匹配动作 + 不匹配动作
 * 支持空条件（始终执行）模式
 */

import { useCallback, useState } from 'react';
import { Button, Select, Input, Space, Typography, Alert } from 'antd';
import { PlusOutlined, ClearOutlined } from '@ant-design/icons';
import type { SingleRuleConfig, ConditionTreeNode, ConditionNode, LogicGroup, Action } from '../../../types/ruleConfig';
import { ACTION_LABELS, createConditionNode, createLogicGroup } from '../../../types/ruleConfig';
import { generateGroovyFromSingleRule } from '../../../utils/dslGenerator';
import ConditionNodeEditor from './ConditionNodeEditor';
import LogicGroupEditor from './LogicGroupEditor';

const { Title } = Typography;

const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({
  value,
  label,
}));

/** 判断条件树是否为空（无有效字段名） */
function isEmptyCondition(node: ConditionTreeNode): boolean {
  if (node.type === 'condition') {
    return !node.fieldName || node.fieldName.trim() === '';
  }
  if (node.type === 'group') {
    return node.children.length === 0 || node.children.every((c) => isEmptyCondition(c));
  }
  return false;
}

export interface ConditionFormProps {
  /** 当前规则配置（单规则） */
  config: SingleRuleConfig;
  /** 配置变更回调，返回最新的 SingleRuleConfig 和生成的 Groovy 脚本 */
  onChange: (config: SingleRuleConfig, script: string) => void;
}

export default function ConditionForm({
  config,
  onChange,
}: ConditionFormProps) {
  const { condition, action, reason, defaultAction, defaultReason } = config;
  const conditionEmpty = isEmptyCondition(condition);

  // editing=true 表示用户正在编辑条件（即使字段名还没填）
  // 当条件有实际内容时自动进入编辑态；无条件时通过按钮手动进入
  const [editing, setEditing] = useState(!conditionEmpty);

  const showEditor = !conditionEmpty || editing;

  const notifyChange = useCallback(
    (updated: Partial<SingleRuleConfig>) => {
      const newConfig = { ...config, ...updated };
      const script = generateGroovyFromSingleRule(newConfig);
      onChange(newConfig, script);
    },
    [config, onChange],
  );

  const handleActionChange = useCallback(
    (value: Action) => notifyChange({ action: value }),
    [notifyChange],
  );

  const handleReasonChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => notifyChange({ reason: e.target.value }),
    [notifyChange],
  );

  const handleDefaultActionChange = useCallback(
    (value: Action) => notifyChange({ defaultAction: value }),
    [notifyChange],
  );

  const handleDefaultReasonChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => notifyChange({ defaultReason: e.target.value }),
    [notifyChange],
  );

  const handleConvertToGroup = useCallback(() => {
    if (condition.type === 'condition') {
      const group = createLogicGroup('AND', [condition as ConditionNode]);
      notifyChange({ condition: group });
    }
  }, [condition, notifyChange]);

  const handleConditionChange = useCallback(
    (updated: ConditionTreeNode) => {
      notifyChange({ condition: updated });
    },
    [notifyChange],
  );

  /** 清空条件（回到"始终执行"模式） */
  const handleClearCondition = useCallback(() => {
    setEditing(false);
    notifyChange({ condition: createConditionNode() });
  }, [notifyChange]);

  /** 从"始终执行"模式进入条件编辑 */
  const handleAddCondition = useCallback(() => {
    setEditing(true);
    notifyChange({ condition: createConditionNode() });
  }, [notifyChange]);

  return (
    <div>
      {/* 条件树 */}
      <div style={{ marginBottom: 16 }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
        }}>
          <Title level={5} style={{ margin: 0 }}>条件</Title>
          {showEditor && !conditionEmpty && (
            <Button
              type="link"
              size="small"
              icon={<ClearOutlined />}
              onClick={handleClearCondition}
              style={{ color: '#999' }}
            >
              清空条件
            </Button>
          )}
        </div>

        {!showEditor ? (
          <Alert
            type="info"
            showIcon
            message="当前规则无条件限制，所有请求将执行默认动作"
            description={
              <Button
                type="dashed"
                size="small"
                icon={<PlusOutlined />}
                onClick={handleAddCondition}
                style={{ marginTop: 4 }}
              >
                添加条件
              </Button>
            }
            style={{ marginBottom: 8 }}
          />
        ) : condition.type === 'condition' ? (
          <>
            <ConditionNodeEditor
              node={condition as ConditionNode}
              onChange={handleConditionChange}
              onRemove={handleClearCondition}
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
            group={condition as LogicGroup}
            onChange={handleConditionChange}
            depth={0}
          />
        )}
      </div>

      {/* 匹配动作（仅在条件编辑模式时显示） */}
      {showEditor && (
        <div style={{ padding: 12, background: '#fff7e6', borderRadius: 6, border: '1px solid #ffe7ba' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontWeight: 600, color: '#389e0d' }}>满足条件时：</span>
            <Select value={action} onChange={handleActionChange} options={ACTION_OPTIONS} style={{ width: 120 }} />
            <Input placeholder="原因说明" value={reason} onChange={handleReasonChange} style={{ flex: 1 }} />
          </div>
        </div>
      )}

      {/* 不匹配动作(默认) */}
      <div style={{ marginTop: 8, padding: 12, background: '#fafafa', borderRadius: 6 }}>
        <Space>
          <span style={{ fontWeight: 600 }}>
            {!showEditor ? '执行动作' : '不满足条件时（默认）'}：
          </span>
          <Select value={defaultAction} onChange={handleDefaultActionChange} options={ACTION_OPTIONS} style={{ width: 110 }} />
          <Input placeholder="默认原因" value={defaultReason} onChange={handleDefaultReasonChange} style={{ width: 200 }} />
        </Space>
      </div>
    </div>
  );
}
