/**
 * RuleTestModal - 规则测试弹窗
 * 从 Groovy 脚本自动提取字段名生成默认 JSON 参数，调用后端测试接口并回显结果
 */

import { useState, useEffect, useMemo } from 'react';
import { Modal, Input, Button, Alert, Descriptions, Tag, Spin, message } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
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
  const [testInput, setTestInput] = useState('');
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<TestResult | null>(null);

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
    }
  }, [open, defaultJson]);

  const handleRun = async () => {
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(testInput);
    } catch {
      message.error('JSON 格式错误，请检查输入');
      return;
    }

    setRunning(true);
    setResult(null);
    try {
      const res = await executeTest(ruleKey, parsed);
      setResult(res);
    } catch (err) {
      message.error(`测试执行失败: ${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      setRunning(false);
    }
  };

  return (
    <Modal
      title="测试规则"
      open={open}
      onCancel={onClose}
      width={640}
      footer={[
        <Button key="close" onClick={onClose}>
          关闭
        </Button>,
        <Button
          key="run"
          type="primary"
          icon={<ThunderboltOutlined />}
          loading={running}
          onClick={handleRun}
        >
          运行
        </Button>,
      ]}
    >
      {running && (
        <div style={{ textAlign: 'center', padding: '12px 0' }}>
          <Spin tip="正在执行规则..." />
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 4, fontWeight: 500 }}>测试参数 (JSON)</div>
        <Input.TextArea
          rows={8}
          value={testInput}
          onChange={(e) => setTestInput(e.target.value)}
          style={{ fontFamily: 'monospace' }}
        />
      </div>

      {result && (
        <div style={{ marginTop: 16 }}>
          {result.success ? (
            <>
              <Alert
                type={result.decision === 'PASS' ? 'success' : 'warning'}
                showIcon
                message={`决策结果: ${result.decision}`}
                description={result.reason}
                style={{ marginBottom: 12 }}
              />
              <Descriptions bordered size="small" column={1}>
                <Descriptions.Item label="规则标识">{result.ruleKey}</Descriptions.Item>
                <Descriptions.Item label="决策">{result.decision}</Descriptions.Item>
                <Descriptions.Item label="耗时">{result.executionTimeMs}ms</Descriptions.Item>
                <Descriptions.Item label="状态">
                  {result.success ? (
                    <Tag color="green">成功</Tag>
                  ) : (
                    <Tag color="red">失败</Tag>
                  )}
                </Descriptions.Item>
              </Descriptions>
              {result.matchedConditions && result.matchedConditions.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <span style={{ fontWeight: 500 }}>匹配条件: </span>
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
              message="执行失败"
              description={result.errorMessage ?? '未知错误'}
            />
          )}
        </div>
      )}
    </Modal>
  );
}
