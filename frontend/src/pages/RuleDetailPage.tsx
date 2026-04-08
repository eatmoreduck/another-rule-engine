/**
 * RuleDetailPage - 规则详情页
 * 单规则模型：条件树 + 匹配动作 + 不匹配默认动作
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card, Descriptions, Button, Space, Breadcrumb, Popconfirm, message,
  Switch, Typography, Spin, Tag, Alert, Row, Col,
} from 'antd';
import {
  EditOutlined, DeleteOutlined, ArrowLeftOutlined,
  CheckCircleOutlined, StopOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getRule, deleteRule, enableRule, disableRule } from '../api/rules';
import type { Rule } from '../types/rule';
import { parseSingleRuleForDisplay } from '../utils/dslParser';
import type { ConditionTreeDisplayNode } from '../utils/dslParser';
import { OPERATOR_LABELS, ACTION_LABELS } from '../types/ruleConfig';
import RuleTestModal from '../components/rules/RuleTestModal';
import Access from '../components/AccessControl';

/** 判断展示节点是否为空条件 */
function isEmptyDisplayNode(node: ConditionTreeDisplayNode): boolean {
  if (node.type === 'condition') {
    return !node.fieldName || node.fieldName.trim() === '';
  }
  if (node.type === 'group') {
    return !node.children || node.children.length === 0 || node.children.every(isEmptyDisplayNode);
  }
  return false;
}

const { Text } = Typography;

/** 递归渲染条件树展示节点 */
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

  // group
  const logicColor = node.logic === 'AND' ? 'blue' : 'orange';
  return (
    <span>
      {'('}
      {node.children?.map((child, i) => (
        <span key={i}>
          {i > 0 && (
            <Tag color={logicColor} style={{ margin: '0 4px' }}>
              {node.logic}
            </Tag>
          )}
          <ConditionTreeDisplay node={child} />
        </span>
      ))}
      {')'}
    </span>
  );
}

export default function RuleDetailPage() {
  const { ruleKey } = useParams<{ ruleKey: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [rule, setRule] = useState<Rule | null>(null);
  const [loading, setLoading] = useState(true);
  const [testModalOpen, setTestModalOpen] = useState(false);

  const loadRule = useCallback(async () => {
    if (!ruleKey) return;
    setLoading(true);
    try {
      const data = await getRule(ruleKey);
      setRule(data);
    } catch {
      message.error(t('rules.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [ruleKey]);

  useEffect(() => {
    loadRule();
  }, [loadRule]);

  const handleToggleEnabled = useCallback(async () => {
    if (!rule) return;
    try {
      const updated = rule.enabled
        ? await disableRule(rule.ruleKey)
        : await enableRule(rule.ruleKey);
      setRule(updated);
      message.success(rule.enabled ? t('common.disabled') : t('common.enabled'));
    } catch {
      message.error(t('common.error'));
    }
  }, [rule]);

  const handleDelete = useCallback(async () => {
    if (!rule) return;
    try {
      await deleteRule(rule.ruleKey);
      message.success(t('rules.deleteSuccess'));
      navigate('/rules');
    } catch {
      message.error(t('rules.deleteFailed'));
    }
  }, [rule, navigate]);

  // 单规则解析展示
  const parsedDisplay = useMemo(() => {
    if (!rule?.groovyScript) return null;
    return parseSingleRuleForDisplay(rule.groovyScript, OPERATOR_LABELS, ACTION_LABELS);
  }, [rule?.groovyScript]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!rule) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Text type="secondary">{t('rules.notExist')}</Text>
        <br />
        <Button style={{ marginTop: 16 }} onClick={() => navigate('/rules')}>
          {t('common.returnList')}
        </Button>
      </div>
    );
  }

  const actionTagColor = (label: string) =>
    label === ACTION_LABELS.PASS ? 'green' : label === ACTION_LABELS.REJECT ? 'red' : 'orange';

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/rules')}>{t('rules.pageTitle')}</a> },
          { title: rule.ruleName },
        ]}
      />

      <Card>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}>
          <Space>
            <Button
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/rules')}
            >
              {t('common.back')}
            </Button>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {rule.ruleName}
            </Typography.Title>
          </Space>
          <Space>
            <Access permission="api:rules:validate">
              <Button
                icon={<ThunderboltOutlined />}
                onClick={() => setTestModalOpen(true)}
              >
                {t('common.test')}
              </Button>
            </Access>
            <Access permission="api:rules:update">
              <Button
                icon={<EditOutlined />}
                onClick={() => navigate(`/rules/${rule.ruleKey}/edit`)}
              >
                {t('common.edit')}
              </Button>
            </Access>
            <Access permission="api:rules:delete">
              <Popconfirm
                title={t('rules.confirmDeleteRule')}
                onConfirm={handleDelete}
                okText={t('common.delete')}
                cancelText={t('common.cancel')}
              >
                <Button danger icon={<DeleteOutlined />}>
                  {t('common.delete')}
                </Button>
              </Popconfirm>
            </Access>
          </Space>
        </div>

        <Descriptions bordered column={2} style={{ marginBottom: 24 }}>
          <Descriptions.Item label={t('rules.ruleKeyLabel')}>{rule.ruleKey}</Descriptions.Item>
          <Descriptions.Item label={t('common.status')}>
            {rule.enabled ? (
              <Tag color="green">{t('common.enabled')}</Tag>
            ) : (
              <Tag color="default">{t('common.disabled')}</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label={t('common.version')}>{rule.version}</Descriptions.Item>
          <Descriptions.Item label={t('rules.enabledStatus')}>
            <Access permission="api:rules:update" fallback={
              <Tag color={rule.enabled ? 'green' : 'default'}>{rule.enabled ? t('common.enabled') : t('common.disabled')}</Tag>
            }>
              <Switch
                checked={rule.enabled}
                onChange={handleToggleEnabled}
                checkedChildren={<CheckCircleOutlined />}
                unCheckedChildren={<StopOutlined />}
              />
            </Access>
          </Descriptions.Item>
          <Descriptions.Item label={t('common.createdBy')}>{rule.createdBy}</Descriptions.Item>
          <Descriptions.Item label={t('common.createdAt')}>
            {rule.createdAt ? new Date(rule.createdAt).toLocaleString('zh-CN') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('common.updatedBy')}>{rule.updatedBy ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('common.updatedAt')}>
            {rule.updatedAt ? new Date(rule.updatedAt).toLocaleString('zh-CN') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('rules.ruleDescription')} span={2}>
            {rule.ruleDescription ?? '-'}
          </Descriptions.Item>
        </Descriptions>

        {/* 左右布局：规则逻辑 + Groovy 脚本 */}
        <Row gutter={16}>
          {/* 左侧：规则逻辑 */}
          <Col xs={24} lg={14}>
            {parsedDisplay && (
              <Card title={t('rules.ruleLogic')} size="small">
                {isEmptyDisplayNode(parsedDisplay.conditionTree) ? (
                  <>
                    <Alert
                      type="info"
                      showIcon
                      message={t('rules.unconditional')}
                      style={{ marginBottom: 12 }}
                    />
                    <div style={{
                      padding: '8px 12px',
                      background: '#fafafa',
                      borderRadius: 6,
                    }}>
                      <Text type="secondary">{t('rules.executeAction')}</Text>
                      <Tag
                        color={actionTagColor(parsedDisplay.defaultActionLabel)}
                        style={{ marginLeft: 8 }}
                      >
                        {parsedDisplay.defaultActionLabel}
                      </Tag>
                      <Text type="secondary"> {parsedDisplay.defaultReason}</Text>
                    </div>
                  </>
                ) : (
                  <>
                    {/* 条件树 */}
                    <div style={{ marginBottom: 12 }}>
                      <Text type="secondary" style={{ marginRight: 8 }}>{t('rules.conditionLabel')}</Text>
                      <ConditionTreeDisplay node={parsedDisplay.conditionTree} />
                    </div>

                    {/* 匹配动作 */}
                    <div style={{
                      padding: '8px 12px',
                      background: '#f6ffed',
                      borderRadius: 6,
                      border: '1px solid #b7eb8f',
                      marginBottom: 8,
                    }}>
                      <Text type="secondary">{t('rules.whenMatched')}</Text>
                      <Tag
                        color={actionTagColor(parsedDisplay.actionLabel)}
                        style={{ marginLeft: 8 }}
                      >
                        {parsedDisplay.actionLabel}
                      </Tag>
                      {parsedDisplay.reason && (
                        <Text type="secondary"> — {parsedDisplay.reason}</Text>
                      )}
                    </div>

                    {/* 不匹配默认动作 */}
                    <div style={{
                      padding: '8px 12px',
                      background: '#fafafa',
                      borderRadius: 6,
                    }}>
                      <Text type="secondary">{t('rules.whenNotMatched')}</Text>
                      <Tag
                        color={actionTagColor(parsedDisplay.defaultActionLabel)}
                        style={{ marginLeft: 8 }}
                      >
                        {parsedDisplay.defaultActionLabel}
                      </Tag>
                      <Text type="secondary"> {parsedDisplay.defaultReason}</Text>
                    </div>
                  </>
                )}
              </Card>
            )}
          </Col>

          {/* 右侧：Groovy 脚本 */}
          <Col xs={24} lg={10}>
            <Card
              title={<Text strong>{t('rules.groovyScript')}</Text>}
              size="small"
              style={{ position: 'sticky', top: 16 }}
            >
              <pre className="script-preview-code" style={{ margin: 0 }}>
                {rule.groovyScript}
              </pre>
            </Card>
          </Col>
        </Row>
      </Card>
      <RuleTestModal
        open={testModalOpen}
        onClose={() => setTestModalOpen(false)}
        ruleKey={rule.ruleKey}
        groovyScript={rule.groovyScript}
      />
    </div>
  );
}
