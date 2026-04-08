/**
 * 规则渲染内容对比组件
 * 将两个版本的 Groovy 脚本解析为结构化展示，并排对比差异
 */

import { useMemo } from 'react';
import { Tag, Typography, Card, Row, Col, Empty } from 'antd';
import { useTranslation } from 'react-i18next';
import { parseSingleRuleForDisplay } from '../utils/dslParser';
import type { ConditionTreeDisplayNode } from '../utils/dslParser';
import { OPERATOR_LABELS, ACTION_LABELS } from '../types/ruleConfig';

const { Text } = Typography;

interface RuleRenderedDiffProps {
  oldScript?: string;
  newScript?: string;
  oldTitle?: string;
  newTitle?: string;
}

/** 递归渲染条件树 */
function ConditionTreeDisplay({ node }: { node: ConditionTreeDisplayNode }) {
  if (node.type === 'condition') {
    return (
      <span style={{ marginRight: 8 }}>
        <Text strong>{node.fieldName}</Text>
        {' '}
        <Tag color="blue">{node.operatorLabel}</Tag>
        {' '}
        <Text code>{node.threshold}</Text>
      </span>
    );
  }
  const logicColor = node.logic === 'AND' ? 'blue' : 'orange';
  return (
    <span>
      {'('}
      {node.children?.map((child, i) => (
        <span key={i}>
          {i > 0 && <Tag color={logicColor} style={{ margin: '0 4px' }}>{node.logic}</Tag>}
          <ConditionTreeDisplay node={child} />
        </span>
      ))}
      {')'}
    </span>
  );
}

/** 动作颜色 */
const actionColor = (label: string) =>
  label === '通过' || label === 'Pass' ? 'green' : label === '拒绝' || label === 'Reject' ? 'red' : 'orange';

/** 渲染单个版本的结构化展示 */
function RenderedRuleCard({ script, title, highlight, noContentText, conditionLabel, noConditionText, whenMatchedLabel, whenNotMatchedLabel }: {
  script?: string; title: string; highlight?: 'old' | 'new';
  noContentText: string; conditionLabel: string; noConditionText: string;
  whenMatchedLabel: string; whenNotMatchedLabel: string;
}) {
  const parsed = useMemo(() => {
    if (!script) return null;
    return parseSingleRuleForDisplay(script, OPERATOR_LABELS, ACTION_LABELS);
  }, [script]);

  if (!parsed) {
    return (
      <Card size="small" title={title} style={{ height: '100%' }}
        headStyle={{ background: highlight === 'old' ? '#e6f7ff' : highlight === 'new' ? '#fff7e6' : '#fafafa', fontSize: 13 }}
      >
        <Empty description={noContentText} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    );
  }

  const hasCondition = parsed.conditionTree &&
    (parsed.conditionTree.type === 'condition'
      ? parsed.conditionTree.fieldName
      : parsed.conditionTree.children?.length);

  return (
    <Card size="small" title={title} style={{ height: '100%' }}
      headStyle={{ background: highlight === 'old' ? '#e6f7ff' : highlight === 'new' ? '#fff7e6' : '#fafafa', fontSize: 13 }}
      bodyStyle={{ padding: '8px 12px' }}
    >
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>{conditionLabel}</Text>
        {hasCondition ? (
          <div style={{ marginTop: 4, padding: '4px 8px', background: '#f6f8fa', borderRadius: 4 }}>
            <ConditionTreeDisplay node={parsed.conditionTree} />
          </div>
        ) : (
          <div style={{ marginTop: 4, color: '#999', fontSize: 12 }}>{noConditionText}</div>
        )}
      </div>

      <div style={{ marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>{whenMatchedLabel}</Text>
        <div style={{ marginTop: 4 }}>
          <Tag color={actionColor(parsed.actionLabel)}>{parsed.actionLabel}</Tag>
          {parsed.reason && <Text type="secondary" style={{ fontSize: 12 }}>— {parsed.reason}</Text>}
        </div>
      </div>

      <div>
        <Text type="secondary" style={{ fontSize: 11 }}>{whenNotMatchedLabel}</Text>
        <div style={{ marginTop: 4 }}>
          <Tag color={actionColor(parsed.defaultActionLabel)}>{parsed.defaultActionLabel}</Tag>
          {parsed.defaultReason && <Text type="secondary" style={{ fontSize: 12 }}>— {parsed.defaultReason}</Text>}
        </div>
      </div>
    </Card>
  );
}

export default function RuleRenderedDiff({ oldScript, newScript, oldTitle, newTitle }: RuleRenderedDiffProps) {
  const { t } = useTranslation();
  const resolvedOldTitle = oldTitle ?? t('grayscale.currentVersionTitle');
  const resolvedNewTitle = newTitle ?? t('grayscale.grayscaleVersionTitle');

  return (
    <Row gutter={16}>
      <Col span={12}>
        <RenderedRuleCard script={oldScript} title={resolvedOldTitle} highlight="old"
          noContentText={t('grayscale.noContentToCompare')}
          conditionLabel={t('grayscale.ruleLogicComparison')}
          noConditionText={t('rules.unconditional')}
          whenMatchedLabel={t('rules.whenMatched')}
          whenNotMatchedLabel={t('rules.whenNotMatched')}
        />
      </Col>
      <Col span={12}>
        <RenderedRuleCard script={newScript} title={resolvedNewTitle} highlight="new"
          noContentText={t('grayscale.noContentToCompare')}
          conditionLabel={t('grayscale.ruleLogicComparison')}
          noConditionText={t('rules.unconditional')}
          whenMatchedLabel={t('rules.whenMatched')}
          whenNotMatchedLabel={t('rules.whenNotMatched')}
        />
      </Col>
    </Row>
  );
}
