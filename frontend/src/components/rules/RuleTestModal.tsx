/**
 * RuleTestModal - 规则测试弹窗
 * 从 Groovy 脚本自动提取字段名生成默认 JSON 参数，调用后端测试接口并回显结果
 */

import { useState, useEffect, useMemo } from 'react';
import { Modal, Input, Button, Alert, Descriptions, Tag, Spin } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { extractFieldNamesFromScript } from '../../utils/dslParser';
import { executeTest } from '../../api/analytics';
import type { TestResult } from '../../types/analytics';

interface RuleTestModalProps {
  open: boolean;
  onClose: () => void;
  ruleKey: string;
  groovyScript: string;
}

export default function RuleTestModal({ open, onClose, ruleKey, groovyScript }: RuleTestModalProps) {
  const { t } = useTranslation();
  const [testInput, setTestInput] = useState('');
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<TestResult | null>(null);
  const [jsonError, setJsonError] = useState('');
  const [execError, setExecError] = useState('');

  // 当 Modal 打开时，自动从脚本提取所有字段名生成默认 JSON
  const defaultJson = useMemo(() => {
    try {
      const fieldNames = extractFieldNamesFromScript(groovyScript);
      const obj: Record<string, number> = {};
      fieldNames.forEach((name) => {
        obj[name] = -1;
      });
      return obj;
    } catch {
      return {};
    }
  }, [groovyScript]);

  useEffect(() => {
    if (open) {
      setTestInput(JSON.stringify(defaultJson, null, 2));
      setResult(null);
      setJsonError('');
      setExecError('');
    }
  }, [open, defaultJson]);

  const handleRun = async () => {
    setJsonError('');
    setExecError('');
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(testInput);
    } catch {
      setJsonError(t('ruleTest.jsonError'));
      return;
    }

    setRunning(true);
    setResult(null);
    try {
      const res = await executeTest(ruleKey, parsed);
      setResult(res);
    } catch (err) {
      setExecError(t('ruleTest.execFailed', { message: err instanceof Error ? err.message : t('ruleTest.unknownError') }));
    } finally {
      setRunning(false);
    }
  };

  return (
    <Modal
      title={t('ruleTest.title')}
      open={open}
      onCancel={onClose}
      width={640}
      footer={[
        <Button key="close" onClick={onClose}>
          {t('common.close')}
        </Button>,
        <Button
          key="run"
          type="primary"
          icon={<ThunderboltOutlined />}
          loading={running}
          onClick={handleRun}
        >
          {t('common.run')}
        </Button>,
      ]}
    >
      {running && (
        <div style={{ textAlign: 'center', padding: '12px 0' }}>
          <Spin tip={t('ruleTest.executing')} />
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 4, fontWeight: 500 }}>{t('ruleTest.testParams')}</div>
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
      {execError && (
        <Alert type="error" message={execError} showIcon closable onClose={() => setExecError('')} style={{ marginBottom: 12 }} />
      )}

      {result && (
        <div style={{ marginTop: 16 }}>
          {result.success ? (
            <>
              <Alert
                type={result.decision === 'PASS' ? 'success' : 'warning'}
                showIcon
                message={t('ruleTest.decisionResult', { decision: result.decision })}
                description={result.reason}
                style={{ marginBottom: 12 }}
              />
              <Descriptions bordered size="small" column={1}>
                <Descriptions.Item label={t('ruleTest.ruleKey')}>{result.ruleKey}</Descriptions.Item>
                <Descriptions.Item label={t('ruleTest.decision')}>{result.decision}</Descriptions.Item>
                <Descriptions.Item label={t('ruleTest.executionTime')}>{result.executionTimeMs}ms</Descriptions.Item>
                <Descriptions.Item label={t('common.status')}>
                  {result.success ? (
                    <Tag color="green">{t('ruleTest.statusSuccess')}</Tag>
                  ) : (
                    <Tag color="red">{t('ruleTest.statusFailed')}</Tag>
                  )}
                </Descriptions.Item>
              </Descriptions>
              {result.matchedConditions && result.matchedConditions.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <span style={{ fontWeight: 500 }}>{t('ruleTest.matchedConditions')}</span>
                  {result.matchedConditions.map((cond, i) => (
                    <Tag key={i} color="blue" style={{ marginBottom: 4 }}>
                      {cond}
                    </Tag>
                  ))}
                </div>
              )}
            </>
          ) : (
            <Alert
              type="error"
              showIcon
              message={t('ruleTest.execFailedTitle')}
              description={result.errorMessage ?? t('ruleTest.unknownError')}
            />
          )}
        </div>
      )}
    </Modal>
  );
}
