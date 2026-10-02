/**
 * ConditionNodeEditor - 单个条件行编辑器
 * 编辑一个原子条件：字段名、运算符、阈值
 */

import { useCallback, useMemo, useState } from 'react';
import { Input, Select, Button, Tag, Tooltip, Typography } from 'antd';
import { DeleteOutlined, InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ConditionNode, Operator } from '../../../types/ruleConfig';
import { OPERATOR_LABELS } from '../../../types/ruleConfig';
import FeatureFieldInput from '../../feature/FeatureFieldInput';
import type { FeatureResolvedInfo } from '../../../types/featureCatalog';

const OPERATOR_OPTIONS = Object.entries(OPERATOR_LABELS).map(([value, label]) => ({
  value,
  label,
}));
const { Text } = Typography;

function isNumericType(dataType: string): boolean {
  return ['NUMBER', 'INTEGER', 'LONG', 'DOUBLE', 'DECIMAL'].includes(dataType);
}

function isCollectionType(dataType: string): boolean {
  return ['STRING', 'TEXT', 'LIST', 'ARRAY'].includes(dataType);
}

function inferThresholdPlaceholder(dataType: string | undefined, t: (key: string) => string): string {
  if (!dataType) return t('ruleConfig.threshold');
  if (isNumericType(dataType)) return t('featureCatalog.thresholdPlaceholderNumber');
  if (dataType === 'BOOLEAN') return t('featureCatalog.thresholdPlaceholderBoolean');
  return t('featureCatalog.thresholdPlaceholderText');
}

export interface ConditionNodeEditorProps {
  node: ConditionNode;
  onChange: (node: ConditionNode) => void;
  onRemove: () => void;
}

export default function ConditionNodeEditor({
  node,
  onChange,
  onRemove,
}: ConditionNodeEditorProps) {
  const { t } = useTranslation();
  const [resolvedFeature, setResolvedFeature] = useState<FeatureResolvedInfo | null>(null);

  const handleOperatorChange = useCallback(
    (value: Operator) => {
      onChange({ ...node, operator: value });
    },
    [node, onChange],
  );

  const handleThresholdChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onChange({ ...node, threshold: e.target.value });
    },
    [node, onChange],
  );

  const operatorWarning = useMemo(() => {
    if (!resolvedFeature?.feature?.dataType) return null;
    const dataType = resolvedFeature.feature.dataType;
    if (['GT', 'GE', 'LT', 'LE'].includes(node.operator) && !isNumericType(dataType)) {
      return t('featureCatalog.operatorTypeWarning', { operator: node.operator, dataType });
    }
    if (['CONTAINS', 'NOT_CONTAINS', 'IN', 'NOT_IN'].includes(node.operator) && !isCollectionType(dataType)) {
      return t('featureCatalog.operatorTypeWarning', { operator: node.operator, dataType });
    }
    if (isNumericType(dataType) && typeof node.threshold === 'string' && node.threshold.trim() !== '' && Number.isNaN(Number(node.threshold))) {
      return t('featureCatalog.thresholdTypeWarning');
    }
    return null;
  }, [node.operator, node.threshold, resolvedFeature, t]);

  const thresholdPlaceholder = inferThresholdPlaceholder(resolvedFeature?.feature.dataType, t);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div className="condition-node-row">
        <FeatureFieldInput
          placeholder={t('ruleConfig.fieldName')}
          value={node.fieldName}
          onChange={(value) => onChange({ ...node, fieldName: value })}
          onFeatureResolved={setResolvedFeature}
          style={{ width: 220 }}
          size="small"
        />
        <Select
          value={node.operator}
          onChange={handleOperatorChange}
          options={OPERATOR_OPTIONS}
          style={{ width: 120 }}
          size="small"
        />
        <Input
          placeholder={thresholdPlaceholder}
          value={String(node.threshold)}
          onChange={handleThresholdChange}
          style={{ width: 160 }}
          size="small"
        />
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={onRemove}
        />
      </div>

      {resolvedFeature?.feature && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginLeft: 2 }}>
          <Tag color="blue" style={{ margin: 0 }}>{resolvedFeature.feature.dataType}</Tag>
          <Tag style={{ margin: 0 }}>{resolvedFeature.feature.sourceType}</Tag>
          {resolvedFeature.matchedByAlias && (
            <Text type="warning" style={{ fontSize: 12 }}>
              {t('featureCatalog.aliasMappedHint', {
                alias: resolvedFeature.matchedAlias ?? node.fieldName,
                code: resolvedFeature.feature.code,
              })}
            </Text>
          )}
          {resolvedFeature.feature.exampleValue && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {t('featureCatalog.exampleShortLabel')}: {resolvedFeature.feature.exampleValue}
            </Text>
          )}
          {resolvedFeature.feature.description && (
            <Tooltip title={resolvedFeature.feature.description}>
              <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
            </Tooltip>
          )}
        </div>
      )}

      {!!node.fieldName.trim() && !resolvedFeature?.feature && (
        <Text type="warning" style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}>
          <WarningOutlined />
          {t('featureCatalog.notFoundHint')}
        </Text>
      )}

      {operatorWarning && (
        <Text type="warning" style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}>
          <WarningOutlined />
          {operatorWarning}
        </Text>
      )}
    </div>
  );
}
