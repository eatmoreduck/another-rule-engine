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
  Tabs,
} from 'antd';
import {
  PlusOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  CheckCircleOutlined,
  RollbackOutlined,
  BarChartOutlined,
  FileTextOutlined,
  EyeOutlined,
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
  getCanaryLogs,
  getRuleVersions,
  getDecisionFlowVersions,
} from '../api/grayscale';
import type {
  VersionOption,
} from '../api/grayscale';
import type {
  GrayscaleRecord,
  GrayscaleReport,
  GrayscaleStatusEnum,
  GrayscaleQueryParams,
  CanaryExecutionLog,
} from '../types/grayscale';
import {
  GrayscaleStatusEnum as GSEnum,
  GrayscaleTargetType,
  GrayscaleStrategyType,
} from '../types/grayscale';
import { getRulesForSelect } from '../api/rules';
import { getDecisionFlows } from '../api/decisionFlows';
import Access from '../components/AccessControl';
import DiffViewer from '../components/DiffViewer';
import RuleRenderedDiff from '../components/RuleRenderedDiff';
import FlowGraphDiff from '../components/FlowGraphDiff';

/** 状态对应的颜色和标签 */
const STATUS_MAP: Record<GrayscaleStatusEnum, { color: string; label: string }> = {
  [GSEnum.DRAFT]: { color: 'default', label: '草稿' },
  [GSEnum.RUNNING]: { color: 'processing', label: '进行中' },
  [GSEnum.PAUSED]: { color: 'warning', label: '已暂停' },
  [GSEnum.COMPLETED]: { color: 'success', label: '已完成' },
  [GSEnum.ROLLED_BACK]: { color: 'error', label: '已回滚' },
};

/** 目标类型标签 */
const TARGET_TYPE_MAP: Record<string, { color: string; label: string }> = {
  RULE: { color: 'blue', label: '规则' },
  DECISION_FLOW: { color: 'purple', label: '决策流' },
};

/** 策略类型标签 */
const STRATEGY_TYPE_MAP: Record<string, { color: string; label: string }> = {
  PERCENTAGE: { color: 'green', label: '百分比' },
  FEATURE: { color: 'orange', label: '特征匹配' },
  WHITELIST: { color: 'cyan', label: '白名单' },
};

/** 默认特征匹配规则模板 */
const DEFAULT_FEATURE_RULES = JSON.stringify(
  [{ field: 'region', operator: 'EQ', value: 'US' }],
  null,
  2,
);

export default function GrayscalePage() {
  const [data, setData] = useState<GrayscaleRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [queryParams, setQueryParams] = useState<GrayscaleQueryParams>({ page: 0, size: 20 });
  const [statusFilter, setStatusFilter] = useState<GrayscaleStatusEnum | undefined>();

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [form] = Form.useForm();

  /** 创建表单中的目标类型和策略类型联动 */
  const [createTargetType, setCreateTargetType] = useState<string>(GrayscaleTargetType.RULE);
  const [createStrategyType, setCreateStrategyType] = useState<string>(GrayscaleStrategyType.PERCENTAGE);

  /** 规则/决策流下拉选项 */
  const [ruleOptions, setRuleOptions] = useState<{ label: string; value: string; version: number }[]>([]);
  const [flowOptions, setFlowOptions] = useState<{ label: string; value: string; version: number }[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);

  /** 版本号下拉选项 */
  const [versionOptions, setVersionOptions] = useState<VersionOption[]>([]);
  const [versionLoading, setVersionLoading] = useState(false);
  /** 当前选中的灰度版本详情 */
  const [selectedVersion, setSelectedVersion] = useState<VersionOption | null>(null);
  /** 当前选中的目标 Key（用于获取当前版本号） */
  const [selectedTargetKey, setSelectedTargetKey] = useState<string>('');

  /** 加载规则/决策流选项 */
  const loadTargetOptions = useCallback(async (type: string) => {
    setOptionsLoading(true);
    try {
      if (type === GrayscaleTargetType.RULE) {
        const rules = await getRulesForSelect();
        setRuleOptions(rules.map((r) => ({ label: `${r.ruleName} (${r.ruleKey})`, value: r.ruleKey, version: r.version ?? 0 })));
      } else {
        const result = await getDecisionFlows({ page: 0, size: 200 });
        setFlowOptions(result.content.map((f) => ({ label: `${f.flowName} (${f.flowKey})`, value: f.flowKey, version: f.version ?? 0 })));
      }
    } catch {
      message.error('加载选项失败');
    } finally {
      setOptionsLoading(false);
    }
  }, []);

  /** 选择目标后加载版本号 */
  const handleTargetSelect = useCallback(async (targetKey: string) => {
    if (!targetKey) {
      setVersionOptions([]);
      setSelectedTargetKey('');
      setSelectedVersion(null);
      return;
    }
    setSelectedTargetKey(targetKey);
    setSelectedVersion(null);
    setVersionLoading(true);
    try {
      const versions = createTargetType === GrayscaleTargetType.RULE
        ? await getRuleVersions(targetKey)
        : await getDecisionFlowVersions(targetKey);
      setVersionOptions(versions);
      // 自动设置当前版本号为表单值（取最新版本号减1作为灰度候选）
      if (versions.length > 0) {
        const latestVersion = versions[0];
        form.setFieldsValue({
          grayscaleVersion: latestVersion.version,
        });
        setSelectedVersion(latestVersion);
      }
    } catch {
      message.error('加载版本列表失败');
      setVersionOptions([]);
    } finally {
      setVersionLoading(false);
    }
  }, [createTargetType, form]);

  /** 打开创建弹窗时加载选项 */
  const openCreateModal = () => {
    setCreateTargetType(GrayscaleTargetType.RULE);
    setCreateStrategyType(GrayscaleStrategyType.PERCENTAGE);
    setCreateModalOpen(true);
    setVersionOptions([]);
    loadTargetOptions(GrayscaleTargetType.RULE);
  };

  const [reportModalOpen, setReportModalOpen] = useState(false);
  const [reportLoading, setReportLoading] = useState(false);
  const [currentReport, setCurrentReport] = useState<GrayscaleReport | null>(null);

  /** 灰度执行日志弹窗 */
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [logLoading, setLogLoading] = useState(false);
  const [logs, setLogs] = useState<CanaryExecutionLog[]>([]);
  const [logRecord, setLogRecord] = useState<GrayscaleRecord | null>(null);

  /** 版本对比弹窗 */
  const [diffModalOpen, setDiffModalOpen] = useState(false);
  const [diffRecord, setDiffRecord] = useState<GrayscaleRecord | null>(null);
  const [diffVersions, setDiffVersions] = useState<{ current?: VersionOption; grayscale?: VersionOption }>({});

  /** 打开版本对比弹窗 */
  const openDiffModal = useCallback(async (record: GrayscaleRecord) => {
    setDiffRecord(record);
    try {
      const versions = record.targetType === 'RULE'
        ? await getRuleVersions(record.targetKey)
        : await getDecisionFlowVersions(record.targetKey);
      const current = versions.find((v) => v.version === record.currentVersion);
      const grayscale = versions.find((v) => v.version === record.grayscaleVersion);
      setDiffVersions({ current, grayscale });
    } catch {
      setDiffVersions({});
    }
    setDiffModalOpen(true);
  }, []);

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

      // 构建请求参数
      const requestParams = {
        ...values,
        targetType: createTargetType,
        strategyType: createStrategyType,
        targetKey:
          createTargetType === GrayscaleTargetType.DECISION_FLOW
            ? values.flowKey
            : values.ruleKey,
        ruleKey: createTargetType === GrayscaleTargetType.RULE ? values.ruleKey : values.flowKey,
      };

      await createGrayscale(requestParams);
      message.success('灰度配置创建成功');
      setCreateModalOpen(false);
      form.resetFields();
      setCreateTargetType(GrayscaleTargetType.RULE);
      setCreateStrategyType(GrayscaleStrategyType.PERCENTAGE);
      setSelectedVersion(null);
      setVersionOptions([]);
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

  /** 查看灰度执行日志 */
  const handleViewLogs = async (record: GrayscaleRecord) => {
    setLogRecord(record);
    setLogModalOpen(true);
    setLogLoading(true);
    try {
      const result = await getCanaryLogs({
        targetType: record.targetType,
        targetKey: record.targetKey || record.ruleKey,
      });
      setLogs(result);
    } catch {
      message.error('加载灰度执行日志失败');
    } finally {
      setLogLoading(false);
    }
  };

  /** JSON 格式校验 */
  const validateJson = (_: unknown, value: string) => {
    if (!value || value.trim() === '') return Promise.resolve();
    try {
      JSON.parse(value);
      return Promise.resolve();
    } catch {
      return Promise.reject(new Error('请输入有效的 JSON 格式'));
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
      title: '目标类型',
      dataIndex: 'targetType',
      width: 100,
      render: (val: string) => {
        const info = TARGET_TYPE_MAP[val] || { color: 'default', label: val };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: '目标 Key',
      dataIndex: 'targetKey',
      ellipsis: true,
      render: (val: string, record) => val || record.ruleKey,
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
      dataIndex: 'grayscalePercentage',
      width: 160,
      render: (val: number) => <Progress percent={val} size="small" />,
    },
    {
      title: '策略类型',
      dataIndex: 'strategyType',
      width: 110,
      render: (val: string) => {
        const info = STRATEGY_TYPE_MAP[val] || { color: 'default', label: val };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
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
      width: 300,
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
          {(record.status === GSEnum.RUNNING ||
            record.status === GSEnum.PAUSED ||
            record.status === GSEnum.COMPLETED) && (
            <Tooltip title="查看对比报告">
              <Button
                type="link"
                size="small"
                icon={<BarChartOutlined />}
                onClick={() => handleViewReport(record.id)}
              />
            </Tooltip>
          )}
          {(record.status === GSEnum.RUNNING || record.status === GSEnum.PAUSED) && (
            <Tooltip title="查看执行日志">
              <Button
                type="link"
                size="small"
                icon={<FileTextOutlined />}
                onClick={() => handleViewLogs(record)}
              />
            </Tooltip>
          )}
          <Tooltip title="查看规则对比">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => openDiffModal(record)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  /** 灰度执行日志表格列定义 */
  const logColumns: ColumnsType<CanaryExecutionLog> = [
    {
      title: '追踪ID',
      dataIndex: 'traceId',
      width: 140,
      ellipsis: true,
    },
    {
      title: '版本',
      dataIndex: 'versionUsed',
      width: 70,
      align: 'center',
    },
    {
      title: '命中灰度',
      dataIndex: 'isCanary',
      width: 90,
      render: (val: boolean) =>
        val ? <Tag color="orange">是</Tag> : <Tag color="blue">否</Tag>,
    },
    {
      title: '决策结果',
      dataIndex: 'decisionResult',
      width: 100,
      ellipsis: true,
    },
    {
      title: '耗时(ms)',
      dataIndex: 'executionTimeMs',
      width: 90,
      align: 'right',
      render: (val: number | null) => (val != null ? val.toFixed(1) : '-'),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (val: string | null) =>
        val ? (
          <Tooltip title={val}>
            <Tag color="error">有错误</Tag>
          </Tooltip>
        ) : (
          <Tag color="success">正常</Tag>
        ),
    },
    {
      title: '执行时间',
      dataIndex: 'createdAt',
      width: 170,
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
              onClick={() => {
                openCreateModal();
              }}
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
          scroll={{ x: 1400 }}
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
          setCreateTargetType(GrayscaleTargetType.RULE);
          setCreateStrategyType(GrayscaleStrategyType.PERCENTAGE);
          setSelectedVersion(null);
          setSelectedTargetKey('');
          setVersionOptions([]);
        }}
        confirmLoading={createLoading}
        okText="创建"
        cancelText="取消"
        width={900}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {/* 目标类型选择 */}
          <Form.Item label="目标类型" required>
            <Select
              value={createTargetType}
              onChange={(val) => {
                setCreateTargetType(val);
                form.setFieldsValue({ ruleKey: undefined, flowKey: undefined });
                setVersionOptions([]);
                loadTargetOptions(val);
              }}
              options={[
                { value: GrayscaleTargetType.RULE, label: '规则' },
                { value: GrayscaleTargetType.DECISION_FLOW, label: '决策流' },
              ]}
            />
          </Form.Item>

          {/* 根据 targetType 切换输入 */}
          {createTargetType === GrayscaleTargetType.RULE ? (
            <Form.Item
              name="ruleKey"
              label="规则"
              rules={[{ required: true, message: '请选择规则' }]}
            >
              <Select
                showSearch
                loading={optionsLoading}
                placeholder="请搜索选择规则"
                optionFilterProp="label"
                onChange={(val) => handleTargetSelect(val)}
                options={ruleOptions}
              />
            </Form.Item>
          ) : (
            <Form.Item
              name="flowKey"
              label="决策流"
              rules={[{ required: true, message: '请选择决策流' }]}
            >
              <Select
                showSearch
                loading={optionsLoading}
                placeholder="请搜索选择决策流"
                optionFilterProp="label"
                onChange={(val) => handleTargetSelect(val)}
                options={flowOptions}
              />
            </Form.Item>
          )}

          {/* 分流策略选择 */}
          <Form.Item label="分流策略" required>
            <Select
              value={createStrategyType}
              onChange={(val) => {
                setCreateStrategyType(val);
                // 切换策略时清除不再需要的字段
                if (val !== GrayscaleStrategyType.FEATURE) {
                  form.setFieldsValue({ featureRules: undefined });
                }
                if (val !== GrayscaleStrategyType.WHITELIST) {
                  form.setFieldsValue({ whitelistIds: undefined });
                }
              }}
              options={[
                { value: GrayscaleStrategyType.PERCENTAGE, label: '百分比' },
                { value: GrayscaleStrategyType.FEATURE, label: '特征匹配' },
                { value: GrayscaleStrategyType.WHITELIST, label: '白名单' },
              ]}
            />
          </Form.Item>

          {/* 百分比策略：显示灰度百分比输入 */}
          {createStrategyType === GrayscaleStrategyType.PERCENTAGE && (
            <Form.Item
              name="percentage"
              label="灰度百分比"
              rules={[{ required: true, message: '请输入灰度百分比' }]}
              extra="灰度流量占总流量的比例（0-100）"
            >
              <InputNumber
                min={0}
                max={100}
                style={{ width: '100%' }}
                placeholder="请输入灰度百分比"
                suffix="%"
              />
            </Form.Item>
          )}

          {/* 特征匹配策略：显示 JSON 编辑器 */}
          {createStrategyType === GrayscaleStrategyType.FEATURE && (
            <Form.Item
              name="featureRules"
              label="特征匹配规则"
              rules={[
                { required: true, message: '请输入特征匹配规则' },
                { validator: validateJson },
              ]}
              extra={
                <span style={{ fontSize: 12, color: '#999' }}>
                  JSON 格式，例如: {DEFAULT_FEATURE_RULES}
                </span>
              }
            >
              <Input.TextArea
                rows={6}
                placeholder={DEFAULT_FEATURE_RULES}
                style={{ fontFamily: 'monospace' }}
              />
            </Form.Item>
          )}

          {/* 白名单策略：显示用户ID输入 */}
          {createStrategyType === GrayscaleStrategyType.WHITELIST && (
            <Form.Item
              name="whitelistIds"
              label="白名单用户ID"
              rules={[{ required: true, message: '请输入白名单用户ID' }]}
              extra="多个用户ID用英文逗号分隔"
            >
              <Select
                mode="tags"
                placeholder="输入用户ID后按回车添加"
                tokenSeparators={[',']}
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}

          <Form.Item
            name="grayscaleVersion"
            label="灰度版本号"
            rules={[{ required: true, message: '请选择灰度版本号' }]}
            extra={versionOptions.length > 0 ? `共 ${versionOptions.length} 个版本可选` : '请先选择目标'}
          >
            <Select
              loading={versionLoading}
              placeholder="请选择灰度版本号"
              showSearch
              optionFilterProp="label"
              onChange={(val: number) => {
                const v = versionOptions.find((opt) => opt.version === val);
                setSelectedVersion(v ?? null);
              }}
              options={versionOptions.map((v) => {
                const statusLabel = v.status ? `[${v.status}]` : '';
                const reason = v.changeReason ? ` - ${v.changeReason}` : '';
                const rollback = v.isRollback && v.rollbackFromVersion ? ` (从 v${v.rollbackFromVersion} 回滚)` : '';
                const date = v.changedAt ? ` (${v.changedAt.slice(0, 16).replace('T', ' ')})` : '';
                return {
                  value: v.version,
                  label: `v${v.version} ${statusLabel}${reason}${rollback}${date}`,
                };
              })}
            />
          </Form.Item>

          {/* 版本对比面板 */}
          {selectedVersion && (() => {
            const currentVerNum = createTargetType === GrayscaleTargetType.RULE
              ? ruleOptions.find((r) => r.value === selectedTargetKey)?.version
              : flowOptions.find((f) => f.value === selectedTargetKey)?.version;
            const currentVersion = currentVerNum
              ? versionOptions.find((v) => v.version === currentVerNum)
              : undefined;
            const isSameVersion = currentVersion && selectedVersion.version === currentVersion.version;

            // 获取对比文本
            const formatContent = (ver: VersionOption | undefined) => {
              if (!ver) return '';
              if (ver.groovyScript) return ver.groovyScript;
              if (ver.flowGraph) {
                try { return JSON.stringify(JSON.parse(ver.flowGraph), null, 2); }
                catch { return ver.flowGraph; }
              }
              return '';
            };
            const oldText = formatContent(currentVersion);
            const newText = formatContent(selectedVersion);
            const isRule = createTargetType === GrayscaleTargetType.RULE;

            return (
              <Card
                size="small"
                title="版本对比"
                style={{ marginBottom: 16, background: '#fafafa' }}
              >
                {isSameVersion && (
                  <div style={{ color: '#faad14', marginBottom: 8, fontSize: 12 }}>
                    注意：灰度版本与当前版本相同，请选择不同的版本进行灰度
                  </div>
                )}

                {/* 版本元信息 */}
                <Row gutter={16} style={{ marginBottom: 12 }}>
                  <Col span={12}>
                    <Descriptions size="small" column={1} bordered>
                      <Descriptions.Item label="当前版本">
                        {currentVersion ? (
                          <Space>
                            <span>v{currentVersion.version}</span>
                            <Tag color={currentVersion.status === 'ACTIVE' ? 'green' : 'default'}>
                              {currentVersion.status || '-'}
                            </Tag>
                            <span style={{ color: '#999' }}>{currentVersion.changedBy || ''}</span>
                          </Space>
                        ) : '-'}
                      </Descriptions.Item>
                      {currentVersion?.changeReason && (
                        <Descriptions.Item label="变更原因">{currentVersion.changeReason}</Descriptions.Item>
                      )}
                    </Descriptions>
                  </Col>
                  <Col span={12}>
                    <Descriptions size="small" column={1} bordered>
                      <Descriptions.Item label="灰度版本">
                        <Space>
                          <span>v{selectedVersion.version}</span>
                          <Tag color={selectedVersion.status === 'ACTIVE' ? 'green' : selectedVersion.status === 'CANARY' ? 'orange' : 'default'}>
                            {selectedVersion.status || '-'}
                          </Tag>
                          <span style={{ color: '#999' }}>{selectedVersion.changedBy || ''}</span>
                        </Space>
                      </Descriptions.Item>
                      {selectedVersion.changeReason && (
                        <Descriptions.Item label="变更原因">{selectedVersion.changeReason}</Descriptions.Item>
                      )}
                      {selectedVersion.isRollback && selectedVersion.rollbackFromVersion && (
                        <Descriptions.Item label="回滚来源">
                          从 v{selectedVersion.rollbackFromVersion} 回滚
                        </Descriptions.Item>
                      )}
                    </Descriptions>
                  </Col>
                </Row>

                {/* 对比内容：规则渲染 + Groovy 代码 / 决策流可视化 + JSON */}
                {(oldText || newText) && (
                  <Tabs
                    size="small"
                    items={[
                      ...(isRule ? [{
                        key: 'rendered',
                        label: '规则逻辑对比',
                        children: (
                          <RuleRenderedDiff
                            oldScript={currentVersion?.groovyScript}
                            newScript={selectedVersion.groovyScript}
                            oldTitle={currentVersion ? `当前版本 v${currentVersion.version}` : '当前版本'}
                            newTitle={`灰度版本 v${selectedVersion.version}`}
                          />
                        ),
                      }] : [{
                        key: 'visual',
                        label: '可视化对比',
                        children: (
                          <FlowGraphDiff
                            oldFlowGraph={currentVersion?.flowGraph ?? ''}
                            newFlowGraph={selectedVersion.flowGraph ?? ''}
                            oldTitle={currentVersion ? `当前版本 v${currentVersion.version}` : '当前版本'}
                            newTitle={`灰度版本 v${selectedVersion.version}`}
                          />
                        ),
                      }]),
                      {
                        key: 'code',
                        label: isRule ? 'Groovy 代码对比' : 'JSON 对比',
                        children: (
                          <DiffViewer
                            oldText={oldText}
                            newText={newText}
                            oldTitle={currentVersion ? `当前版本 v${currentVersion.version}` : '当前版本'}
                            newTitle={`灰度版本 v${selectedVersion.version}`}
                            groovyHighlight={isRule}
                          />
                        ),
                      },
                    ]}
                  />
                )}
                {!oldText && !newText && (
                  <div style={{ color: '#999', textAlign: 'center', padding: 16 }}>无脚本内容可对比</div>
                )}
              </Card>
            );
          })()}

          <Form.Item
            name="description"
            label="灰度描述"
          >
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
              <Descriptions.Item label="规则 Key">
                {currentReport.ruleKey}
              </Descriptions.Item>
              <Descriptions.Item label="灰度比例">
                {currentReport.grayscalePercentage}%
              </Descriptions.Item>
              <Descriptions.Item label="当前版本">
                v{currentReport.currentVersion}
              </Descriptions.Item>
              <Descriptions.Item label="灰度版本">
                v{currentReport.grayscaleVersion}
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
                      <Statistic
                        title="执行次数"
                        value={currentReport.currentVersionMetrics.executionCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="命中次数"
                        value={currentReport.currentVersionMetrics.hitCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="命中率"
                        value={currentReport.currentVersionMetrics.hitRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="平均耗时"
                        value={currentReport.currentVersionMetrics.avgExecutionTimeMs ?? 0}
                        suffix="ms"
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="错误率"
                        value={currentReport.currentVersionMetrics.errorRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="错误数"
                        value={currentReport.currentVersionMetrics.errorCount ?? 0}
                      />
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
                      <Statistic
                        title="执行次数"
                        value={currentReport.grayscaleVersionMetrics.executionCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="命中次数"
                        value={currentReport.grayscaleVersionMetrics.hitCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="命中率"
                        value={currentReport.grayscaleVersionMetrics.hitRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="平均耗时"
                        value={currentReport.grayscaleVersionMetrics.avgExecutionTimeMs ?? 0}
                        suffix="ms"
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="错误率"
                        value={currentReport.grayscaleVersionMetrics.errorRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title="错误数"
                        value={currentReport.grayscaleVersionMetrics.errorCount ?? 0}
                      />
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

      {/* 灰度执行日志弹窗 */}
      <Modal
        title={`灰度执行日志 - ${logRecord?.targetKey || logRecord?.ruleKey || ''}`}
        open={logModalOpen}
        onCancel={() => {
          setLogModalOpen(false);
          setLogs([]);
          setLogRecord(null);
        }}
        footer={null}
        width={960}
      >
        {logLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>加载中...</div>
        ) : logs.length > 0 ? (
          <Table
            rowKey="id"
            columns={logColumns}
            dataSource={logs}
            size="small"
            pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
            scroll={{ x: 800 }}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
            暂无执行日志
          </div>
        )}
      </Modal>

      {/* 版本对比弹窗 */}
      <Modal
        title={`版本对比 - ${diffRecord?.targetKey || ''} (当前 v${diffRecord?.currentVersion ?? '-'} vs 灰度 v${diffRecord?.grayscaleVersion ?? '-'})`}
        open={diffModalOpen}
        onCancel={() => {
          setDiffModalOpen(false);
          setDiffRecord(null);
          setDiffVersions({});
        }}
        footer={null}
        width={960}
      >
        {(() => {
          const { current: cv, grayscale: gv } = diffVersions;
          const isRule = diffRecord?.targetType === 'RULE';
          const formatContent = (ver: VersionOption | undefined) => {
            if (!ver) return '';
            if (ver.groovyScript) return ver.groovyScript;
            if (ver.flowGraph) {
              try { return JSON.stringify(JSON.parse(ver.flowGraph), null, 2); }
              catch { return ver.flowGraph; }
            }
            return '';
          };

          if (!cv && !gv) {
            return <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>未找到版本信息</div>;
          }

          return (
            <Tabs
              items={[
                ...(isRule ? [{
                  key: 'rendered',
                  label: '规则逻辑对比',
                  children: (
                    <RuleRenderedDiff
                      oldScript={cv?.groovyScript}
                      newScript={gv?.groovyScript}
                      oldTitle={cv ? `当前版本 v${cv.version}` : '当前版本'}
                      newTitle={gv ? `灰度版本 v${gv.version}` : '灰度版本'}
                    />
                  ),
                }] : [{
                  key: 'visual',
                  label: '可视化对比',
                  children: (
                    <FlowGraphDiff
                      oldFlowGraph={cv?.flowGraph ?? ''}
                      newFlowGraph={gv?.flowGraph ?? ''}
                      oldTitle={cv ? `当前版本 v${cv.version}` : '当前版本'}
                      newTitle={gv ? `灰度版本 v${gv.version}` : '灰度版本'}
                    />
                  ),
                }]),
                {
                  key: 'code',
                  label: isRule ? 'Groovy 代码对比' : 'JSON 对比',
                  children: (
                    <DiffViewer
                      oldText={formatContent(cv)}
                      newText={formatContent(gv)}
                      oldTitle={cv ? `当前版本 v${cv.version}` : '当前版本'}
                      newTitle={gv ? `灰度版本 v${gv.version}` : '灰度版本'}
                      groovyHighlight={isRule}
                    />
                  ),
                },
              ]}
            />
          );
        })()}
      </Modal>
    </>
  );
}
