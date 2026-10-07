/**
 * NodeConfigPanel - 节点属性配置面板
 * 右侧面板，选中节点后可编辑节点属性
 */

import { useCallback, useState, useEffect } from 'react';
import { Select, Input, Divider, Typography, Tag, message, Alert, Button } from 'antd';
import { useTranslation } from 'react-i18next';
import type {
  FlowNode,
  ConditionNodeData,
  ActionNodeData,
  EndNodeData,
  RuleSetNodeData,
  BlacklistNodeData,
  WhitelistNodeData,
} from '../../types/flowConfig';
import { KEY_TYPE_LABELS, normalizeConditionBranches, type ConditionBranch } from '../../types/flowConfig';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { OPERATOR_LABELS, ACTION_LABELS, type Operator, type Action } from '../../types/ruleConfig';
import { getRulesForSelect } from '../../api/rules';
import { getListKeys } from '../../api/nameList';
import type { RuleSelectOption } from '../../types/rule';
import type { FeatureResolvedInfo } from '../../types/featureCatalog';
import FeatureFieldInput from '../feature/FeatureFieldInput';

const { Text } = Typography;

interface NodeConfigPanelProps {
  node: FlowNode;
  onUpdate: (nodeId: string, newData: Partial<ConditionNodeData | ActionNodeData | EndNodeData | RuleSetNodeData | BlacklistNodeData | WhitelistNodeData>) => void;
}

const NUMERIC_OPERATORS: Operator[] = ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE'];
const BOOLEAN_OPERATORS: Operator[] = ['EQ', 'NE'];
const STRING_OPERATORS: Operator[] = ['EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS', 'IN', 'NOT_IN'];

function isNumericDataType(dataType: string): boolean {
  return ['NUMBER', 'INTEGER', 'LONG', 'DOUBLE', 'DECIMAL'].includes(dataType);
}

function isBooleanDataType(dataType: string): boolean {
  return dataType === 'BOOLEAN';
}

function getRecommendedOperators(dataType?: string): Operator[] | null {
  if (!dataType) {
    return null;
  }
  if (isNumericDataType(dataType)) {
    return NUMERIC_OPERATORS;
  }
  if (isBooleanDataType(dataType)) {
    return BOOLEAN_OPERATORS;
  }
  return STRING_OPERATORS;
}

/** 条件节点配置表单 */
function ConditionConfig({
  data,
  onUpdate,
}: {
  data: ConditionNodeData;
  onUpdate: (updates: Partial<ConditionNodeData>) => void;
}) {
  const { t } = useTranslation();
  const branches = normalizeConditionBranches(data);
  const [resolvedByBranch, setResolvedByBranch] = useState<Record<string, FeatureResolvedInfo | null>>({});

  const updateBranch = (id: string, patch: Partial<ConditionBranch>) => {
    onUpdate({ branches: branches.map((b) => (b.id === id ? { ...b, ...patch } : b)) });
  };
  const addBranch = () => {
    let n = branches.length + 1;
    let id = `b${n}`;
    while (branches.some((b) => b.id === id)) id = `b${++n}`;
    onUpdate({ branches: [...branches, { id, fieldName: '', operator: 'GT', threshold: 0 }] });
  };
  const removeBranch = (id: string) => {
    if (branches.length <= 1) return;
    onUpdate({ branches: branches.filter((b) => b.id !== id) });
  };

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.nodeLabel')}</Text>
        <Input
          value={data.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 6 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.branchListHint')}</Text>
      </div>
      {branches.map((branch, index) => {
        const resolved = resolvedByBranch[branch.id] ?? null;
        const recommendedOperators = getRecommendedOperators(resolved?.feature.dataType);
        const operatorOptions = Object.entries(OPERATOR_LABELS).map(([key, label]) => {
          const operatorKey = key as Operator;
          const disabled = !!recommendedOperators && !recommendedOperators.includes(operatorKey);
          const suffix = disabled ? ` (${t('nodeConfig.operatorNotRecommended')})` : '';
          return { value: key, label: `${label}${suffix}`, disabled };
        });
        const thresholdPlaceholder = (() => {
          const dataType = resolved?.feature.dataType;
          if (!dataType) return t('nodeConfig.thresholdPlaceholder');
          if (isNumericDataType(dataType)) return t('nodeConfig.thresholdNumberHint');
          if (isBooleanDataType(dataType)) return t('nodeConfig.thresholdBooleanHint');
          return t('nodeConfig.thresholdTextHint');
        })();

        return (
          <div key={branch.id} style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <Text strong style={{ fontSize: 12 }}>{t('nodeConfig.branchN', { n: index + 1 })}</Text>
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                disabled={branches.length <= 1}
                onClick={() => removeBranch(branch.id)}
              />
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.featureField')}</Text>
            <FeatureFieldInput
              value={branch.fieldName}
              onChange={(value) => updateBranch(branch.id, { fieldName: value })}
              onFeatureResolved={(info) => setResolvedByBranch((prev) => ({ ...prev, [branch.id]: info }))}
              placeholder={t('nodeConfig.featureFieldPlaceholder')}
              size="small"
              style={{ width: '100%', marginTop: 4 }}
            />
            <div style={{ marginTop: 6 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.comparisonOperator')}</Text>
              <Select
                value={branch.operator}
                onChange={(v: Operator) => updateBranch(branch.id, { operator: v })}
                size="small"
                style={{ width: '100%', marginTop: 4 }}
                options={operatorOptions}
              />
            </div>
            {resolved && !recommendedOperators?.includes(branch.operator) && (
              <div style={{ marginTop: 4, fontSize: 12, color: '#ad6800' }}>
                {t('nodeConfig.operatorMismatchHint', { dataType: resolved.feature.dataType })}
              </div>
            )}
            <div style={{ marginTop: 6 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.threshold')}</Text>
              <Input
                value={String(branch.threshold)}
                onChange={(e) => updateBranch(branch.id, { threshold: e.target.value })}
                placeholder={thresholdPlaceholder}
                size="small"
                style={{ marginTop: 4 }}
              />
            </div>
          </div>
        );
      })}
      <Button block size="small" type="dashed" icon={<PlusOutlined />} onClick={addBranch}>
        {t('nodeConfig.addBranch')}
      </Button>
      <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>{t('nodeConfig.branchMatchHint')}</div>
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
  const { t } = useTranslation();
  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.nodeLabel')}</Text>
        <Input
          value={data.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.decisionResult')}</Text>
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
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.reasonDescription')}</Text>
        <Input.TextArea
          value={data.reason}
          onChange={(e) => onUpdate({ reason: e.target.value })}
          placeholder={t('nodeConfig.reasonPlaceholder')}
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
  const { t } = useTranslation();
  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.nodeLabel')}</Text>
        <Input
          value={data.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          size="small"
          style={{ marginTop: 4 }}
        />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.defaultDecision')}</Text>
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
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.defaultReason')}</Text>
        <Input
          value={data.defaultReason}
          onChange={(e) => onUpdate({ defaultReason: e.target.value })}
          placeholder={t('nodeConfig.defaultReasonPlaceholder')}
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
  const { t } = useTranslation();
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
          message.error(t('nodeConfig.loadRulesFailed'));
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
        ruleName: t('nodeConfig.referencedRule'),
        enabled: false,
        deleted: false,
        unavailable: true,
      })),
  ];

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.nodeLabel')}</Text>
        <Input value={data.label} onChange={(e) => onUpdate({ label: e.target.value })}
          size="small" style={{ marginTop: 4 }} />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12, padding: '8px 10px', background: '#fff2f0', borderRadius: 4, border: '1px solid #ffccc7' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {t('nodeConfig.executionMode')}<Text type="danger" strong>{t('nodeConfig.rejectPriority')}</Text>
        </Text>
        <div style={{ fontSize: 11, color: '#999', marginTop: 4 }}>
          {t('nodeConfig.rejectPriorityDesc')}
        </div>
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.referenceRules')}</Text>
        <Select
          mode="multiple"
          value={data.ruleKeys ?? []}
          onChange={(value: string[]) => onUpdate({ ruleKeys: value })}
          loading={loadingRules}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          placeholder={t('nodeConfig.selectReferenceRules')}
          notFoundContent={loadingRules ? t('nodeConfig.loadingRules') : t('nodeConfig.noRulesAvailable')}
          options={ruleOptions.map((rule) => ({
            value: rule.ruleKey,
            label: `${rule.ruleName} (${rule.ruleKey})${rule.unavailable ? `(${t('nodeConfig.currentlyUnavailable')})` : rule.enabled ? '' : `(${t('common.disabled')})`}`,
          }))}
          optionFilterProp="label"
          showSearch
        />
      </div>
      {data.ruleKeys && data.ruleKeys.length > 0 && (
        <div style={{ fontSize: 11, color: '#8c8c8c' }}>
          {t('nodeConfig.referencedRuleCount', { count: data.ruleKeys.length })}
        </div>
      )}
    </>
  );
}

/** 黑名单节点配置表单 */
function BlacklistConfig({
  data,
  onUpdate,
}: {
  data: BlacklistNodeData;
  onUpdate: (updates: Partial<BlacklistNodeData>) => void;
}) {
  const { t } = useTranslation();
  const [listKeys, setListKeys] = useState<string[]>([]);
  const [loadingListKeys, setLoadingListKeys] = useState(false);

  useEffect(() => {
    let active = true;
    setLoadingListKeys(true);
    getListKeys()
      .then((keys) => {
        if (active) {
          setListKeys(keys);
        }
      })
      .catch((err) => {
        console.error('Failed to load listKeys for blacklist node:', err);
      })
      .finally(() => {
        if (active) {
          setLoadingListKeys(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.nodeLabel')}</Text>
        <Input value={data.label} onChange={(e) => onUpdate({ label: e.target.value })}
          size="small" style={{ marginTop: 4 }} />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.listKey')}</Text>
        <Select
          value={data.listKey || undefined}
          onChange={(v: string) => onUpdate({ listKey: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          placeholder={t('nodeConfig.selectListPlaceholder')}
          allowClear
          loading={loadingListKeys}
          options={listKeys.map(key => ({ value: key, label: key }))}
          showSearch
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.keyType')}</Text>
        <Select
          value={data.keyType || undefined}
          onChange={(v: string) => onUpdate({ keyType: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          placeholder={t('nodeConfig.selectKeyTypePlaceholder')}
          options={Object.entries(KEY_TYPE_LABELS).map(([key, label]) => ({
            value: key,
            label,
          }))}
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.featureBinding')}</Text>
        <Input
          value={data.fieldName || undefined}
          onChange={(e) => onUpdate({ fieldName: e.target.value || undefined })}
          size="small"
          style={{ marginTop: 4 }}
          placeholder={t('nodeConfig.featureBindingPlaceholder')}
          allowClear
        />
        <Text type="secondary" style={{ fontSize: 11, display: 'block', marginTop: 2 }}>
          {t('nodeConfig.featureBindingHint')}
        </Text>
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ padding: '8px 10px', background: '#fff2f0', borderRadius: 4, border: '1px solid #ffccc7' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {t('nodeConfig.blacklistMatchDesc')}
        </Text>
      </div>
    </>
  );
}

/** 白名单节点配置表单 */
function WhitelistConfig({
  data,
  onUpdate,
}: {
  data: WhitelistNodeData;
  onUpdate: (updates: Partial<WhitelistNodeData>) => void;
}) {
  const { t } = useTranslation();
  const [listKeys, setListKeys] = useState<string[]>([]);
  const [loadingListKeys, setLoadingListKeys] = useState(false);

  useEffect(() => {
    let active = true;
    setLoadingListKeys(true);
    getListKeys()
      .then((keys) => {
        if (active) {
          setListKeys(keys);
        }
      })
      .catch((err) => {
        console.error('Failed to load listKeys for whitelist node:', err);
      })
      .finally(() => {
        if (active) {
          setLoadingListKeys(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.nodeLabel')}</Text>
        <Input value={data.label} onChange={(e) => onUpdate({ label: e.target.value })}
          size="small" style={{ marginTop: 4 }} />
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.listKey')}</Text>
        <Select
          value={data.listKey || undefined}
          onChange={(v: string) => onUpdate({ listKey: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          placeholder={t('nodeConfig.selectListPlaceholder')}
          allowClear
          loading={loadingListKeys}
          options={listKeys.map(key => ({ value: key, label: key }))}
          showSearch
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.keyType')}</Text>
        <Select
          value={data.keyType || undefined}
          onChange={(v: string) => onUpdate({ keyType: v })}
          size="small"
          style={{ width: '100%', marginTop: 4 }}
          placeholder={t('nodeConfig.selectKeyTypePlaceholder')}
          options={Object.entries(KEY_TYPE_LABELS).map(([key, label]) => ({
            value: key,
            label,
          }))}
        />
      </div>
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>{t('nodeConfig.featureBinding')}</Text>
        <Input
          value={data.fieldName || undefined}
          onChange={(e) => onUpdate({ fieldName: e.target.value || undefined })}
          size="small"
          style={{ marginTop: 4 }}
          placeholder={t('nodeConfig.featureBindingPlaceholder')}
          allowClear
        />
        <Text type="secondary" style={{ fontSize: 11, display: 'block', marginTop: 2 }}>
          {t('nodeConfig.featureBindingHint')}
        </Text>
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ padding: '8px 10px', background: '#f6ffed', borderRadius: 4, border: '1px solid #b7eb8f' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {t('nodeConfig.whitelistMatchDesc')}
        </Text>
      </div>
    </>
  );
}

export default function NodeConfigPanel({ node, onUpdate }: NodeConfigPanelProps) {
  const { t } = useTranslation();
  const handleUpdate = useCallback(
    (updates: Partial<ConditionNodeData | ActionNodeData | EndNodeData | RuleSetNodeData | BlacklistNodeData | WhitelistNodeData>) => {
      onUpdate(node.id, updates);
    },
    [node.id, onUpdate],
  );

  const nodeType = node.data.nodeType;

  const typeLabel: Record<string, { text: string; color: string }> = {
    start: { text: t('nodeConfig.typeLabels.start'), color: 'green' },
    end: { text: t('nodeConfig.typeLabels.end'), color: 'red' },
    condition: { text: t('nodeConfig.typeLabels.condition'), color: 'blue' },
    action: { text: t('nodeConfig.typeLabels.action'), color: 'orange' },
    ruleset: { text: t('nodeConfig.typeLabels.ruleset'), color: 'purple' },
    blacklist: { text: t('nodeConfig.typeLabels.blacklist'), color: 'red' },
    whitelist: { text: t('nodeConfig.typeLabels.whitelist'), color: 'green' },
    merge: { text: t('nodeConfig.typeLabels.merge'), color: 'default' },
  };
  const info = typeLabel[nodeType] ?? { text: nodeType, color: 'default' };

  return (
    <div style={{ padding: '12px', background: '#fff', borderLeft: '1px solid #f0f0f0', height: '100%', overflow: 'auto' }}>
      <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
        <Text strong>{t('nodeConfig.nodeProperties')}</Text>
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
        <div style={{ color: '#999', fontSize: 13 }}>{t('nodeConfig.startNodeNoConfig')}</div>
      )}
      {nodeType === 'blacklist' && (
        <BlacklistConfig data={node.data as BlacklistNodeData} onUpdate={handleUpdate} />
      )}
      {nodeType === 'whitelist' && (
        <WhitelistConfig data={node.data as WhitelistNodeData} onUpdate={handleUpdate} />
      )}
      {nodeType === 'merge' && (
        <div style={{ color: '#999', fontSize: 13 }}>{t('nodeConfig.mergeNodeNoConfig')}</div>
      )}
    </div>
  );
}
