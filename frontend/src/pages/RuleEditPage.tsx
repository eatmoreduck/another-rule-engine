/**
 * RuleEditPage - 表单模式规则编辑页
 * Plan 03-02/03-04: 含 ModeSwitch，脚本预览，保存对接后端
 * 重构: 使用 ConditionForm 组件管理条件规则
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card, Form, Input, Button, Breadcrumb, message,
  Spin, Typography, Collapse,
} from 'antd';
import { SaveOutlined, EyeOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
import { getRule, createRule, updateRule } from '../api/rules';
import type { Rule } from '../types/rule';
import type { FormRuleConfig, Action, ConditionActionRule } from '../types/ruleConfig';
import { generateGroovyFromForm } from '../utils/dslGenerator';
import ModeSwitch from '../components/rules/ModeSwitch';
import ScriptPreview from '../components/rules/ScriptPreview';
import ConditionForm from '../components/rules/form/ConditionForm';
import '../styles/editor.css';

const { Title } = Typography;

export default function RuleEditPage() {
  const { ruleKey } = useParams<{ ruleKey: string }>();
  const navigate = useNavigate();
  const isNew = !ruleKey;

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [existingRule, setExistingRule] = useState<Rule | null>(null);

  // 条件规则列表
  const [conditions, setConditions] = useState<ConditionActionRule[]>([]);
  const [defaultAction, setDefaultAction] = useState<Action>('PASS');
  const [defaultReason, setDefaultReason] = useState('默认通过');

  // 加载已有规则
  useEffect(() => {
    if (!isNew && ruleKey) {
      setLoading(true);
      getRule(ruleKey)
        .then((rule) => {
          setExistingRule(rule);
          form.setFieldsValue({
            ruleKey: rule.ruleKey,
            ruleName: rule.ruleName,
            ruleDescription: rule.ruleDescription ?? '',
          });
        })
        .catch(() => message.error('加载规则失败'))
        .finally(() => setLoading(false));
    }
  }, [isNew, ruleKey, form]);

  // 根据当前配置生成 Groovy 脚本
  const generatedScript = useMemo(() => {
    const config: FormRuleConfig = {
      defaultAction,
      defaultReason,
      rules: conditions,
    };
    return generateGroovyFromForm(config);
  }, [conditions, defaultAction, defaultReason]);

  // 离开页面确认
  useBlocker(
    ({ currentLocation, nextLocation }) => {
      if (dirty && currentLocation.pathname !== nextLocation.pathname) {
        return !window.confirm('有未保存的修改，确认离开？');
      }
      return false;
    },
  );

  // ConditionForm 变更回调
  const handleConditionChange = useCallback((_config: FormRuleConfig, _script: string) => {
    // 从 config 中同步状态到本页（保持 conditions/defaultAction/defaultReason 同步）
    setConditions(_config.rules);
    setDefaultAction(_config.defaultAction);
    setDefaultReason(_config.defaultReason);
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
        navigate(`/rules/${created.ruleKey}`);
      } else if (ruleKey) {
        await updateRule(ruleKey, {
          ruleName: values.ruleName,
          ruleDescription: values.ruleDescription,
          groovyScript: generatedScript,
        });
        message.success('规则保存成功');
        navigate(`/rules/${ruleKey}`);
      }
      setDirty(false);
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
          { title: <a onClick={() => navigate('/rules')}>规则管理</a> },
          ...(isNew
            ? [{ title: '新建规则' }]
            : [
                { title: <a onClick={() => navigate(`/rules/${ruleKey}`)}>{existingRule?.ruleName ?? ruleKey}</a> },
                { title: '编辑' },
              ]),
        ]}
      />

      <Card>
        <div className="page-header">
          <Title level={4} style={{ margin: 0 }}>
            {isNew ? '新建规则（表单模式）' : `编辑规则 - ${existingRule?.ruleName ?? ruleKey}`}
          </Title>
          <div className="page-header-actions">
            <ModeSwitch currentMode="form" ruleKey={ruleKey} />
            <Button
              icon={<EyeOutlined />}
              onClick={() => setPreviewOpen(true)}
            >
              预览脚本
            </Button>
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

        <div className="form-editor-container">
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

          {/* 使用 ConditionForm 组件 */}
          <ConditionForm
            conditions={conditions}
            defaultAction={defaultAction}
            defaultReason={defaultReason}
            onChange={handleConditionChange}
          />
        </div>

        {/* 脚本预览折叠面板 */}
        <Collapse
          style={{ marginTop: 16 }}
          items={[
            {
              key: 'preview',
              label: '生成的 Groovy 脚本',
              children: (
                <pre className="script-preview-code">{generatedScript}</pre>
              ),
            },
          ]}
        />
      </Card>

      <ScriptPreview
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        script={generatedScript}
      />
    </div>
  );
}
