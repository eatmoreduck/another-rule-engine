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
import { validateFeatureDefinitions } from '../api/featureCatalog';
import type { Rule } from '../types/rule';
import type { RuleReference } from '../types/rule';
import type { SingleRuleConfig } from '../types/ruleConfig';
import type { FeatureValidationResponse } from '../types/featureCatalog';
import { generateGroovyFromSingleRule } from '../utils/dslGenerator';
import { parseGroovyToSingleRule } from '../utils/dslParser';
import { createDefaultSingleRule } from '../types/ruleConfig';
import ConditionForm from '../components/rules/form/ConditionForm';
import RuleTestModal from '../components/rules/RuleTestModal';
import '../styles/editor.css';

const { Title, Text } = Typography;

function collectFeatureValidationItems(node: SingleRuleConfig['condition']): Array<{ fieldName: string; operator?: string; threshold?: unknown }> {
  if (node.type === 'condition') {
    if (!node.fieldName.trim()) {
      return [];
    }
    return [{
      fieldName: node.fieldName.trim(),
      operator: node.operator,
      threshold: node.threshold,
    }];
  }
  return node.children.flatMap((child) => collectFeatureValidationItems(child));
}

function confirmFeatureWarnings(
  validation: FeatureValidationResponse,
  title: string,
  unknownFieldsLabel: string,
  warningsLabel: string,
  aliasMappingsLabel: string,
  itemWarningsLabel: string,
  okText: string,
  cancelText: string,
): Promise<boolean> {
  const aliasMappings = validation.items.filter((item) => item.matchedByAlias && item.canonicalCode);
  const itemWarnings = validation.items.filter((item) => item.warnings.length > 0);

  return new Promise((resolve) => {
    Modal.confirm({
      title,
      width: 560,
      content: (
        <div>
          {validation.unknownFields.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{unknownFieldsLabel}</div>
              <div>{validation.unknownFields.join(', ')}</div>
            </div>
          )}
          {validation.warnings.length > 0 && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{warningsLabel}</div>
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {validation.warnings.slice(0, 6).map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}
          {aliasMappings.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{aliasMappingsLabel}</div>
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {aliasMappings.slice(0, 6).map((item) => (
                  <li key={`${item.fieldName}-${item.canonicalCode}`}>
                    {item.fieldName} → {item.canonicalCode}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {itemWarnings.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{itemWarningsLabel}</div>
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {itemWarnings.slice(0, 6).map((item) => (
                  <li key={`warn-${item.fieldName}`}>
                    {item.fieldName}: {item.warnings[0]}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      ),
      okText,
      cancelText,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

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
      const validationItems = collectFeatureValidationItems(ruleConfig.condition);
      if (validationItems.length > 0) {
        const validation = await validateFeatureDefinitions(validationItems);
        if (validation.warnings.length > 0 || validation.unknownFields.length > 0 || !validation.valid) {
          const shouldContinue = await confirmFeatureWarnings(
            validation,
            t('featureCatalog.validationWarningTitle'),
            t('featureCatalog.unknownFieldsLabel'),
            t('featureCatalog.warningsLabel'),
            t('featureCatalog.aliasMappingsLabel'),
            t('featureCatalog.itemWarningsLabel'),
            t('featureCatalog.continueSave'),
            t('featureCatalog.backEdit'),
          );
          if (!shouldContinue) {
            return;
          }
        }
      }
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
  }, [form, generatedScript, isNew, navigate, ruleConfig.condition, ruleKey, t]);

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
