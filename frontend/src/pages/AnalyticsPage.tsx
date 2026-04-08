import { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Table,
  Tag,
  Space,
  Breadcrumb,
  message,
  DatePicker,
  Button,
  Statistic,
  Row,
  Col,
  Descriptions,
  Spin,
  Empty,
  Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BarChartOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  DisconnectOutlined,
  SearchOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  detectAllConflicts,
  getAnalyticsOverview,
  getDependencyGraph,
} from '../api/analytics';
import type {
  ConflictResult,
  RuleAnalytics,
  DependencyGraph,
  DependencyEdge,
} from '../types/analytics';

const { RangePicker } = DatePicker;

export default function AnalyticsPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<'analytics' | 'conflicts' | 'dependencies'>('analytics');

  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(7, 'day'),
    dayjs(),
  ]);
  const [analyticsData, setAnalyticsData] = useState<RuleAnalytics[]>([]);

  const [conflicts, setConflicts] = useState<ConflictResult[]>([]);

  const [dependencyGraph, setDependencyGraph] = useState<DependencyGraph | null>(null);

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getAnalyticsOverview(
        dateRange[0].format('YYYY-MM-DD'),
        dateRange[1].format('YYYY-MM-DD'),
      );
      setAnalyticsData(data);
    } catch {
      message.error(t('analytics.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [dateRange]);

  const fetchConflicts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await detectAllConflicts();
      setConflicts(data);
      message.success(t('analytics.conflictDetected', { count: data.length }));
    } catch {
      message.error(t('analytics.conflictDetectFailed'));
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchDependencies = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getDependencyGraph();
      setDependencyGraph(data);
    } catch {
      message.error(t('analytics.dependencyLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeTab === 'analytics') {
      fetchAnalytics();
    } else if (activeTab === 'conflicts') {
      fetchConflicts();
    } else if (activeTab === 'dependencies') {
      fetchDependencies();
    }
  }, [activeTab, fetchAnalytics, fetchConflicts, fetchDependencies]);

  const analyticsColumns: ColumnsType<RuleAnalytics> = [
    {
      title: t('analytics.columns.ruleKey'),
      dataIndex: 'ruleKey',
      key: 'ruleKey',
      ellipsis: true,
    },
    {
      title: t('analytics.columns.ruleName'),
      dataIndex: 'ruleName',
      key: 'ruleName',
      ellipsis: true,
    },
    {
      title: t('analytics.columns.totalExecutions'),
      dataIndex: 'totalExecutions',
      key: 'totalExecutions',
      sorter: (a, b) => a.totalExecutions - b.totalExecutions,
      render: (val: number) => val.toLocaleString(),
    },
    {
      title: t('analytics.columns.hitCount'),
      dataIndex: 'hitCount',
      key: 'hitCount',
      render: (val: number) => val.toLocaleString(),
    },
    {
      title: t('analytics.columns.hitRate'),
      dataIndex: 'hitRate',
      key: 'hitRate',
      sorter: (a, b) => a.hitRate - b.hitRate,
      render: (val: number) => (
        <span style={{ color: val >= 50 ? '#3f8600' : '#cf1322' }}>
          {val.toFixed(1)}%
        </span>
      ),
    },
    {
      title: t('analytics.columns.rejectRate'),
      dataIndex: 'rejectRate',
      key: 'rejectRate',
      render: (val: number) => `${val.toFixed(1)}%`,
    },
    {
      title: t('analytics.columns.errorRate'),
      dataIndex: 'errorRate',
      key: 'errorRate',
      render: (val: number) =>
        val > 5 ? (
          <Tag color="red">{val.toFixed(1)}%</Tag>
        ) : (
          <Tag color="green">{val.toFixed(1)}%</Tag>
        ),
    },
    {
      title: t('analytics.columns.avgExecutionTime'),
      dataIndex: 'avgExecutionTimeMs',
      key: 'avgExecutionTimeMs',
      sorter: (a, b) => a.avgExecutionTimeMs - b.avgExecutionTimeMs,
      render: (val: number) => (
        <span style={{ color: val > 50 ? '#cf1322' : '#333' }}>
          {val.toFixed(2)} ms
        </span>
      ),
    },
    {
      title: t('analytics.columns.p99ExecutionTime'),
      dataIndex: 'p99ExecutionTimeMs',
      key: 'p99ExecutionTimeMs',
      render: (val: number) => `${val.toFixed(2)} ms`,
    },
  ];

  const conflictColumns: ColumnsType<ConflictResult> = [
    {
      title: t('analytics.columns.conflictType'),
      dataIndex: 'conflictType',
      key: 'conflictType',
      width: 140,
      render: (val: string) => {
        const colorMap: Record<string, string> = {
          CONDITION_CONFLICT: 'red',
          DECISION_CONFLICT: 'orange',
        };
        return <Tag color={colorMap[val] || 'default'}>{val}</Tag>;
      },
    },
    {
      title: t('analytics.columns.rule1'),
      key: 'rule1',
      width: 160,
      render: (_, record) => (
        <span>
          {record.ruleName1}
          <br />
          <small style={{ color: '#999' }}>{record.ruleKey1}</small>
        </span>
      ),
    },
    {
      title: t('analytics.columns.rule2'),
      key: 'rule2',
      width: 160,
      render: (_, record) => (
        <span>
          {record.ruleName2}
          <br />
          <small style={{ color: '#999' }}>{record.ruleKey2}</small>
        </span>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('analytics.columns.severity'),
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (val: string) => {
        const colorMap: Record<string, string> = {
          HIGH: 'red',
          MEDIUM: 'orange',
          LOW: 'blue',
        };
        return <Tag color={colorMap[val] || 'default'}>{val}</Tag>;
      },
    },
  ];

  const dependencyColumns: ColumnsType<DependencyEdge> = [
    {
      title: t('analytics.columns.source'),
      dataIndex: 'source',
      key: 'source',
      width: 150,
    },
    {
      title: t('analytics.columns.target'),
      dataIndex: 'target',
      key: 'target',
      width: 150,
    },
    {
      title: t('analytics.columns.dependencyType'),
      dataIndex: 'dependencyType',
      key: 'dependencyType',
      width: 150,
      render: (val: string) => {
        const labelMap: Record<string, string> = {
          FEATURE_DEPENDENCY: t('analytics.featureDependency'),
        };
        return <Tag color="blue">{labelMap[val] || val}</Tag>;
      },
    },
    {
      title: t('analytics.columns.sharedFeatures'),
      dataIndex: 'sharedFeatureList',
      key: 'sharedFeatureList',
      render: (val: string[]) =>
        val?.map((f, i) => <Tag key={i}>{f}</Tag>) || '-',
    },
  ];

  return (
    <>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[{ title: t('analytics.pageTitle') }]}
      />

      <Space style={{ marginBottom: 16 }}>
        <Button
          type={activeTab === 'analytics' ? 'primary' : 'default'}
          icon={<BarChartOutlined />}
          onClick={() => setActiveTab('analytics')}
        >
          {t('analytics.effectAnalysis')}
        </Button>
        <Button
          type={activeTab === 'conflicts' ? 'primary' : 'default'}
          icon={<WarningOutlined />}
          onClick={() => setActiveTab('conflicts')}
        >
          {t('analytics.conflictDetection')}
        </Button>
        <Button
          type={activeTab === 'dependencies' ? 'primary' : 'default'}
          icon={<DisconnectOutlined />}
          onClick={() => setActiveTab('dependencies')}
        >
          {t('analytics.dependencyAnalysis')}
        </Button>
      </Space>

      <Spin spinning={loading}>
        {activeTab === 'analytics' && (
          <>
            <Card style={{ marginBottom: 16 }}>
              <Space>
                <span>{t('analytics.timeRange')}</span>
                <RangePicker
                  value={dateRange}
                  onChange={(dates) => {
                    if (dates && dates[0] && dates[1]) {
                      setDateRange([dates[0], dates[1]]);
                    }
                  }}
                />
                <Button
                  icon={<SearchOutlined />}
                  onClick={fetchAnalytics}
                  loading={loading}
                >
                  {t('analytics.query')}
                </Button>
              </Space>
            </Card>

            {analyticsData.length > 0 && (
              <Row gutter={16} style={{ marginBottom: 24 }}>
                <Col span={6}>
                  <Card>
                    <Statistic
                      title={t('analytics.summary.ruleCount')}
                      value={analyticsData.length}
                      prefix={<CheckCircleOutlined />}
                    />
                  </Card>
                </Col>
                <Col span={6}>
                  <Card>
                    <Statistic
                      title={t('analytics.summary.totalExecutions')}
                      value={analyticsData.reduce(
                        (sum, a) => sum + a.totalExecutions,
                        0,
                      )}
                    />
                  </Card>
                </Col>
                <Col span={6}>
                  <Card>
                    <Statistic
                      title={t('analytics.summary.avgHitRate')}
                      value={
                        analyticsData.length > 0
                          ? analyticsData.reduce(
                              (sum, a) => sum + a.hitRate,
                              0,
                            ) / analyticsData.length
                          : 0
                      }
                      precision={1}
                      suffix="%"
                    />
                  </Card>
                </Col>
                <Col span={6}>
                  <Card>
                    <Statistic
                      title={t('analytics.summary.avgExecutionTime')}
                      value={
                        analyticsData.length > 0
                          ? analyticsData.reduce(
                              (sum, a) => sum + a.avgExecutionTimeMs,
                              0,
                            ) / analyticsData.length
                          : 0
                      }
                      precision={2}
                      suffix="ms"
                    />
                  </Card>
                </Col>
              </Row>
            )}

            <Card title={t('analytics.effectAnalysisTitle')}>
              <Table
                rowKey="ruleKey"
                columns={analyticsColumns}
                dataSource={analyticsData}
                pagination={{ pageSize: 10 }}
                size="middle"
                locale={{ emptyText: t('analytics.noAnalyticsData') }}
              />
            </Card>
          </>
        )}

        {activeTab === 'conflicts' && (
          <Card
            title={t('analytics.conflictTitle')}
            extra={
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchConflicts}
                loading={loading}
              >
                {t('analytics.reDetect')}
              </Button>
            }
          >
            {conflicts.length === 0 ? (
              <Empty
                description={t('analytics.noConflicts')}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ) : (
              <>
                <Alert
                  type="warning"
                  message={t('analytics.conflictWarning', { count: conflicts.length })}
                  description={t('analytics.conflictWarningDesc')}
                  showIcon
                  style={{ marginBottom: 16 }}
                />
                <Table
                  rowKey={(_, index) => String(index)}
                  columns={conflictColumns}
                  dataSource={conflicts}
                  pagination={{ pageSize: 10 }}
                  size="middle"
                />
              </>
            )}
          </Card>
        )}

        {activeTab === 'dependencies' && (
          <>
            <Card
              title={t('analytics.dependencyTitle')}
              extra={
                <Button
                  icon={<ReloadOutlined />}
                  onClick={fetchDependencies}
                  loading={loading}
                >
                  {t('common.refresh')}
                </Button>
              }
              style={{ marginBottom: 24 }}
            >
              {dependencyGraph && dependencyGraph.nodes.length > 0 ? (
                <>
                  <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
                    <Descriptions.Item label={t('analytics.nodeCount')}>
                      {dependencyGraph.nodes.length}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('analytics.edgeCount')}>
                      {dependencyGraph.edges.length}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('analytics.sharedFeatureCount')}>
                      {dependencyGraph.sharedFeatures?.length ?? 0}
                    </Descriptions.Item>
                  </Descriptions>

                  {dependencyGraph.sharedFeatures &&
                    dependencyGraph.sharedFeatures.length > 0 && (
                      <div style={{ marginBottom: 16 }}>
                        <strong>{t('analytics.sharedFeaturesLabel')}</strong>
                        <div style={{ marginTop: 8 }}>
                          {dependencyGraph.sharedFeatures.map((f, i) => (
                            <Tag key={i} color="blue" style={{ marginBottom: 4 }}>
                              {f}
                            </Tag>
                          ))}
                        </div>
                      </div>
                    )}
                </>
              ) : (
                <Empty
                  description={t('analytics.noDependencyData')}
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )}
            </Card>

            {dependencyGraph && dependencyGraph.edges.length > 0 && (
              <Card title={t('analytics.dependencyDetailTitle')}>
                <Table
                  rowKey={(_, index) => String(index)}
                  columns={dependencyColumns}
                  dataSource={dependencyGraph.edges}
                  pagination={{ pageSize: 10 }}
                  size="middle"
                />
              </Card>
            )}
          </>
        )}
      </Spin>
    </>
  );
}
