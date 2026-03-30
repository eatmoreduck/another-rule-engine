import { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Col,
  Row,
  Statistic,
  Table,
  Tag,
  Breadcrumb,
  Spin,
  message,
  Select,
  Space,
} from 'antd';
import type { TableProps } from 'antd';
import {
  ThunderboltOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  getMetricsOverview,
  getRuleMetricsRanking,
  getRecentLogs,
} from '../api/metrics';
import type {
  MetricsOverview,
  RuleMetrics,
  ExecutionLog,
  LogLevel,
} from '../types/metrics';

const defaultOverview: MetricsOverview = {
  totalExecutions: 0,
  hitCount: 0,
  hitRate: 0,
  avgExecutionTime: 0,
  errorCount: 0,
  errorRate: 0,
};

/** 规则排行表格列定义 */
const ruleColumns: TableProps<RuleMetrics>['columns'] = [
  {
    title: '规则名称',
    dataIndex: 'ruleName',
    key: 'ruleName',
    ellipsis: true,
  },
  {
    title: '规则 Key',
    dataIndex: 'ruleKey',
    key: 'ruleKey',
    ellipsis: true,
  },
  {
    title: '执行次数',
    dataIndex: 'executionCount',
    key: 'executionCount',
    sorter: (a, b) => a.executionCount - b.executionCount,
    render: (val: number) => val.toLocaleString(),
  },
  {
    title: '命中次数',
    dataIndex: 'hitCount',
    key: 'hitCount',
    render: (val: number) => val.toLocaleString(),
  },
  {
    title: '命中率',
    dataIndex: 'hitRate',
    key: 'hitRate',
    sorter: (a, b) => a.hitRate - b.hitRate,
    render: (val: number) => `${val.toFixed(1)}%`,
  },
  {
    title: '平均耗时',
    dataIndex: 'avgExecutionTime',
    key: 'avgExecutionTime',
    sorter: (a, b) => a.avgExecutionTime - b.avgExecutionTime,
    render: (val: number) => `${val.toFixed(2)} ms`,
  },
  {
    title: '错误次数',
    dataIndex: 'errorCount',
    key: 'errorCount',
    render: (val: number) =>
      val > 0 ? <Tag color="red">{val}</Tag> : <Tag color="green">0</Tag>,
  },
  {
    title: '状态',
    dataIndex: 'enabled',
    key: 'enabled',
    render: (enabled: boolean) =>
      enabled ? (
        <Tag color="green">启用</Tag>
      ) : (
        <Tag color="default">禁用</Tag>
      ),
  },
];

/** 日志结果对应的 Tag 颜色 */
function getResultTag(result: ExecutionLog['result']) {
  switch (result) {
    case 'HIT':
      return <Tag color="green">命中</Tag>;
    case 'MISS':
      return <Tag color="blue">未命中</Tag>;
    case 'ERROR':
      return <Tag color="red">错误</Tag>;
    default:
      return <Tag>{result}</Tag>;
  }
}

/** 日志级别对应的 Tag 颜色 */
function getLevelTag(level: LogLevel) {
  switch (level) {
    case 'INFO':
      return <Tag color="blue">{level}</Tag>;
    case 'WARN':
      return <Tag color="orange">{level}</Tag>;
    case 'ERROR':
      return <Tag color="red">{level}</Tag>;
    default:
      return <Tag>{level}</Tag>;
  }
}

export default function MonitoringPage() {
  const [overview, setOverview] = useState<MetricsOverview>(defaultOverview);
  const [rules, setRules] = useState<RuleMetrics[]>([]);
  const [logs, setLogs] = useState<ExecutionLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [logLevelFilter, setLogLevelFilter] = useState<LogLevel | undefined>();

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [overviewData, rulesData, logsData] = await Promise.all([
        getMetricsOverview(),
        getRuleMetricsRanking({ limit: 20 }),
        getRecentLogs({ limit: 50, level: logLevelFilter }),
      ]);
      setOverview(overviewData);
      setRules(rulesData);
      setLogs(logsData);
    } catch {
      message.error('加载监控数据失败');
    } finally {
      setLoading(false);
    }
  }, [logLevelFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  /** 日志表格列定义 */
  const logColumns: TableProps<ExecutionLog>['columns'] = [
    {
      title: '执行时间',
      dataIndex: 'executedAt',
      key: 'executedAt',
      width: 180,
      render: (val: string) => new Date(val).toLocaleString('zh-CN'),
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      ellipsis: true,
    },
    {
      title: '结果',
      dataIndex: 'result',
      key: 'result',
      width: 100,
      render: (result: ExecutionLog['result']) => getResultTag(result),
    },
    {
      title: '耗时',
      dataIndex: 'executionTime',
      key: 'executionTime',
      width: 120,
      render: (val: number) => {
        const color = val > 50 ? 'red' : val > 20 ? 'orange' : '#333';
        return <span style={{ color }}>{val.toFixed(2)} ms</span>;
      },
    },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 80,
      render: (level: LogLevel) => getLevelTag(level),
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
        items={[{ title: '监控仪表盘' }]}
      />

      <Spin spinning={loading}>
        {/* 顶部统计卡片 */}
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}>
            <Card>
              <Statistic
                title="总执行次数"
                value={overview.totalExecutions}
                prefix={<ThunderboltOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="命中率"
                value={overview.hitRate}
                precision={1}
                suffix="%"
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: overview.hitRate >= 50 ? '#3f8600' : '#cf1322' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="平均耗时"
                value={overview.avgExecutionTime}
                precision={2}
                suffix="ms"
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: overview.avgExecutionTime > 50 ? '#cf1322' : '#333' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="错误率"
                value={overview.errorRate}
                precision={2}
                suffix="%"
                prefix={<WarningOutlined />}
                valueStyle={{ color: overview.errorRate > 5 ? '#cf1322' : '#3f8600' }}
              />
            </Card>
          </Col>
        </Row>

        {/* 规则执行排行 */}
        <Card
          title="规则执行排行"
          style={{ marginBottom: 24 }}
        >
          <Table<RuleMetrics>
            rowKey="ruleKey"
            columns={ruleColumns}
            dataSource={rules}
            pagination={false}
            size="middle"
          />
        </Card>

        {/* 最近执行日志 */}
        <Card
          title="最近执行日志"
          extra={
            <Space>
              <Select
                placeholder="日志级别"
                allowClear
                style={{ width: 120 }}
                value={logLevelFilter}
                onChange={(val) => setLogLevelFilter(val)}
                options={[
                  { value: 'INFO', label: 'INFO' },
                  { value: 'WARN', label: 'WARN' },
                  { value: 'ERROR', label: 'ERROR' },
                ]}
              />
            </Space>
          }
        >
          <Table<ExecutionLog>
            rowKey="id"
            columns={logColumns}
            dataSource={logs}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            size="middle"
          />
        </Card>
      </Spin>
    </>
  );
}
