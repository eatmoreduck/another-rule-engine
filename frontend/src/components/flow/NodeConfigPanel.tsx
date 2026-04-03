/**
 * NodeConfigPanel - 节点属性配置面板
 * 右侧面板，选中节点后可编辑节点属性
 */

import { useCallback, useState, useEffect } from 'react';
import { Select, Input, Divider, Typography, Tag, message } from 'antd';
import type {
  FlowNode,
  ConditionNodeData,
  ActionNodeData,
  EndNodeData,
  RuleSetNodeData,
} from '../../types/flowConfig';
import { OPERATOR_LABELS, ACTION_LABELS, type Operator, type Action } from '../../types/ruleConfig';
import { getRulesForSelect } from '../../api/rules';
import type { RuleSelectOption } from '../../types/rule';

const { Text } = Typography;

interface NodeConfigPanelProps {
  node: FlowNode;
  onUpdate: (nodeId: string, newData: Partial<ConditionNodeData | ActionNodeData | EndNodeData | RuleSetNodeData>) => void;
}

/** 条件节点配置表单 */
function ConditionConfig({
  data,
  onUpdate,
}: {
  data: ConditionNodeData;
  onUpdate: (updates: Partial<ConditionNodeData>) => void;
}) {
  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>节点标签</Text>
        <Input
          value={data.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>特征字段</Text>
        <Input
          value={data.fieldName}
          onChange={(e) => onUpdate({ fieldName: e.target.value })}
          placeholder="例如: amount, riskScore"
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>比较运算符</Text>
        <Select
          value={data.operator}
          onChange={(v: Operator) => onUpdate({ operator: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          options={Object.entries(OPERATOR_LABELS).map(([key, label]) => ({
            value: key,
            label,
          }))}
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>阈值</Text>
        <Input
          value={String(data.threshold)}
          onChange={(e) => onUpdate({ threshold: e.target.value })}
          placeholder="比较值"
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
    </>
  );
}

/** 决策节点配置表单 */
function ActionConfig({
  data,
  onUpdate,
}: {
  data: ActionNodeData;
  onUpdate: (updates: Partial<ActionNodeData>) => void;
}) {
  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>节点标签</Text>
        <Input
          value={data.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>决策结果</Text>
        <Select
          value={data.action}
          onChange={(v: Action) => onUpdate({ action: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          options={Object.entries(ACTION_LABELS).map(([key, label]) => ({
            value: key,
            label,
          }))}
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>原因说明</Text>
        <Input.TextArea
          value={data.reason}
          onChange={(e) => onUpdate({ reason: e.target.value })}
          placeholder="决策原因"
          size="small"
          rows={2}
          style={{ marginTop: 4 }}
        />
      </div>
    </>
  );
}

/** 结束节点配置表单 */
function EndConfig({
  data,
  onUpdate,
}: {
  data: EndNodeData;
  onUpdate: (updates: Partial<EndNodeData>) => void;
}) {
  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>节点标签</Text>
        <Input
          value={data.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>默认决策</Text>
        <Select
          value={data.defaultAction}
          onChange={(v: Action) => onUpdate({ defaultAction: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          options={Object.entries(ACTION_LABELS).map(([key, label]) => ({
            value: key,
            label,
          }))}
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>默认原因</Text>
        <Input
          value={data.defaultReason}
          onChange={(e) => onUpdate({ defaultReason: e.target.value })}
          placeholder="默认决策原因"
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
    </>
  );
}

/** 规则集节点配置表单 - 引用已有规则 */
function RuleSetConfig({
  data,
  onUpdate,
}: {
  data: RuleSetNodeData;
  onUpdate: (updates: Partial<RuleSetNodeData>) => void;
}) {
  const [rules, setRules] = useState<RuleSelectOption[]>([]);
  const [loadingRules, setLoadingRules] = useState(false);

  useEffect(() => {
    let active = true;
    setLoadingRules(true);
    getRulesForSelect()
      .then((loadedRules) => {
        if (active) {
          setRules(loadedRules);
        }
      })
      .catch((error) => {
        console.error('Failed to load rules for ruleset node:', error);
        if (active) {
          message.error('加载引用规则失败，请稍后重试');
        }
      })
      .finally(() => {
        if (active) {
          setLoadingRules(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const ruleOptions = [
    ...rules,
    ...(data.ruleKeys ?? [])
      .filter((ruleKey) => !rules.some((rule) => rule.ruleKey === ruleKey))
      .map((ruleKey) => ({
        ruleKey,
        ruleName: '已引用规则',
        enabled: false,
        deleted: false,
        unavailable: true,
      })),
  ];

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>节点标签</Text>
        <Input value={data.label} onChange={(e) => onUpdate({ label: e.target.value })}
          size="small" style={{ marginTop: 4 }} />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12, padding: '8px 10px', background: '#fff2f0', borderRadius: 4, border: '1px solid #ffccc7' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          执行模式：<Text type="danger" strong>拒绝优先</Text>
        </Text>
        <div style={{ fontSize: 11, color: '#999', marginTop: 4 }}>
          任一引用规则返回拒绝，立即中断并返回拒绝决策
        </div>
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>引用规则</Text>
        <Select
          mode="multiple"
          value={data.ruleKeys ?? []}
          onChange={(value: string[]) => onUpdate({ ruleKeys: value })}
          loading={loadingRules}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          placeholder="选择要引用的规则"
          notFoundContent={loadingRules ? '加载中...' : '暂无可引用规则'}
          options={ruleOptions.map((rule) => ({
            value: rule.ruleKey,
            label: `${rule.ruleName} (${rule.ruleKey})${rule.unavailable ? '（当前不可用）' : rule.enabled ? '' : '（已禁用）'}`,
          }))}
          optionFilterProp="label"
          showSearch
        />
      </div>
      {data.ruleKeys && data.ruleKeys.length > 0 && (
        <div style={{ fontSize: 11, color: '#8c8c8c' }}>
          已引用 {data.ruleKeys.length} 条规则
        </div>
      )}
    </>
  );
}

export default function NodeConfigPanel({ node, onUpdate }: NodeConfigPanelProps) {
  const handleUpdate = useCallback(
    (updates: Partial<ConditionNodeData | ActionNodeData | EndNodeData | RuleSetNodeData>) => {
      onUpdate(node.id, updates);
    },
    [node.id, onUpdate],
  );

  const nodeType = node.data.nodeType;

  const typeLabel: Record<string, { text: string; color: string }> = {
    start: { text: '开始节点', color: 'green' },
    end: { text: '结束节点', color: 'red' },
    condition: { text: '条件节点', color: 'blue' },
    action: { text: '决策节点', color: 'orange' },
    ruleset: { text: '规则集节点', color: 'purple' },
  };
  const info = typeLabel[nodeType] ?? { text: nodeType, color: 'default' };

  return (
    <div style={{ padding: '12px', background: '#fff', borderLeft: '1px solid #f0f0f0', height: '100%', overflow: 'auto' }}>
      <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
        <Text strong>节点属性</Text>
        <Tag color={info.color}>{info.text}</Tag>
      </div>
      <Text type="secondary" style={{ fontSize: 11 }}>ID: {node.id}</Text>

      <Divider style={{ margin: '12px 0' }} />

      {nodeType === 'condition' && (
        <ConditionConfig data={node.data as ConditionNodeData} onUpdate={handleUpdate} />
      )}
      {nodeType === 'action' && (
        <ActionConfig data={node.data as ActionNodeData} onUpdate={handleUpdate} />
      )}
      {nodeType === 'end' && (
        <EndConfig data={node.data as EndNodeData} onUpdate={handleUpdate} />
      )}
      {nodeType === 'ruleset' && (
        <RuleSetConfig data={node.data as RuleSetNodeData} onUpdate={handleUpdate} />
      )}
      {nodeType === 'start' && (
        <div style={{ color: '#999', fontSize: 13 }}>开始节点无需配置</div>
      )}
    </div>
  );
}
