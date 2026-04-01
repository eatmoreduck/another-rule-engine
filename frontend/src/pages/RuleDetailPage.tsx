/**
 * RuleDetailPage - 规则详情页
 * 支持人类可读的规则逻辑展示 + 原始 Groovy 脚本查看
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card, Descriptions, Button, Space, Breadcrumb, Popconfirm, message,
  Collapse, Switch, Typography, Spin, Table, Tag,
} from 'antd';
import {
  EditOutlined, DeleteOutlined, ArrowLeftOutlined,
  CheckCircleOutlined, StopOutlined,
} from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { getRule, deleteRule, enableRule, disableRule } from '../api/rules';
import type { Rule } from '../types/rule';
import RuleStatusBadge from '../components/rules/RuleStatusBadge';
import { parseGroovyForDisplay } from '../utils/dslParser';
import { OPERATOR_LABELS, ACTION_LABELS } from '../types/ruleConfig';

const { Text } = Typography;

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

  // 在顶层解析 Groovy 脚本（必须在 early return 之前调用 hooks）
  const parsedDisplay = useMemo(() => {
    if (!rule?.groovyScript) return null;
    return parseGroovyForDisplay(rule.groovyScript, OPERATOR_LABELS, ACTION_LABELS);
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
            <RuleStatusBadge status={rule.status} />
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

        {/* 人类可读的规则逻辑展示 */}
        {parsedDisplay && (() => {
          if (parsedDisplay.conditions.length === 0) {
            return (
              <Card title="规则逻辑" size="small" style={{ marginBottom: 16 }}>
                <Space>
                  <Text type="secondary">无条件规则，默认动作：</Text>
                  <Tag color={actionTagColor(parsedDisplay.defaultActionLabel)}>
                    {parsedDisplay.defaultActionLabel}
                  </Tag>
                  <Text type="secondary">{parsedDisplay.defaultReason}</Text>
                </Space>
              </Card>
            );
          }
          return (
            <Card title="规则逻辑" size="small" style={{ marginBottom: 16 }}>
              <Table
                size="small"
                pagination={false}
                dataSource={parsedDisplay.conditions.map((c, i) => ({ ...c, key: i }))}
                columns={[
                  { title: '字段', dataIndex: 'fieldName', width: 140 },
                  { title: '运算符', dataIndex: 'operatorLabel', width: 100 },
                  { title: '阈值', dataIndex: 'threshold', width: 120 },
                  {
                    title: '命中动作',
                    dataIndex: 'actionLabel',
                    width: 100,
                    render: (text: string) => (
                      <Tag color={actionTagColor(text)}>{text}</Tag>
                    ),
                  },
                  { title: '原因', dataIndex: 'reason' },
                ]}
              />
              <div style={{ marginTop: 12, padding: '8px 12px', background: '#fafafa', borderRadius: 6 }}>
                <Space>
                  <Text type="secondary">默认动作（无匹配条件时）：</Text>
                  <Tag color={actionTagColor(parsedDisplay.defaultActionLabel)}>
                    {parsedDisplay.defaultActionLabel}
                  </Tag>
                  <Text type="secondary">{parsedDisplay.defaultReason}</Text>
                </Space>
              </div>
            </Card>
          );
        })()}

        <Collapse
          items={[
            {
              key: 'script',
              label: 'Groovy 脚本（原始代码）',
              children: (
                <pre
                  style={{
                    background: '#1e1e1e',
                    color: '#d4d4d4',
                    padding: 16,
                    borderRadius: 8,
                    fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace",
                    fontSize: 13,
                    lineHeight: 1.6,
                    overflow: 'auto',
                    whiteSpace: 'pre-wrap',
                    margin: 0,
                  }}
                >
                  {rule.groovyScript}
                </pre>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
