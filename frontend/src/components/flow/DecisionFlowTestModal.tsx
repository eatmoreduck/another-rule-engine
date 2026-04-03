/**
 * DecisionFlowTestModal - 决策流测试弹窗
 * 从 flowGraph JSON + ruleset 引用规则脚本 中提取所有特征字段名，生成默认 JSON
 */

import { useState, useEffect, useCallback } from 'react';
import { Modal, Input, Button, Alert, Descriptions, Tag, Spin } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
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
  const [testInput, setTestInput] = useState('');
  const [loadingFields, setLoadingFields] = useState(false);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<FlowTestResult | null>(null);
  const [jsonError, setJsonError] = useState('');
  const [execError, setExecError] = useState('');

  // 异步提取所有字段及其类型：flowGraph + ruleset 引用规则
  const fetchAllFields = useCallback(async (graphJson: string): Promise<FieldTypeInfo[]> => {
    const typeMap = new Map<string, 'number' | 'string'>();

    // 1. 从 flowGraph JSON 本身提取（condition 节点的 fieldName + threshold 类型）
    extractFieldTypesFromFlowGraph(graphJson).forEach(({ name, inferredType }) => {
      typeMap.set(name, inferredType);
    });

    // 2. 解析 ruleset 节点，获取引用规则的脚本并提取字段类型
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
      // 并发获取所有引用规则的脚本
      if (ruleKeys.length > 0) {
        const rules = await Promise.allSettled(ruleKeys.map((k) => getRule(k)));
        for (const r of rules) {
          if (r.status === 'fulfilled' && r.value.groovyScript) {
            extractFieldTypesFromScript(r.value.groovyScript).forEach(({ name, inferredType }) => {
              // 仅在未推断过类型时设置（flowGraph 中的条件节点优先级更高）
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

  // 保存推断出的字段类型，用于校验
  const [fieldTypes, setFieldTypes] = useState<FieldTypeInfo[]>([]);
  const [typeWarnings, setTypeWarnings] = useState<string[]>([]);

  // Modal 打开时异步加载字段并生成默认 JSON
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
      setJsonError('JSON 格式错误，请检查输入');
      return;
    }

    // 类型校验：检查字段值类型是否与推断类型一致
    const warnings: string[] = [];
    for (const { name, inferredType } of fieldTypes) {
      const value = parsed[name];
      if (value === undefined || value === null) continue; // 缺失字段不校验
      if (inferredType === 'number' && typeof value === 'string') {
        warnings.push(`"${name}" 期望数字类型，当前为字符串 "${value}"`);
      } else if (inferredType === 'string' && typeof value === 'number') {
        warnings.push(`"${name}" 期望字符串类型，当前为数字 ${value}`);
      }
    }
    if (warnings.length > 0) {
      setTypeWarnings(warnings);
      return; // 阻止提交，让用户修正
    }

    setRunning(true);
    setResult(null);
    try {
      const res = await executeFlowTest(flowKey, parsed);
      setResult(res);
    } catch (err) {
      setExecError(`测试执行失败: ${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      setRunning(false);
    }
  };

  const decisionTagColor = (decision: string) =>
    decision === 'PASS' ? 'green' : decision === 'REJECT' ? 'red' : 'orange';

  return (
    <Modal
      title="测试决策流"
      open={open}
      onCancel={onClose}
      width={640}
      footer={[
        <Button key="close" onClick={onClose}>关闭</Button>,
        <Button key="run" type="primary" icon={<ThunderboltOutlined />}
          loading={running || loadingFields} onClick={handleRun}
          disabled={loadingFields}>
          运行
        </Button>,
      ]}
    >
      {loadingFields && (
        <div style={{ textAlign: 'center', padding: '12px 0' }}>
          <Spin tip="正在提取特征字段..." />
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 4, fontWeight: 500 }}>测试参数 (JSON)</div>
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
          message="字段类型不匹配"
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
            message={`决策结果: ${result.decision}`}
            description={result.reason}
            style={{ marginBottom: 12 }}
          />
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="决策流">{flowKey}</Descriptions.Item>
            <Descriptions.Item label="决策">
              <Tag color={decisionTagColor(result.decision)}>{result.decision}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{result.executionTimeMs}ms</Descriptions.Item>
          </Descriptions>
        </div>
      )}
    </Modal>
  );
}
