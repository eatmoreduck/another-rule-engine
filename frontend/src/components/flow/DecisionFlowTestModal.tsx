/**
 * DecisionFlowTestModal - 决策流测试弹窗
 * 从 flowGraph JSON + ruleset 引用规则脚本 中提取所有特征字段名，生成默认 JSON
 */

import { useState, useEffect, useCallback } from 'react';
import { Modal, Input, Button, Alert, Descriptions, Tag, Spin } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { extractFieldTypesFromFlowGraph, extractFieldTypesFromScript, type FieldTypeInfo } from '../../utils/dslParser';
import { executeFlowTest, type FlowTestResult } from '../../api/decisionFlows';
import { getRule } from '../../api/rules';

interface DecisionFlowTestModalProps {
  open: boolean;
  onClose: () => void;
  flowKey: string;
  flowGraph: string;
}

export default function DecisionFlowTestModal({ open, onClose, flowKey, flowGraph }: DecisionFlowTestModalProps) {
  const { t } = useTranslation();
  const [testInput, setTestInput] = useState('');
  const [loadingFields, setLoadingFields] = useState(false);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<FlowTestResult | null>(null);
  const [jsonError, setJsonError] = useState('');
  const [execError, setExecError] = useState('');

  // 异步提取所有字段及其类型
  const fetchAllFields = useCallback(async (graphJson: string): Promise<FieldTypeInfo[]> => {
    const typeMap = new Map<string, 'number' | 'string'>();

    extractFieldTypesFromFlowGraph(graphJson).forEach(({ name, inferredType }) => {
      typeMap.set(name, inferredType);
    });

    try {
      const graph = JSON.parse(graphJson);
      const nodes: Array<{ data?: Record<string, unknown> }> = graph?.nodes ?? [];
      const ruleKeys: string[] = [];
      for (const node of nodes) {
        if (node.data?.nodeType === 'ruleset' && Array.isArray(node.data.ruleKeys)) {
          for (const key of node.data.ruleKeys as string[]) {
            ruleKeys.push(key);
          }
        }
      }
      if (ruleKeys.length > 0) {
        const rules = await Promise.allSettled(ruleKeys.map((k) => getRule(k)));
        for (const r of rules) {
          if (r.status === 'fulfilled' && r.value.groovyScript) {
            extractFieldTypesFromScript(r.value.groovyScript).forEach(({ name, inferredType }) => {
              if (!typeMap.has(name)) {
                typeMap.set(name, inferredType);
              }
            });
          }
        }
      }
    } catch {
      // 解析失败，仅用直接提取结果
    }

    return Array.from(typeMap.entries()).map(([name, inferredType]) => ({ name, inferredType }));
  }, []);

  const [fieldTypes, setFieldTypes] = useState<FieldTypeInfo[]>([]);
  const [typeWarnings, setTypeWarnings] = useState<string[]>([]);

  useEffect(() => {
    if (!open) return;
    setResult(null);
    setJsonError('');
    setExecError('');
    setTypeWarnings([]);

    let cancelled = false;
    setLoadingFields(true);

    fetchAllFields(flowGraph).then((fields) => {
      if (cancelled) return;
      setFieldTypes(fields);
      const obj: Record<string, unknown> = {};
      fields.forEach(({ name, inferredType }) => {
        obj[name] = inferredType === 'number' ? -1 : '';
      });
      setTestInput(JSON.stringify(obj, null, 2));
      setLoadingFields(false);
    }).catch(() => {
      if (cancelled) return;
      setFieldTypes([]);
      setTestInput('{}');
      setLoadingFields(false);
    });

    return () => { cancelled = true; };
  }, [open, flowGraph, fetchAllFields]);

  const handleRun = async () => {
    setJsonError('');
    setExecError('');
    setTypeWarnings([]);
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(testInput);
    } catch {
      setJsonError(t('flowTest.jsonError'));
      return;
    }

    const warnings: string[] = [];
    for (const { name, inferredType } of fieldTypes) {
      const value = parsed[name];
      if (value === undefined || value === null) continue;
      if (inferredType === 'number' && typeof value === 'string') {
        warnings.push(t('flowTest.typeWarningNumber', { name, value }));
      } else if (inferredType === 'string' && typeof value === 'number') {
        warnings.push(t('flowTest.typeWarningString', { name, value }));
      }
    }
    if (warnings.length > 0) {
      setTypeWarnings(warnings);
      return;
    }

    setRunning(true);
    setResult(null);
    try {
      const res = await executeFlowTest(flowKey, parsed);
      setResult(res);
    } catch (err) {
      setExecError(t('flowTest.execFailed', { message: err instanceof Error ? err.message : 'Unknown error' }));
    } finally {
      setRunning(false);
    }
  };

  const decisionTagColor = (decision: string) =>
    decision === 'PASS' ? 'green' : decision === 'REJECT' ? 'red' : 'orange';

  return (
    <Modal
      title={t('flowTest.title')}
      open={open}
      onCancel={onClose}
      width={640}
      footer={[
        <Button key="close" onClick={onClose}>{t('common.close')}</Button>,
        <Button key="run" type="primary" icon={<ThunderboltOutlined />}
          loading={running || loadingFields} onClick={handleRun}
          disabled={loadingFields}>
          {t('common.run')}
        </Button>,
      ]}
    >
      {loadingFields && (
        <div style={{ textAlign: 'center', padding: '12px 0' }}>
          <Spin tip={t('flowTest.extractingFields')} />
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 4, fontWeight: 500 }}>{t('flowTest.testParams')}</div>
        <Input.TextArea
          rows={8}
          value={testInput}
          onChange={(e) => { setTestInput(e.target.value); setJsonError(''); }}
          style={{ fontFamily: 'monospace' }}
        />
      </div>

      {jsonError && (
        <Alert type="error" message={jsonError} showIcon closable onClose={() => setJsonError('')} style={{ marginBottom: 12 }} />
      )}
      {typeWarnings.length > 0 && (
        <Alert
          type="warning"
          message={t('flowTest.typeMismatch')}
          description={
            <ul style={{ margin: 0, paddingLeft: 16 }}>
              {typeWarnings.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          }
          showIcon
          closable
          onClose={() => setTypeWarnings([])}
          style={{ marginBottom: 12 }}
        />
      )}
      {execError && (
        <Alert type="error" message={execError} showIcon closable onClose={() => setExecError('')} style={{ marginBottom: 12 }} />
      )}

      {result && (
        <div style={{ marginTop: 16 }}>
          <Alert
            type={result.decision === 'PASS' ? 'success' : result.decision === 'REJECT' ? 'error' : 'warning'}
            showIcon
            message={t('flowTest.decisionResult', { decision: result.decision })}
            description={result.reason}
            style={{ marginBottom: 12 }}
          />
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label={t('flowTest.decisionFlow')}>{flowKey}</Descriptions.Item>
            <Descriptions.Item label={t('flowTest.decisionLabel')}>
              <Tag color={decisionTagColor(result.decision)}>{result.decision}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('flowTest.executionTime')}>{result.executionTimeMs}ms</Descriptions.Item>
          </Descriptions>
        </div>
      )}
    </Modal>
  );
}
