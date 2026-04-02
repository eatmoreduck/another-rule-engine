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
  CheckCircleOutlined, StopOutlined,
} from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { getRule, deleteRule, enableRule, disableRule } from '../api/rules';
import type { Rule } from '../types/rule';
import { parseSingleRuleForDisplay } from '../utils/dslParser';
import type { ConditionTreeDisplayNode } from '../utils/dslParser';
import { OPERATOR_LABELS, ACTION_LABELS } from '../types/ruleConfig';

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
  const [rule, setRule] = useState<Rule | null>(null);
  const [loading, setLoading] = useState(true);

  const loadRule = useCallback(async () => {
    if (!ruleKey) return;
    setLoading(true);
    try {
      const data = await getRule(ruleKey);
      setRule(data);
    } catch {
      message.error('加载规则详情失败');
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
      message.success(rule.enabled ? '已禁用' : '已启用');
    } catch {
      message.error('操作失败');
    }
  }, [rule]);

  const handleDelete = useCallback(async () => {
    if (!rule) return;
    try {
      await deleteRule(rule.ruleKey);
      message.success('删除成功');
      navigate('/rules');
    } catch {
      message.error('删除失败');
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
        <Text type="secondary">规则不存在或加载失败</Text>
        <br />
        <Button style={{ marginTop: 16 }} onClick={() => navigate('/rules')}>
          返回列表
        </Button>
      </div>
    );
  }

  const actionTagColor = (label: string) =>
    label === '通过' ? 'green' : label === '拒绝' ? 'red' : 'orange';

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/rules')}>规则配置</a> },
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
              返回
            </Button>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {rule.ruleName}
            </Typography.Title>
          </Space>
          <Space>
            <Button
              icon={<EditOutlined />}
              onClick={() => navigate(`/rules/${rule.ruleKey}/edit`)}
            >
              编辑
            </Button>
            <Popconfirm
              title="确认删除此规则？删除后不可恢复。"
              onConfirm={handleDelete}
              okText="确认删除"
              cancelText="取消"
            >
              <Button danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        </div>

        <Descriptions bordered column={2} style={{ marginBottom: 24 }}>
          <Descriptions.Item label="规则标识">{rule.ruleKey}</Descriptions.Item>
          <Descriptions.Item label="状态">
            {rule.enabled ? (
              <Tag color="green">已启用</Tag>
            ) : (
              <Tag color="default">已禁用</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="版本">{rule.version}</Descriptions.Item>
          <Descriptions.Item label="启用状态">
            <Switch
              checked={rule.enabled}
              onChange={handleToggleEnabled}
              checkedChildren={<CheckCircleOutlined />}
              unCheckedChildren={<StopOutlined />}
            />
          </Descriptions.Item>
          <Descriptions.Item label="创建人">{rule.createdBy}</Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {rule.createdAt ? new Date(rule.createdAt).toLocaleString('zh-CN') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新人">{rule.updatedBy ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {rule.updatedAt ? new Date(rule.updatedAt).toLocaleString('zh-CN') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="规则描述" span={2}>
            {rule.ruleDescription ?? '-'}
          </Descriptions.Item>
        </Descriptions>

        {/* 左右布局：规则逻辑 + Groovy 脚本 */}
        <Row gutter={16}>
          {/* 左侧：规则逻辑 */}
          <Col xs={24} lg={14}>
            {parsedDisplay && (
              <Card title="规则逻辑" size="small">
                {isEmptyDisplayNode(parsedDisplay.conditionTree) ? (
                  <>
                    <Alert
                      type="info"
                      showIcon
                      message="无条件限制，所有请求执行以下动作"
                      style={{ marginBottom: 12 }}
                    />
                    <div style={{
                      padding: '8px 12px',
                      background: '#fafafa',
                      borderRadius: 6,
                    }}>
                      <Text type="secondary">执行动作：</Text>
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
                      <Text type="secondary" style={{ marginRight: 8 }}>条件：</Text>
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
                      <Text type="secondary">满足条件时：</Text>
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
                      <Text type="secondary">不满足条件时（默认）：</Text>
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
              title={<Text strong>Groovy 脚本</Text>}
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
    </div>
  );
}
