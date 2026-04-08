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
import { useTranslation } from 'react-i18next';
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

export default function MonitoringPage() {
  const { t } = useTranslation();
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
      message.error(t('monitoring.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [logLevelFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  /** 规则排行表格列定义 */
  const ruleColumns: TableProps<RuleMetrics>['columns'] = [
    {
      title: t('monitoring.columns.ruleName'),
      dataIndex: 'ruleName',
      key: 'ruleName',
      ellipsis: true,
    },
    {
      title: t('monitoring.columns.ruleKey'),
      dataIndex: 'ruleKey',
      key: 'ruleKey',
      ellipsis: true,
    },
    {
      title: t('monitoring.columns.executionCount'),
      dataIndex: 'executionCount',
      key: 'executionCount',
      sorter: (a, b) => a.executionCount - b.executionCount,
      render: (val: number) => val.toLocaleString(),
    },
    {
      title: t('monitoring.columns.hitCount'),
      dataIndex: 'hitCount',
      key: 'hitCount',
      render: (val: number) => val.toLocaleString(),
    },
    {
      title: t('monitoring.columns.hitRate'),
      dataIndex: 'hitRate',
      key: 'hitRate',
      sorter: (a, b) => a.hitRate - b.hitRate,
      render: (val: number) => `${val.toFixed(1)}%`,
    },
    {
      title: t('monitoring.columns.avgExecutionTime'),
      dataIndex: 'avgExecutionTime',
      key: 'avgExecutionTime',
      sorter: (a, b) => a.avgExecutionTime - b.avgExecutionTime,
      render: (val: number) => `${val.toFixed(2)} ms`,
    },
    {
      title: t('monitoring.columns.errorCount'),
      dataIndex: 'errorCount',
      key: 'errorCount',
      render: (val: number) =>
        val > 0 ? <Tag color="red">{val}</Tag> : <Tag color="green">0</Tag>,
    },
    {
      title: t('monitoring.columns.status'),
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) =>
        enabled ? (
          <Tag color="green">{t('monitoring.columns.enabled')}</Tag>
        ) : (
          <Tag color="default">{t('monitoring.columns.disabled')}</Tag>
        ),
    },
  ];

  /** 日志结果对应的 Tag 颜色 */
  function getResultTag(result: ExecutionLog['result']) {
    switch (result) {
      case 'HIT':
        return <Tag color="green">{t('monitoring.results.HIT')}</Tag>;
      case 'MISS':
        return <Tag color="blue">{t('monitoring.results.MISS')}</Tag>;
      case 'ERROR':
        return <Tag color="red">{t('monitoring.results.ERROR')}</Tag>;
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

  /** 日志表格列定义 */
  const logColumns: TableProps<ExecutionLog>['columns'] = [
    {
      title: t('monitoring.columns.executedAt'),
      dataIndex: 'executedAt',
      key: 'executedAt',
      width: 180,
      render: (val: string) => new Date(val).toLocaleString('zh-CN'),
    },
    {
      title: t('monitoring.columns.ruleName'),
      dataIndex: 'ruleName',
      key: 'ruleName',
      ellipsis: true,
    },
    {
      title: t('monitoring.columns.result'),
      dataIndex: 'result',
      key: 'result',
      width: 100,
      render: (result: ExecutionLog['result']) => getResultTag(result),
    },
    {
      title: t('monitoring.columns.time'),
      dataIndex: 'executionTime',
      key: 'executionTime',
      width: 120,
      render: (val: number) => {
        const color = val > 50 ? 'red' : val > 20 ? 'orange' : '#333';
        return <span style={{ color }}>{val.toFixed(2)} ms</span>;
      },
    },
    {
      title: t('monitoring.columns.level'),
      dataIndex: 'level',
      key: 'level',
      width: 80,
      render: (level: LogLevel) => getLevelTag(level),
    },
    {
      title: t('monitoring.columns.errorMessage'),
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
        items={[{ title: t('monitoring.pageTitle') }]}
      />

      <Spin spinning={loading}>
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}>
            <Card>
              <Statistic
                title={t('monitoring.totalExecutions')}
                value={overview.totalExecutions}
                prefix={<ThunderboltOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title={t('monitoring.hitRate')}
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
                title={t('monitoring.avgTime')}
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
                title={t('monitoring.errorRate')}
                value={overview.errorRate}
                precision={2}
                suffix="%"
                prefix={<WarningOutlined />}
                valueStyle={{ color: overview.errorRate > 5 ? '#cf1322' : '#3f8600' }}
              />
            </Card>
          </Col>
        </Row>

        <Card
          title={t('monitoring.ruleRanking')}
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

        <Card
          title={t('monitoring.recentLogs')}
          extra={
            <Space>
              <Select
                placeholder={t('monitoring.logLevel')}
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
