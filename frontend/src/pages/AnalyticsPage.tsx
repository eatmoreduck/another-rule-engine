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
  Divider,
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
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<'analytics' | 'conflicts' | 'dependencies'>('analytics');

  // 分析数据
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(7, 'day'),
    dayjs(),
  ]);
  const [analyticsData, setAnalyticsData] = useState<RuleAnalytics[]>([]);

  // 冲突数据
  const [conflicts, setConflicts] = useState<ConflictResult[]>([]);

  // 依赖数据
  const [dependencyGraph, setDependencyGraph] = useState<DependencyGraph | null>(null);

  /** 加载分析数据 */
  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getAnalyticsOverview(
        dateRange[0].format('YYYY-MM-DD'),
        dateRange[1].format('YYYY-MM-DD'),
      );
      setAnalyticsData(data);
    } catch {
      message.error('加载分析数据失败');
    } finally {
      setLoading(false);
    }
  }, [dateRange]);

  /** 加载冲突数据 */
  const fetchConflicts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await detectAllConflicts();
      setConflicts(data);
      message.success(`检测完成，发现 ${data.length} 个冲突`);
    } catch {
      message.error('冲突检测失败');
    } finally {
      setLoading(false);
    }
  }, []);

  /** 加载依赖关系 */
  const fetchDependencies = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getDependencyGraph();
      setDependencyGraph(data);
    } catch {
      message.error('加载依赖关系失败');
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

  /** 分析数据表格列 */
  const analyticsColumns: ColumnsType<RuleAnalytics> = [
    {
      title: '规则 Key',
      dataIndex: 'ruleKey',
      key: 'ruleKey',
      ellipsis: true,
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      ellipsis: true,
    },
    {
      title: '总执行',
      dataIndex: 'totalExecutions',
      key: 'totalExecutions',
      sorter: (a, b) => a.totalExecutions - b.totalExecutions,
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
      render: (val: number) => (
        <span style={{ color: val >= 50 ? '#3f8600' : '#cf1322' }}>
          {val.toFixed(1)}%
        </span>
      ),
    },
    {
      title: '拦截率',
      dataIndex: 'rejectRate',
      key: 'rejectRate',
      render: (val: number) => `${val.toFixed(1)}%`,
    },
    {
      title: '错误率',
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
      title: '平均耗时',
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
      title: 'P99 耗时',
      dataIndex: 'p99ExecutionTimeMs',
      key: 'p99ExecutionTimeMs',
      render: (val: number) => `${val.toFixed(2)} ms`,
    },
  ];

  /** 冲突结果表格列 */
  const conflictColumns: ColumnsType<ConflictResult> = [
    {
      title: '冲突类型',
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
      title: '规则 1',
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
      title: '规则 2',
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
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '严重程度',
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

  /** 依赖边表格列 */
  const dependencyColumns: ColumnsType<DependencyEdge> = [
    {
      title: '源规则',
      dataIndex: 'source',
      key: 'source',
      width: 150,
    },
    {
      title: '目标规则',
      dataIndex: 'target',
      key: 'target',
      width: 150,
    },
    {
      title: '依赖类型',
      dataIndex: 'dependencyType',
      key: 'dependencyType',
      width: 150,
      render: (val: string) => {
        const labelMap: Record<string, string> = {
          FEATURE_DEPENDENCY: '特征依赖',
        };
        return <Tag color="blue">{labelMap[val] || val}</Tag>;
      },
    },
    {
      title: '共享特征',
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
        items={[{ title: '分析中心' }]}
      />

      {/* Tab 切换 */}
      <Space style={{ marginBottom: 16 }}>
        <Button
          type={activeTab === 'analytics' ? 'primary' : 'default'}
          icon={<BarChartOutlined />}
          onClick={() => setActiveTab('analytics')}
        >
          效果分析
        </Button>
        <Button
          type={activeTab === 'conflicts' ? 'primary' : 'default'}
          icon={<WarningOutlined />}
          onClick={() => setActiveTab('conflicts')}
        >
          冲突检测
        </Button>
        <Button
          type={activeTab === 'dependencies' ? 'primary' : 'default'}
          icon={<DisconnectOutlined />}
          onClick={() => setActiveTab('dependencies')}
        >
          依赖分析
        </Button>
      </Space>

      <Spin spinning={loading}>
        {/* 效果分析 */}
        {activeTab === 'analytics' && (
          <>
            <Card style={{ marginBottom: 16 }}>
              <Space>
                <span>时间范围:</span>
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
                  查询
                </Button>
              </Space>
            </Card>

            {/* 汇总统计 */}
            {analyticsData.length > 0 && (
              <Row gutter={16} style={{ marginBottom: 24 }}>
                <Col span={6}>
                  <Card>
                    <Statistic
                      title="规则总数"
                      value={analyticsData.length}
                      prefix={<CheckCircleOutlined />}
                    />
                  </Card>
                </Col>
                <Col span={6}>
                  <Card>
                    <Statistic
                      title="总执行次数"
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
                      title="平均命中率"
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
                      title="平均耗时"
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

            <Card title="规则效果分析">
              <Table
                rowKey="ruleKey"
                columns={analyticsColumns}
                dataSource={analyticsData}
                pagination={{ pageSize: 10 }}
                size="middle"
                locale={{ emptyText: '暂无分析数据' }}
              />
            </Card>
          </>
        )}

        {/* 冲突检测 */}
        {activeTab === 'conflicts' && (
          <Card
            title="规则冲突检测"
            extra={
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchConflicts}
                loading={loading}
              >
                重新检测
              </Button>
            }
          >
            {conflicts.length === 0 ? (
              <Empty
                description="未发现规则冲突"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ) : (
              <>
                <Alert
                  type="warning"
                  message={`发现 ${conflicts.length} 个规则冲突`}
                  description="建议优先处理 HIGH 级别的冲突，以避免规则执行结果不一致。"
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

        {/* 依赖分析 */}
        {activeTab === 'dependencies' && (
          <>
            <Card
              title="规则依赖关系图"
              extra={
                <Button
                  icon={<ReloadOutlined />}
                  onClick={fetchDependencies}
                  loading={loading}
                >
                  刷新
                </Button>
              }
              style={{ marginBottom: 24 }}
            >
              {dependencyGraph && dependencyGraph.nodes.length > 0 ? (
                <>
                  <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
                    <Descriptions.Item label="规则数量">
                      {dependencyGraph.nodes.length}
                    </Descriptions.Item>
                    <Descriptions.Item label="依赖关系数">
                      {dependencyGraph.edges.length}
                    </Descriptions.Item>
                    <Descriptions.Item label="共享特征数">
                      {dependencyGraph.sharedFeatures?.length ?? 0}
                    </Descriptions.Item>
                  </Descriptions>

                  {dependencyGraph.sharedFeatures &&
                    dependencyGraph.sharedFeatures.length > 0 && (
                      <div style={{ marginBottom: 16 }}>
                        <strong>共享特征:</strong>
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
                  description="暂无依赖关系数据"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )}
            </Card>

            {dependencyGraph && dependencyGraph.edges.length > 0 && (
              <Card title="依赖关系详情">
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
