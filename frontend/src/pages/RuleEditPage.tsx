/**
 * RuleEditPage - 单规则编辑页
 * 左侧：基本信息 + 条件编辑表单
 * 右侧：Groovy 脚本实时预览
 */

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  Card, Form, Input, Button, Breadcrumb, message,
  Spin, Typography, Row, Col, Modal,
} from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
import { getRule, createRule, updateRule, getRuleReferences } from '../api/rules';
import type { Rule } from '../types/rule';
import type { SingleRuleConfig } from '../types/ruleConfig';
import { generateGroovyFromSingleRule } from '../utils/dslGenerator';
import { parseGroovyToSingleRule } from '../utils/dslParser';
import { createDefaultSingleRule } from '../types/ruleConfig';
import ConditionForm from '../components/rules/form/ConditionForm';
import '../styles/editor.css';

const { Title, Text } = Typography;

export default function RuleEditPage() {
  const { ruleKey } = useParams<{ ruleKey: string }>();
  const navigate = useNavigate();
  const isNew = !ruleKey;

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const justSavedRef = useRef(false);
  const [existingRule, setExistingRule] = useState<Rule | null>(null);

  // 单规则配置
  const [ruleConfig, setRuleConfig] = useState<SingleRuleConfig>(createDefaultSingleRule());

  // 加载已有规则（含引用提示）
  useEffect(() => {
    if (!isNew && ruleKey) {
      setLoading(true);
      // 先加载规则数据，确保页面能尽快渲染
      getRule(ruleKey)
        .then((rule) => {
          setExistingRule(rule);
          form.setFieldsValue({
            ruleKey: rule.ruleKey,
            ruleName: rule.ruleName,
            ruleDescription: rule.ruleDescription ?? '',
          });
          // 解析已有 groovyScript 回填条件表单
          if (rule.groovyScript) {
            const parsed = parseGroovyToSingleRule(rule.groovyScript);
            setRuleConfig(parsed);
          }
          // 数据加载完成后，异步检查引用（不阻塞页面渲染）
          return getRuleReferences(ruleKey);
        })
        .then((refs) => {
          if (refs && refs.length > 0) {
            const typeLabel = (t: string) => t === 'decision_flow' ? '决策流' : '规则集';
            const refList = refs.map((r) => `• ${typeLabel(r.type)}：${r.name}（${r.key}）`).join('\n');
            Modal.info({
              title: '引用提示',
              content: (
                <div>
                  <p>此规则被以下资源引用，修改可能影响它们的执行结果：</p>
                  <pre style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{refList}</pre>
                </div>
              ),
              okText: '知道了',
            });
          }
        })
        .catch(() => message.error('加载规则失败'))
        .finally(() => setLoading(false));
    }
  }, [isNew, ruleKey, form]);

  // 根据当前配置生成 Groovy 脚本
  const generatedScript = useMemo(() => {
    return generateGroovyFromSingleRule(ruleConfig);
  }, [ruleConfig]);

  // 离开页面确认
  useBlocker(
    ({ currentLocation, nextLocation }) => {
      if (justSavedRef.current) return false;
      if (!dirty) return false;
      if (currentLocation.pathname === nextLocation.pathname) return false;
      // 模式切换不拦截
      const isRuleEditSwitch = nextLocation.pathname.startsWith('/rules/')
        && (nextLocation.pathname.endsWith('/edit')
          || nextLocation.pathname.endsWith('/flow')
          || nextLocation.pathname === '/rules/new'
          || nextLocation.pathname === '/rules/new/flow');
      if (isRuleEditSwitch) return false;
      return !window.confirm('有未保存的修改，确认离开？');
    },
  );

  // ConditionForm 变更回调
  const handleConditionChange = useCallback((newConfig: SingleRuleConfig, _script: string) => {
    setRuleConfig(newConfig);
    setDirty(true);
  }, []);

  const handleSave = useCallback(async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);

      if (isNew) {
        const created = await createRule({
          ruleKey: values.ruleKey,
          ruleName: values.ruleName,
          ruleDescription: values.ruleDescription,
          groovyScript: generatedScript,
        });
        message.success('规则创建成功');
        justSavedRef.current = true;
        navigate(`/rules/${created.ruleKey}`);
      } else if (ruleKey) {
        await updateRule(ruleKey, {
          ruleName: values.ruleName,
          ruleDescription: values.ruleDescription,
          groovyScript: generatedScript,
        });
        message.success('规则保存成功');
        justSavedRef.current = true;
        navigate(`/rules/${ruleKey}`);
      }
    } catch (err) {
      if (err instanceof Error) {
        message.error(`保存失败: ${err.message}`);
      }
    } finally {
      setSaving(false);
    }
  }, [isNew, ruleKey, form, generatedScript, navigate]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/rules')}>规则配置</a> },
          ...(isNew
            ? [{ title: '新建规则' }]
            : [
                { title: <a onClick={() => navigate(`/rules/${ruleKey}`)}>{existingRule?.ruleName ?? ruleKey}</a> },
                { title: '编辑' },
              ]),
        ]}
      />

      {/* 页面标题 + 保存按钮 */}
      <div className="page-header" style={{ marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          {isNew ? '新建规则（表单模式）' : `编辑规则 - ${existingRule?.ruleName ?? ruleKey}`}
        </Title>
        <div className="page-header-actions">
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={handleSave}
          >
            保存
          </Button>
        </div>
      </div>

      {/* 左右布局 */}
      <Row gutter={16}>
        {/* 左侧：基本信息 + 条件编辑 */}
        <Col xs={24} lg={14}>
          <Card>
            <Form form={form} layout="vertical">
              <Form.Item
                name="ruleKey"
                label="规则标识"
                rules={[{ required: true, message: '请输入规则标识' }]}
              >
                <Input placeholder="例如: black-list-check" disabled={!isNew} />
              </Form.Item>
              <Form.Item
                name="ruleName"
                label="规则名称"
                rules={[{ required: true, message: '请输入规则名称' }]}
              >
                <Input placeholder="例如: 黑名单检查规则" />
              </Form.Item>
              <Form.Item name="ruleDescription" label="规则描述">
                <Input.TextArea rows={2} placeholder="可选，描述规则的业务用途" />
              </Form.Item>
            </Form>

            <ConditionForm
              config={ruleConfig}
              onChange={handleConditionChange}
            />
          </Card>
        </Col>

        {/* 右侧：Groovy 脚本实时预览 */}
        <Col xs={24} lg={10}>
          <Card
            title={<Text strong>Groovy 脚本预览</Text>}
            size="small"
            style={{ position: 'sticky', top: 16 }}
          >
            <pre className="script-preview-code" style={{ margin: 0 }}>
              {generatedScript}
            </pre>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
