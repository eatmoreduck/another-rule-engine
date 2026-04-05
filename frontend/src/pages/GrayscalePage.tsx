import { useEffect, useState, useCallback } from 'react';
import {
  Button,
  Card,
  Table,
  Tag,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  message,
  Breadcrumb,
  Progress,
  Descriptions,
  Statistic,
  Row,
  Col,
  Tooltip,
  Popconfirm,
} from 'antd';
import {
  PlusOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  CheckCircleOutlined,
  RollbackOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  createGrayscale,
  startGrayscale,
  pauseGrayscale,
  completeGrayscale,
  rollbackGrayscale,
  getGrayscaleReport,
  getGrayscales,
} from '../api/grayscale';
import type {
  GrayscaleRecord,
  GrayscaleReport,
  GrayscaleStatusEnum,
  GrayscaleQueryParams,
} from '../types/grayscale';
import { GrayscaleStatusEnum as GSEnum } from '../types/grayscale';
import Access from '../components/AccessControl';

/** 状态对应的颜色和标签 */
const STATUS_MAP: Record<GrayscaleStatusEnum, { color: string; label: string }> = {
  [GSEnum.DRAFT]: { color: 'default', label: '草稿' },
  [GSEnum.RUNNING]: { color: 'processing', label: '进行中' },
  [GSEnum.PAUSED]: { color: 'warning', label: '已暂停' },
  [GSEnum.COMPLETED]: { color: 'success', label: '已完成' },
  [GSEnum.ROLLED_BACK]: { color: 'error', label: '已回滚' },
};

export default function GrayscalePage() {
  const [data, setData] = useState<GrayscaleRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [queryParams, setQueryParams] = useState<GrayscaleQueryParams>({ page: 0, size: 20 });
  const [statusFilter, setStatusFilter] = useState<GrayscaleStatusEnum | undefined>();

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [form] = Form.useForm();

  const [reportModalOpen, setReportModalOpen] = useState(false);
  const [reportLoading, setReportLoading] = useState(false);
  const [currentReport, setCurrentReport] = useState<GrayscaleReport | null>(null);

  /** 加载灰度列表 */
  const loadData = useCallback(async (params?: GrayscaleQueryParams) => {
    setLoading(true);
    try {
      const result = await getGrayscales(params);
      setData(result.content);
      setTotal(result.totalElements);
    } catch {
      message.error('加载灰度列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData(queryParams);
  }, [queryParams, loadData]);

  /** 筛选状态变化 */
  const handleStatusFilter = (value: GrayscaleStatusEnum | undefined) => {
    setStatusFilter(value);
    const params = { ...queryParams, status: value, page: 0 };
    setQueryParams(params);
    loadData(params);
  };

  /** 分页变化 */
  const handlePageChange = (page: number, pageSize: number) => {
    const params = { ...queryParams, page: page - 1, size: pageSize };
    setQueryParams(params);
    loadData(params);
  };

  /** 创建灰度 */
  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreateLoading(true);
      await createGrayscale(values);
      message.success('灰度配置创建成功');
      setCreateModalOpen(false);
      form.resetFields();
      loadData(queryParams);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        message.error('创建灰度配置失败');
      }
    } finally {
      setCreateLoading(false);
    }
  };

  /** 操作：启动灰度 */
  const handleStart = async (id: number) => {
    try {
      await startGrayscale(id);
      message.success('灰度已启动');
      loadData(queryParams);
    } catch {
      message.error('启动灰度失败');
    }
  };

  /** 操作：暂停灰度 */
  const handlePause = async (id: number) => {
    try {
      await pauseGrayscale(id);
      message.success('灰度已暂停');
      loadData(queryParams);
    } catch {
      message.error('暂停灰度失败');
    }
  };

  /** 操作：完成灰度（全量发布） */
  const handleComplete = async (id: number) => {
    try {
      await completeGrayscale(id);
      message.success('灰度已完成，已全量发布');
      loadData(queryParams);
    } catch {
      message.error('完成灰度失败');
    }
  };

  /** 操作：回滚灰度 */
  const handleRollback = async (id: number) => {
    try {
      await rollbackGrayscale(id);
      message.success('灰度已回滚');
      loadData(queryParams);
    } catch {
      message.error('回滚灰度失败');
    }
  };

  /** 查看对比报告 */
  const handleViewReport = async (id: number) => {
    setReportModalOpen(true);
    setReportLoading(true);
    try {
      const report = await getGrayscaleReport(id);
      setCurrentReport(report);
    } catch {
      message.error('加载灰度报告失败');
    } finally {
      setReportLoading(false);
    }
  };

  /** 表格列定义 */
  const columns: ColumnsType<GrayscaleRecord> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      ellipsis: true,
    },
    {
      title: '规则 Key',
      dataIndex: 'ruleKey',
      ellipsis: true,
    },
    {
      title: '当前版本',
      dataIndex: 'currentVersion',
      width: 100,
      align: 'center',
    },
    {
      title: '灰度版本',
      dataIndex: 'grayscaleVersion',
      width: 100,
      align: 'center',
    },
    {
      title: '灰度比例',
      dataIndex: 'percentage',
      width: 160,
      render: (val: number) => <Progress percent={val} size="small" />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: GrayscaleStatusEnum) => {
        const info = STATUS_MAP[status];
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      width: 100,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Access permission="api:grayscale:manage">
            {record.status === GSEnum.DRAFT && (
              <Tooltip title="启动灰度">
                <Button
                  type="link"
                  size="small"
                  icon={<PlayCircleOutlined />}
                  onClick={() => handleStart(record.id)}
                />
              </Tooltip>
            )}
            {record.status === GSEnum.RUNNING && (
              <Tooltip title="暂停灰度">
                <Button
                  type="link"
                  size="small"
                  icon={<PauseCircleOutlined />}
                  onClick={() => handlePause(record.id)}
                />
              </Tooltip>
            )}
            {record.status === GSEnum.RUNNING && (
              <Popconfirm
                title="确认完成灰度？灰度版本将全量发布。"
                onConfirm={() => handleComplete(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Tooltip title="完成灰度（全量发布）">
                  <Button type="link" size="small" icon={<CheckCircleOutlined />} />
                </Tooltip>
              </Popconfirm>
            )}
            {(record.status === GSEnum.RUNNING || record.status === GSEnum.PAUSED) && (
              <Popconfirm
                title="确认回滚？将恢复到当前稳定版本。"
                onConfirm={() => handleRollback(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Tooltip title="回滚灰度">
                  <Button type="link" size="small" danger icon={<RollbackOutlined />} />
                </Tooltip>
              </Popconfirm>
            )}
          </Access>
          {(record.status === GSEnum.RUNNING || record.status === GSEnum.PAUSED || record.status === GSEnum.COMPLETED) && (
            <Tooltip title="查看对比报告">
              <Button
                type="link"
                size="small"
                icon={<BarChartOutlined />}
                onClick={() => handleViewReport(record.id)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '灰度发布' }]} />

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Space>
            <Select
              placeholder="状态筛选"
              allowClear
              style={{ width: 150 }}
              value={statusFilter}
              onChange={handleStatusFilter}
              options={Object.entries(STATUS_MAP).map(([value, { label }]) => ({
                value,
                label,
              }))}
            />
          </Space>
          <Access permission="api:grayscale:manage">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              新建灰度
            </Button>
          </Access>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            current: (queryParams.page ?? 0) + 1,
            pageSize: queryParams.size ?? 20,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: handlePageChange,
          }}
        />
      </Card>

      {/* 创建灰度弹窗 */}
      <Modal
        title="新建灰度配置"
        open={createModalOpen}
        onOk={handleCreate}
        onCancel={() => {
          setCreateModalOpen(false);
          form.resetFields();
        }}
        confirmLoading={createLoading}
        okText="创建"
        cancelText="取消"
        width={520}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="ruleKey"
            label="规则 Key"
            rules={[{ required: true, message: '请输入规则 Key' }]}
          >
            <Input placeholder="请输入要灰度的规则 Key" />
          </Form.Item>
          <Form.Item
            name="grayscaleVersion"
            label="灰度版本号"
            rules={[{ required: true, message: '请输入灰度版本号' }]}
          >
            <InputNumber
              min={1}
              style={{ width: '100%' }}
              placeholder="请输入灰度版本号"
            />
          </Form.Item>
          <Form.Item
            name="percentage"
            label="灰度百分比"
            rules={[{ required: true, message: '请输入灰度百分比' }]}
            extra="灰度流量占总流量的比例（1-100）"
          >
            <InputNumber
              min={1}
              max={100}
              style={{ width: '100%' }}
              placeholder="请输入灰度百分比"
              suffix="%"
            />
          </Form.Item>
          <Form.Item name="description" label="灰度描述">
            <Input.TextArea rows={3} placeholder="请输入灰度说明（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 灰度对比报告弹窗 */}
      <Modal
        title="灰度对比报告"
        open={reportModalOpen}
        onCancel={() => {
          setReportModalOpen(false);
          setCurrentReport(null);
        }}
        footer={null}
        width={720}
      >
        {reportLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>加载中...</div>
        ) : currentReport ? (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 24 }}>
              <Descriptions.Item label="规则名称">{currentReport.ruleName}</Descriptions.Item>
              <Descriptions.Item label="规则 Key">{currentReport.ruleKey}</Descriptions.Item>
              <Descriptions.Item label="当前版本">v{currentReport.currentVersion}</Descriptions.Item>
              <Descriptions.Item label="灰度版本">v{currentReport.grayscaleVersion}</Descriptions.Item>
              <Descriptions.Item label="灰度比例">{currentReport.percentage}%</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_MAP[currentReport.status].color}>
                  {STATUS_MAP[currentReport.status].label}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            <Row gutter={24}>
              {/* 当前版本指标 */}
              <Col span={12}>
                <Card
                  title="当前版本"
                  size="small"
                  style={{ textAlign: 'center' }}
                  headStyle={{ background: '#e6f7ff' }}
                >
                  <Row gutter={[16, 16]}>
                    <Col span={12}>
                      <Statistic title="执行次数" value={currentReport.currentMetrics.executionCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="命中次数" value={currentReport.currentMetrics.hitCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="命中率" value={currentReport.currentMetrics.hitRate} suffix="%" precision={2} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="平均耗时" value={currentReport.currentMetrics.avgDuration} suffix="ms" precision={2} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="最大耗时" value={currentReport.currentMetrics.maxDuration} suffix="ms" precision={2} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="最小耗时" value={currentReport.currentMetrics.minDuration} suffix="ms" precision={2} />
                    </Col>
                  </Row>
                </Card>
              </Col>

              {/* 灰度版本指标 */}
              <Col span={12}>
                <Card
                  title="灰度版本"
                  size="small"
                  style={{ textAlign: 'center' }}
                  headStyle={{ background: '#fff7e6' }}
                >
                  <Row gutter={[16, 16]}>
                    <Col span={12}>
                      <Statistic title="执行次数" value={currentReport.grayscaleMetrics.executionCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="命中次数" value={currentReport.grayscaleMetrics.hitCount} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="命中率" value={currentReport.grayscaleMetrics.hitRate} suffix="%" precision={2} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="平均耗时" value={currentReport.grayscaleMetrics.avgDuration} suffix="ms" precision={2} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="最大耗时" value={currentReport.grayscaleMetrics.maxDuration} suffix="ms" precision={2} />
                    </Col>
                    <Col span={12}>
                      <Statistic title="最小耗时" value={currentReport.grayscaleMetrics.minDuration} suffix="ms" precision={2} />
                    </Col>
                  </Row>
                </Card>
              </Col>
            </Row>
          </>
        ) : (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
            暂无报告数据
          </div>
        )}
      </Modal>
    </>
  );
}
