/**
 * ConditionForm - 条件规则表单组件
 * 封装条件行管理（添加/删除/编辑）和默认动作配置
 * 支持实时 DSL 预览，通过 onChange 回调通知父组件配置变更
 */

import { useCallback, useMemo } from 'react';
import { Button, Select, Input, Space, Empty, Typography, Collapse, Card } from 'antd';
import { PlusOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import type { FormRuleConfig, ConditionActionRule, Action, Operator } from '../../../types/ruleConfig';
import { OPERATOR_LABELS, ACTION_LABELS } from '../../../types/ruleConfig';
import { generateGroovyFromForm } from '../../../utils/dslGenerator';
import '../../../styles/editor.css';

const { Title } = Typography;

const OPERATOR_OPTIONS = Object.entries(OPERATOR_LABELS).map(([value, label]) => ({
  value,
  label,
}));

const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({
  value,
  label,
}));

const LOGIC_GATE_OPTIONS = [
  { value: 'AND', label: 'AND' },
  { value: 'OR', label: 'OR' },
];

export interface ConditionFormProps {
  /** 当前条件规则列表 */
  conditions: ConditionActionRule[];
  /** 默认动作 */
  defaultAction: Action;
  /** 默认原因 */
  defaultReason: string;
  /** 配置变更回调，返回最新的 FormRuleConfig 和生成的 Groovy 脚本 */
  onChange: (config: FormRuleConfig, script: string) => void;
  /** 是否显示内联 DSL 预览折叠面板 */
  showPreview?: boolean;
}

export default function ConditionForm({
  conditions,
  defaultAction,
  defaultReason,
  onChange,
  showPreview = true,
}: ConditionFormProps) {
  // 根据当前条件生成配置和脚本
  const currentConfig = useMemo<FormRuleConfig>(() => ({
    defaultAction,
    defaultReason,
    rules: conditions,
  }), [conditions, defaultAction, defaultReason]);

  const generatedScript = useMemo(() => {
    return generateGroovyFromForm(currentConfig);
  }, [currentConfig]);

  // 通知父组件配置变更
  const notifyChange = useCallback(
    (newConditions: ConditionActionRule[], newDefaultAction: Action, newDefaultReason: string) => {
      const config: FormRuleConfig = {
        defaultAction: newDefaultAction,
        defaultReason: newDefaultReason,
        rules: newConditions,
      };
      const script = generateGroovyFromForm(config);
      onChange(config, script);
    },
    [onChange],
  );

  const addCondition = useCallback(() => {
    const newCondition: ConditionActionRule = {
      fieldName: '',
      operator: 'GT' as Operator,
      threshold: '',
      action: 'REJECT' as Action,
      reason: '',
    };
    notifyChange([...conditions, newCondition], defaultAction, defaultReason);
  }, [conditions, defaultAction, defaultReason, notifyChange]);

  const removeCondition = useCallback((index: number) => {
    const updated = conditions.filter((_, i) => i !== index);
    notifyChange(updated, defaultAction, defaultReason);
  }, [conditions, defaultAction, defaultReason, notifyChange]);

  const updateCondition = useCallback(
    (index: number, field: keyof ConditionActionRule, value: string | number) => {
      const updated = conditions.map((c, i) =>
        i === index ? { ...c, [field]: value } : c,
      );
      notifyChange(updated, defaultAction, defaultReason);
    },
    [conditions, defaultAction, defaultReason, notifyChange],
  );

  const handleDefaultActionChange = useCallback(
    (value: Action) => {
      notifyChange(conditions, value, defaultReason);
    },
    [conditions, defaultReason, notifyChange],
  );

  const handleDefaultReasonChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      notifyChange(conditions, defaultAction, e.target.value);
    },
    [conditions, defaultAction, notifyChange],
  );

  return (
    <div>
      {/* 条件规则列表 */}
      <div style={{ marginBottom: 16 }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
        }}>
          <Title level={5} style={{ margin: 0 }}>条件规则</Title>
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={addCondition}
            size="small"
          >
            添加条件
          </Button>
        </div>

        {conditions.length === 0 ? (
          <Empty
            description="暂无条件规则，点击上方按钮添加"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          conditions.map((cond, index) => (
            <div key={index} className="rule-condition-row">
              <Space wrap size="small" align="start">
                {/* 逻辑连接词（从第二行开始显示） */}
                {index > 0 && (
                  <Select
                    value={cond.logicGate ?? 'AND'}
                    onChange={(v) => updateCondition(index, 'logicGate', v)}
                    options={LOGIC_GATE_OPTIONS}
                    style={{ width: 80 }}
                  />
                )}
                <Input
                  placeholder="字段名"
                  value={cond.fieldName}
                  onChange={(e) => updateCondition(index, 'fieldName', e.target.value)}
                  style={{ width: 140 }}
                />
                <Select
                  value={cond.operator}
                  onChange={(v) => updateCondition(index, 'operator', v)}
                  options={OPERATOR_OPTIONS}
                  style={{ width: 120 }}
                />
                <Input
                  placeholder="阈值"
                  value={String(cond.threshold)}
                  onChange={(e) => updateCondition(index, 'threshold', e.target.value)}
                  style={{ width: 120 }}
                />
                <Select
                  value={cond.action}
                  onChange={(v) => updateCondition(index, 'action', v)}
                  options={ACTION_OPTIONS}
                  style={{ width: 110 }}
                />
                <Input
                  placeholder="原因说明"
                  value={cond.reason}
                  onChange={(e) => updateCondition(index, 'reason', e.target.value)}
                  style={{ width: 150 }}
                />
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeCondition(index)}
                />
              </Space>
            </div>
          ))
        )}
      </div>

      {/* 默认动作 */}
      <div style={{ marginTop: 16, padding: 12, background: '#fafafa', borderRadius: 6 }}>
        <Space>
          <span style={{ fontWeight: 600 }}>默认动作（无匹配条件时）：</span>
          <Select
            value={defaultAction}
            onChange={handleDefaultActionChange}
            options={ACTION_OPTIONS}
            style={{ width: 110 }}
          />
          <Input
            placeholder="默认原因"
            value={defaultReason}
            onChange={handleDefaultReasonChange}
            style={{ width: 200 }}
          />
        </Space>
      </div>

      {/* DSL 实时预览 */}
      {showPreview && conditions.length > 0 && (
        <Collapse
          style={{ marginTop: 16 }}
          items={[
            {
              key: 'dsl-preview',
              label: (
                <Space>
                  <EyeOutlined />
                  <span>DSL 实时预览</span>
                </Space>
              ),
              children: (
                <Card
                  size="small"
                  style={{ background: '#1e1e1e', border: 'none', borderRadius: 8 }}
                >
                  <pre className="script-preview-code" style={{ margin: 0 }}>
                    {generatedScript}
                  </pre>
                </Card>
              ),
            },
          ]}
        />
      )}
    </div>
  );
}
