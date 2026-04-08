/**
 * ConditionNodeEditor - 单个条件行编辑器
 * 编辑一个原子条件：字段名、运算符、阈值
 */

import { useCallback } from 'react';
import { Input, Select, Button } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ConditionNode, Operator } from '../../../types/ruleConfig';
import { OPERATOR_LABELS } from '../../../types/ruleConfig';

const OPERATOR_OPTIONS = Object.entries(OPERATOR_LABELS).map(([value, label]) => ({
  value,
  label,
}));

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

  const handleFieldChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onChange({ ...node, fieldName: e.target.value });
    },
    [node, onChange],
  );

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

  return (
    <div className="condition-node-row">
      <Input
        placeholder={t('ruleConfig.field')}
        value={node.fieldName}
        onChange={handleFieldChange}
        style={{ width: 140 }}
      />
      <Select
        value={node.operator}
        onChange={handleOperatorChange}
        options={OPERATOR_OPTIONS}
        style={{ width: 120 }}
      />
      <Input
        placeholder={t('ruleConfig.threshold')}
        value={String(node.threshold)}
        onChange={handleThresholdChange}
        style={{ width: 120 }}
      />
      <Button
        type="text"
        danger
        icon={<DeleteOutlined />}
        onClick={onRemove}
      />
    </div>
  );
}
