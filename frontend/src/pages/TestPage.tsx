import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Table,
  Tag,
  Space,
  Breadcrumb,
  message,
  Descriptions,
  Divider,
  Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlayCircleOutlined,
  ExperimentOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { executeTest, executeBatchTest, getTestHistory } from '../api/analytics';
import type { TestResult } from '../types/analytics';

export default function TestPage() {
  const [form] = Form.useForm();
  const [batchForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [batchResults, setBatchResults] = useState<TestResult[]>([]);
  const [historyResults, setHistoryResults] = useState<TestResult[]>([]);
  const [historyRuleKey, setHistoryRuleKey] = useState('');

  /** 执行单次测试 */
  const handleExecuteTest = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      // 解析测试数据 JSON
      let testData: Record<string, unknown>;
      try {
        testData = JSON.parse(values.testData);
      } catch {
        message.error('测试数据格式错误，请输入有效 JSON');
        return;
      }

      const result = await executeTest(values.ruleKey, testData);
      setTestResult(result);

      if (result.success) {
        message.success('测试执行成功');
      } else {
        message.warning('测试执行失败');
      }
    } catch {
      message.error('请填写必填字段');
    } finally {
      setLoading(false);
    }
  };

  /** 执行批量测试 */
  const handleBatchTest = async () => {
    try {
      const values = await batchForm.validateFields();
      setLoading(true);

      // 解析批量测试数据
      let testDataList: Record<string, unknown>[];
      try {
        testDataList = JSON.parse(values.batchData);
        if (!Array.isArray(testDataList)) {
          message.error('批量测试数据必须是 JSON 数组');
          return;
        }
      } catch {
        message.error('批量测试数据格式错误，请输入有效 JSON 数组');
        return;
      }

      const results = await executeBatchTest(values.ruleKey, testDataList);
      setBatchResults(results);

      const successCount = results.filter((r) => r.success).length;
      message.success(`批量测试完成: ${successCount}/${results.length} 成功`);
    } catch {
      message.error('请填写必填字段');
    } finally {
      setLoading(false);
    }
  };

  /** 获取测试历史 */
  const handleGetHistory = async () => {
    if (!historyRuleKey.trim()) {
      message.warning('请输入规则 Key');
      return;
    }
    setLoading(true);
    try {
      const results = await getTestHistory(historyRuleKey);
      setHistoryResults(results);
      if (results.length === 0) {
        message.info('暂无测试历史记录');
      }
    } catch {
      message.error('获取测试历史失败');
    } finally {
      setLoading(false);
    }
  };

  /** 测试结果列定义 */
  const resultColumns: ColumnsType<TestResult> = [
    {
      title: '序号',
      key: 'index',
      width: 60,
      render: (_, __, index) => index + 1,
    },
    {
      title: '决策结果',
      dataIndex: 'decision',
      key: 'decision',
      width: 100,
      render: (val: string) =>
        val === 'PASS' ? (
          <Tag color="green">PASS</Tag>
        ) : (
          <Tag color="red">{val}</Tag>
        ),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: '耗时',
      dataIndex: 'executionTimeMs',
      key: 'executionTimeMs',
      width: 100,
      render: (val: number) => `${val} ms`,
    },
    {
      title: '状态',
      dataIndex: 'success',
      key: 'success',
      width: 80,
      render: (val: boolean) =>
        val ? (
          <Tag color="green">成功</Tag>
        ) : (
          <Tag color="red">失败</Tag>
        ),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      ellipsis: true,
      render: (val?: string) => val ?? '-',
    },
  ];

  return (
    <>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[{ title: '测试验证' }]}
      />

      {/* 单次测试 */}
      <Card
        title={
          <Space>
            <PlayCircleOutlined />
            模拟数据测试
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Form form={form} layout="vertical" style={{ maxWidth: 800 }}>
          <Form.Item
            name="ruleKey"
            label="规则 Key"
            rules={[{ required: true, message: '请输入规则 Key' }]}
          >
            <Input placeholder="请输入要测试的规则 Key" />
          </Form.Item>
          <Form.Item
            name="testData"
            label="测试数据 (JSON)"
            rules={[{ required: true, message: '请输入测试数据' }]}
            extra='请输入 JSON 格式的模拟特征数据，例如: {"amount": 1500, "userId": "u123"}'
          >
            <Input.TextArea
              rows={6}
              placeholder='{"amount": 1500, "userId": "u123", "ip": "192.168.1.1"}'
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={loading}
              onClick={handleExecuteTest}
            >
              执行测试
            </Button>
          </Form.Item>
        </Form>

        {/* 测试结果 */}
        {testResult && (
          <>
            <Divider>测试结果</Divider>
            {testResult.success ? (
              <Alert
                type={testResult.decision === 'PASS' ? 'success' : 'warning'}
                message={`决策: ${testResult.decision}`}
                description={testResult.reason}
                showIcon
                style={{ marginBottom: 16 }}
              />
            ) : (
              <Alert
                type="error"
                message="测试执行失败"
                description={testResult.errorMessage}
                showIcon
                style={{ marginBottom: 16 }}
              />
            )}
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="规则 Key">
                {testResult.ruleKey}
              </Descriptions.Item>
              <Descriptions.Item label="决策结果">
                {testResult.decision}
              </Descriptions.Item>
              <Descriptions.Item label="执行耗时">
                {testResult.executionTimeMs} ms
              </Descriptions.Item>
              <Descriptions.Item label="执行状态">
                {testResult.success ? '成功' : '失败'}
              </Descriptions.Item>
            </Descriptions>
            {testResult.matchedConditions &&
              testResult.matchedConditions.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <strong>匹配条件:</strong>
                  <div style={{ marginTop: 8 }}>
                    {testResult.matchedConditions.map((cond, idx) => (
                      <Tag key={idx} style={{ marginBottom: 4 }}>
                        {cond}
                      </Tag>
                    ))}
                  </div>
                </div>
              )}
          </>
        )}
      </Card>

      {/* 批量测试 */}
      <Card
        title={
          <Space>
            <ExperimentOutlined />
            批量测试
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Form form={batchForm} layout="vertical" style={{ maxWidth: 800 }}>
          <Form.Item
            name="ruleKey"
            label="规则 Key"
            rules={[{ required: true, message: '请输入规则 Key' }]}
          >
            <Input placeholder="请输入要测试的规则 Key" />
          </Form.Item>
          <Form.Item
            name="batchData"
            label="批量测试数据 (JSON 数组)"
            rules={[{ required: true, message: '请输入批量测试数据' }]}
            extra='请输入 JSON 数组，每组数据为一条测试用例'
          >
            <Input.TextArea
              rows={8}
              placeholder={'[\n  {"amount": 500, "userId": "u001"},\n  {"amount": 2000, "userId": "u002"},\n  {"amount": 100, "userId": "u003"}\n]'}
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<ExperimentOutlined />}
              loading={loading}
              onClick={handleBatchTest}
            >
              执行批量测试
            </Button>
          </Form.Item>
        </Form>

        {/* 批量测试结果 */}
        {batchResults.length > 0 && (
          <>
            <Divider>批量测试结果 ({batchResults.length} 条)</Divider>
            <Table
              rowKey={(_, index) => String(index)}
              columns={resultColumns}
              dataSource={batchResults}
              pagination={false}
              size="small"
            />
          </>
        )}
      </Card>

      {/* 测试历史 */}
      <Card
        title={
          <Space>
            <HistoryOutlined />
            测试历史
          </Space>
        }
      >
        <Space style={{ marginBottom: 16 }}>
          <Input
            placeholder="请输入规则 Key"
            value={historyRuleKey}
            onChange={(e) => setHistoryRuleKey(e.target.value)}
            style={{ width: 300 }}
          />
          <Button
            icon={<HistoryOutlined />}
            loading={loading}
            onClick={handleGetHistory}
          >
            查询历史
          </Button>
        </Space>

        {historyResults.length > 0 && (
          <Table
            rowKey={(_, index) => String(index)}
            columns={resultColumns}
            dataSource={historyResults}
            pagination={{ pageSize: 10 }}
            size="small"
          />
        )}
      </Card>
    </>
  );
}
