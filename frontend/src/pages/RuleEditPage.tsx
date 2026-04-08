/**
 * RuleEditPage - 单规则编辑页
 * 左侧：基本信息 + 条件编辑表单
 * 右侧：Groovy 脚本实时预览
 */

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  Card, Form, Input, Button, Breadcrumb, message,
  Spin, Typography, Row, Col, Tag, Modal,
} from 'antd';
import { SaveOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useBlocker } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getRule, createRule, updateRule, getRuleReferences } from '../api/rules';
import type { Rule } from '../types/rule';
import type { RuleReference } from '../types/rule';
import type { SingleRuleConfig } from '../types/ruleConfig';
import { generateGroovyFromSingleRule } from '../utils/dslGenerator';
import { parseGroovyToSingleRule } from '../utils/dslParser';
import { createDefaultSingleRule } from '../types/ruleConfig';
import ConditionForm from '../components/rules/form/ConditionForm';
import RuleTestModal from '../components/rules/RuleTestModal';
import '../styles/editor.css';

const { Title, Text } = Typography;

export default function RuleEditPage() {
  const { ruleKey } = useParams<{ ruleKey: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const isNew = !ruleKey;

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [testModalOpen, setTestModalOpen] = useState(false);
  const justSavedRef = useRef(false);
  const modalShownRef = useRef(false);
  const [existingRule, setExistingRule] = useState<Rule | null>(null);
  const [references, setReferences] = useState<RuleReference[]>([]);

  // 单规则配置
  const [ruleConfig, setRuleConfig] = useState<SingleRuleConfig>(createDefaultSingleRule());

  // 加载已有规则（含引用提示）
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
          if (rule.groovyScript) {
            const parsed = parseGroovyToSingleRule(rule.groovyScript);
            setRuleConfig(parsed);
          }
          return getRuleReferences(ruleKey);
        })
        .then((refs) => {
          if (refs && refs.length > 0) {
            setReferences(refs);
            if (!modalShownRef.current) {
              modalShownRef.current = true;
              Modal.warning({
                title: t('rules.referenceTitle'),
                width: 520,
                content: (
                  <div>
                    <p style={{ marginBottom: 12 }}>{t('rules.referenceContent')}</p>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                      {refs.map((r: RuleReference, idx: number) => {
                        const typeLabel = r.type === 'decision_flow' ? t('rules.referenceDecisionFlow') : t('rules.referenceRuleSet');
                        const color = r.type === 'decision_flow' ? 'blue' : 'purple';
                        return (
                          <Tag key={idx} color={color} style={{ margin: 0 }}>
                            {typeLabel}：{r.name}（{r.key}）
                          </Tag>
                        );
                      })}
                    </div>
                  </div>
                ),
                okText: t('rules.referenceOk'),
              });
            }
          }
        })
        .catch(() => message.error(t('rules.loadFailed')))
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
      const isRuleEditSwitch = nextLocation.pathname.startsWith('/rules/')
        && (nextLocation.pathname.endsWith('/edit')
          || nextLocation.pathname.endsWith('/flow')
          || nextLocation.pathname === '/rules/new'
          || nextLocation.pathname === '/rules/new/flow');
      if (isRuleEditSwitch) return false;
      return !window.confirm(t('common.confirmLeave'));
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
        message.success(t('rules.createSuccess'));
        justSavedRef.current = true;
        navigate(`/rules/${created.ruleKey}`);
      } else if (ruleKey) {
        await updateRule(ruleKey, {
          ruleName: values.ruleName,
          ruleDescription: values.ruleDescription,
          groovyScript: generatedScript,
        });
        message.success(t('rules.saveSuccess'));
        justSavedRef.current = true;
        navigate(`/rules/${ruleKey}`);
      }
    } catch (err) {
      if (err instanceof Error) {
        message.error(`${t('rules.saveFailed')}: ${err.message}`);
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
          { title: <a onClick={() => navigate('/rules')}>{t('rules.pageTitle')}</a> },
          ...(isNew
            ? [{ title: t('rules.createRule') }]
            : [
                { title: <a onClick={() => navigate(`/rules/${ruleKey}`)}>{existingRule?.ruleName ?? ruleKey}</a> },
                { title: t('common.edit') },
              ]),
        ]}
      />

      {/* 页面标题 + 保存按钮 */}
      <div className="page-header" style={{ marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          {isNew ? t('rules.createRuleForm') : `${t('rules.editRule')} - ${existingRule?.ruleName ?? ruleKey}`}
        </Title>
        <div className="page-header-actions">
          <Button
            icon={<ThunderboltOutlined />}
            onClick={() => setTestModalOpen(true)}
            disabled={isNew}
          >
            {t('common.test')}
          </Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={handleSave}
          >
            {t('common.save')}
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
                label={t('rules.ruleKeyLabel')}
                rules={[{ required: true, message: t('rules.ruleKeyRequired') }]}
              >
                <Input placeholder={t('rules.ruleKeyPlaceholder')} disabled={!isNew} />
              </Form.Item>
              <Form.Item
                name="ruleName"
                label={t('rules.ruleName')}
                rules={[{ required: true, message: t('rules.ruleNameRequired') }]}
              >
                <Input placeholder={t('rules.ruleNamePlaceholder')} />
              </Form.Item>
              <Form.Item name="ruleDescription" label={t('rules.ruleDescription')}>
                <Input.TextArea rows={2} placeholder={t('rules.ruleDescPlaceholder')} />
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
            title={<Text strong>{t('rules.groovyScriptPreview')}</Text>}
            size="small"
            style={{ position: 'sticky', top: 16 }}
          >
            <pre className="script-preview-code" style={{ margin: 0 }}>
              {generatedScript}
            </pre>
          </Card>
        </Col>
      </Row>
      {!isNew && ruleKey && existingRule && (
        <RuleTestModal
          open={testModalOpen}
          onClose={() => setTestModalOpen(false)}
          ruleKey={ruleKey}
          groovyScript={generatedScript}
        />
      )}
    </div>
  );
}
