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
import { useTranslation } from 'react-i18next';
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

/** 状态对应的颜色（label 通过 i18n 翻译） */
const STATUS_MAP: Record<GrayscaleStatusEnum, { color: string; labelKey: string }> = {
  [GSEnum.DRAFT]: { color: 'default', labelKey: 'grayscale.status.DRAFT' },
  [GSEnum.RUNNING]: { color: 'processing', labelKey: 'grayscale.status.RUNNING' },
  [GSEnum.PAUSED]: { color: 'warning', labelKey: 'grayscale.status.PAUSED' },
  [GSEnum.COMPLETED]: { color: 'success', labelKey: 'grayscale.status.COMPLETED' },
  [GSEnum.ROLLED_BACK]: { color: 'error', labelKey: 'grayscale.status.ROLLED_BACK' },
};

/** 目标类型标签 */
const TARGET_TYPE_MAP: Record<string, { color: string; labelKey: string }> = {
  RULE: { color: 'blue', labelKey: 'grayscale.targetTypeLabel.RULE' },
  DECISION_FLOW: { color: 'purple', labelKey: 'grayscale.targetTypeLabel.DECISION_FLOW' },
};

/** 策略类型标签 */
const STRATEGY_TYPE_MAP: Record<string, { color: string; labelKey: string }> = {
  PERCENTAGE: { color: 'green', labelKey: 'grayscale.strategyTypeLabel.PERCENTAGE' },
  FEATURE: { color: 'orange', labelKey: 'grayscale.strategyTypeLabel.FEATURE' },
  WHITELIST: { color: 'cyan', labelKey: 'grayscale.strategyTypeLabel.WHITELIST' },
};

/** 默认特征匹配规则模板 */
const DEFAULT_FEATURE_RULES = JSON.stringify(
  [{ field: 'region', operator: 'EQ', value: 'US' }],
  null,
  2,
);

export default function GrayscalePage() {
  const { t } = useTranslation();
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
      message.error(t('grayscale.loadOptionsFailed'));
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
      message.error(t('grayscale.loadVersionsFailed'));
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
      message.error(t('grayscale.loadListFailed'));
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
      message.success(t('grayscale.createSuccess'));
      setCreateModalOpen(false);
      form.resetFields();
      setCreateTargetType(GrayscaleTargetType.RULE);
      setCreateStrategyType(GrayscaleStrategyType.PERCENTAGE);
      setSelectedVersion(null);
      setVersionOptions([]);
      loadData(queryParams);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        message.error(t('grayscale.createFailed'));
      }
    } finally {
      setCreateLoading(false);
    }
  };

  /** 操作：启动灰度 */
  const handleStart = async (id: number) => {
    try {
      await startGrayscale(id);
      message.success(t('grayscale.startSuccess'));
      loadData(queryParams);
    } catch {
      message.error(t('grayscale.startFailed'));
    }
  };

  /** 操作：暂停灰度 */
  const handlePause = async (id: number) => {
    try {
      await pauseGrayscale(id);
      message.success(t('grayscale.pauseSuccess'));
      loadData(queryParams);
    } catch {
      message.error(t('grayscale.pauseFailed'));
    }
  };

  /** 操作：完成灰度（全量发布） */
  const handleComplete = async (id: number) => {
    try {
      await completeGrayscale(id);
      message.success(t('grayscale.completeSuccess'));
      loadData(queryParams);
    } catch {
      message.error(t('grayscale.completeFailed'));
    }
  };

  /** 操作：回滚灰度 */
  const handleRollback = async (id: number) => {
    try {
      await rollbackGrayscale(id);
      message.success(t('grayscale.rollbackSuccess'));
      loadData(queryParams);
    } catch {
      message.error(t('grayscale.rollbackFailed'));
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
      message.error(t('grayscale.loadReportFailed'));
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
      message.error(t('grayscale.loadLogsFailed'));
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
      return Promise.reject(new Error(t('common.error')));
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
      title: t('grayscale.targetType'),
      dataIndex: 'targetType',
      width: 100,
      render: (val: string) => {
        const info = TARGET_TYPE_MAP[val] || { color: 'default', labelKey: val };
        return <Tag color={info.color}>{t(info.labelKey)}</Tag>;
      },
    },
    {
      title: t('grayscale.targetKey'),
      dataIndex: 'targetKey',
      ellipsis: true,
      render: (val: string, record) => val || record.ruleKey,
    },
    {
      title: t('grayscale.currentVersion'),
      dataIndex: 'currentVersion',
      width: 100,
      align: 'center',
    },
    {
      title: t('grayscale.grayscaleVersion'),
      dataIndex: 'grayscaleVersion',
      width: 100,
      align: 'center',
    },
    {
      title: t('grayscale.grayscalePercentage'),
      dataIndex: 'grayscalePercentage',
      width: 160,
      render: (val: number) => <Progress percent={val} size="small" />,
    },
    {
      title: t('grayscale.strategyType'),
      dataIndex: 'strategyType',
      width: 110,
      render: (val: string) => {
        const info = STRATEGY_TYPE_MAP[val] || { color: 'default', labelKey: val };
        return <Tag color={info.color}>{t(info.labelKey)}</Tag>;
      },
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 100,
      render: (status: GrayscaleStatusEnum) => {
        const info = STATUS_MAP[status];
        return <Tag color={info.color}>{t(info.labelKey)}</Tag>;
      },
    },
    {
      title: t('common.createdBy'),
      dataIndex: 'createdBy',
      width: 100,
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      width: 170,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 300,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Access permission="api:grayscale:manage">
            {record.status === GSEnum.DRAFT && (
              <Tooltip title={t('grayscale.startGrayscale')}>
                <Button
                  type="link"
                  size="small"
                  icon={<PlayCircleOutlined />}
                  onClick={() => handleStart(record.id)}
                />
              </Tooltip>
            )}
            {record.status === GSEnum.RUNNING && (
              <Tooltip title={t('grayscale.pauseGrayscale')}>
                <Button
                  type="link"
                  size="small"
                  icon={<PauseCircleOutlined />}
                  onClick={() => handlePause(record.id)}
                />
              </Tooltip>
            )}
            {record.status === GSEnum.PAUSED && (
              <Tooltip title={t('grayscale.resumeGrayscale')}>
                <Button
                  type="link"
                  size="small"
                  icon={<PlayCircleOutlined />}
                  onClick={() => handleStart(record.id)}
                />
              </Tooltip>
            )}
            {(record.status === GSEnum.RUNNING || record.status === GSEnum.PAUSED) && (
              <Popconfirm
                title={t('grayscale.confirmComplete')}
                onConfirm={() => handleComplete(record.id)}
                okText={t('common.confirm')}
                cancelText={t('common.cancel')}
              >
                <Tooltip title={t('grayscale.completeGrayscale')}>
                  <Button type="link" size="small" icon={<CheckCircleOutlined />} />
                </Tooltip>
              </Popconfirm>
            )}
            {(record.status === GSEnum.RUNNING || record.status === GSEnum.PAUSED) && (
              <Popconfirm
                title={t('grayscale.confirmRollback')}
                onConfirm={() => handleRollback(record.id)}
                okText={t('common.confirm')}
                cancelText={t('common.cancel')}
              >
                <Tooltip title={t('grayscale.rollbackGrayscale')}>
                  <Button type="link" size="small" danger icon={<RollbackOutlined />} />
                </Tooltip>
              </Popconfirm>
            )}
          </Access>
          {(record.status === GSEnum.RUNNING ||
            record.status === GSEnum.PAUSED ||
            record.status === GSEnum.COMPLETED) && (
            <Tooltip title={t('grayscale.viewReport')}>
              <Button
                type="link"
                size="small"
                icon={<BarChartOutlined />}
                onClick={() => handleViewReport(record.id)}
              />
            </Tooltip>
          )}
          {(record.status === GSEnum.RUNNING || record.status === GSEnum.PAUSED) && (
            <Tooltip title={t('grayscale.viewLogs')}>
              <Button
                type="link"
                size="small"
                icon={<FileTextOutlined />}
                onClick={() => handleViewLogs(record)}
              />
            </Tooltip>
          )}
          <Tooltip title={t('grayscale.viewDiff')}>
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
      title: t('grayscale.traceId'),
      dataIndex: 'traceId',
      width: 140,
      ellipsis: true,
    },
    {
      title: t('grayscale.versionField'),
      dataIndex: 'versionUsed',
      width: 70,
      align: 'center',
    },
    {
      title: t('grayscale.hitCanary'),
      dataIndex: 'isCanary',
      width: 90,
      render: (val: boolean) =>
        val ? <Tag color="orange">{t('common.yes')}</Tag> : <Tag color="blue">{t('common.no')}</Tag>,
    },
    {
      title: t('grayscale.decisionResult'),
      dataIndex: 'decisionResult',
      width: 100,
      ellipsis: true,
    },
    {
      title: t('grayscale.executionTimeMs'),
      dataIndex: 'executionTimeMs',
      width: 90,
      align: 'right',
      render: (val: number | null) => (val != null ? val.toFixed(1) : '-'),
    },
    {
      title: t('grayscale.errorMessage'),
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (val: string | null) =>
        val ? (
          <Tooltip title={val}>
            <Tag color="error">{t('grayscale.hasError')}</Tag>
          </Tooltip>
        ) : (
          <Tag color="success">{t('grayscale.normal')}</Tag>
        ),
    },
    {
      title: t('grayscale.executionTime'),
      dataIndex: 'createdAt',
      width: 170,
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('grayscale.pageTitle') }]} />

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Space>
            <Select
              placeholder={t('grayscale.statusFilter')}
              allowClear
              style={{ width: 150 }}
              value={statusFilter}
              onChange={handleStatusFilter}
              options={Object.entries(STATUS_MAP).map(([value, { labelKey }]) => ({
                value,
                label: t(labelKey),
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
              {t('grayscale.createGrayscale')}
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
            showTotal: (total) => t('common.total', { count: total }),
            onChange: handlePageChange,
          }}
        />
      </Card>

      {/* 创建灰度弹窗 */}
      <Modal
        title={t('grayscale.createTitle')}
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
        okText={t('common.create')}
        cancelText={t('common.cancel')}
        width={900}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {/* 目标类型选择 */}
          <Form.Item label={t('grayscale.targetType')} required>
            <Select
              value={createTargetType}
              onChange={(val) => {
                setCreateTargetType(val);
                form.setFieldsValue({ ruleKey: undefined, flowKey: undefined });
                setVersionOptions([]);
                loadTargetOptions(val);
              }}
              options={[
                { value: GrayscaleTargetType.RULE, label: t('grayscale.rule') },
                { value: GrayscaleTargetType.DECISION_FLOW, label: t('grayscale.decisionFlow') },
              ]}
            />
          </Form.Item>

          {/* 根据 targetType 切换输入 */}
          {createTargetType === GrayscaleTargetType.RULE ? (
            <Form.Item
              name="ruleKey"
              label={t('grayscale.rule')}
              rules={[{ required: true, message: t('grayscale.ruleRequired') }]}
            >
              <Select
                showSearch
                loading={optionsLoading}
                placeholder={t('grayscale.ruleSelectPlaceholder')}
                optionFilterProp="label"
                onChange={(val) => handleTargetSelect(val)}
                options={ruleOptions}
              />
            </Form.Item>
          ) : (
            <Form.Item
              name="flowKey"
              label={t('grayscale.decisionFlow')}
              rules={[{ required: true, message: t('grayscale.flowRequired') }]}
            >
              <Select
                showSearch
                loading={optionsLoading}
                placeholder={t('grayscale.flowSelectPlaceholder')}
                optionFilterProp="label"
                onChange={(val) => handleTargetSelect(val)}
                options={flowOptions}
              />
            </Form.Item>
          )}

          {/* 分流策略选择 */}
          <Form.Item label={t('grayscale.strategy.title')} required>
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
                { value: GrayscaleStrategyType.PERCENTAGE, label: t('grayscale.strategy.percentage') },
                { value: GrayscaleStrategyType.FEATURE, label: t('grayscale.strategy.feature') },
                { value: GrayscaleStrategyType.WHITELIST, label: t('grayscale.strategy.whitelist') },
              ]}
            />
          </Form.Item>

          {/* 百分比策略：显示灰度百分比输入 */}
          {createStrategyType === GrayscaleStrategyType.PERCENTAGE && (
            <Form.Item
              name="percentage"
              label={t('grayscale.percentageLabel')}
              rules={[{ required: true, message: t('grayscale.percentageRequired') }]}
              extra={t('grayscale.percentageExtra')}
            >
              <InputNumber
                min={0}
                max={100}
                style={{ width: '100%' }}
                placeholder={t('grayscale.percentagePlaceholder')}
                suffix="%"
              />
            </Form.Item>
          )}

          {/* 特征匹配策略：显示 JSON 编辑器 */}
          {createStrategyType === GrayscaleStrategyType.FEATURE && (
            <Form.Item
              name="featureRules"
              label={t('grayscale.featureRulesLabel')}
              rules={[
                { required: true, message: t('grayscale.featureRulesPlaceholder') },
                { validator: validateJson },
              ]}
              extra={
                <span style={{ fontSize: 12, color: '#999' }}>
                  {t('grayscale.featureRulesExtra', { example: DEFAULT_FEATURE_RULES })}
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
              label={t('grayscale.whitelistLabel')}
              rules={[{ required: true, message: t('grayscale.whitelistRequired') }]}
              extra={t('grayscale.whitelistExtra')}
            >
              <Select
                mode="tags"
                placeholder={t('grayscale.whitelistPlaceholder')}
                tokenSeparators={[',']}
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}

          <Form.Item
            name="grayscaleVersion"
            label={t('grayscale.grayscaleVersionLabel')}
            rules={[{ required: true, message: t('grayscale.grayscaleVersionRequired') }]}
            extra={versionOptions.length > 0 ? t('grayscale.grayscaleVersionCount', { count: versionOptions.length }) : t('grayscale.selectTargetFirst')}
          >
            <Select
              loading={versionLoading}
              placeholder={t('grayscale.grayscaleVersionRequired')}
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
                title={t('grayscale.versionComparison')}
                style={{ marginBottom: 16, background: '#fafafa' }}
              >
                {isSameVersion && (
                  <div style={{ color: '#faad14', marginBottom: 8, fontSize: 12 }}>
                    {t('grayscale.sameVersionWarning')}
                  </div>
                )}

                {/* 版本元信息 */}
                <Row gutter={16} style={{ marginBottom: 12 }}>
                  <Col span={12}>
                    <Descriptions size="small" column={1} bordered>
                      <Descriptions.Item label={t('grayscale.currentVersion')}>
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
                        <Descriptions.Item label={t('grayscale.changeReason')}>{currentVersion.changeReason}</Descriptions.Item>
                      )}
                    </Descriptions>
                  </Col>
                  <Col span={12}>
                    <Descriptions size="small" column={1} bordered>
                      <Descriptions.Item label={t('grayscale.grayscaleVersion')}>
                        <Space>
                          <span>v{selectedVersion.version}</span>
                          <Tag color={selectedVersion.status === 'ACTIVE' ? 'green' : selectedVersion.status === 'CANARY' ? 'orange' : 'default'}>
                            {selectedVersion.status || '-'}
                          </Tag>
                          <span style={{ color: '#999' }}>{selectedVersion.changedBy || ''}</span>
                        </Space>
                      </Descriptions.Item>
                      {selectedVersion.changeReason && (
                        <Descriptions.Item label={t('grayscale.changeReason')}>{selectedVersion.changeReason}</Descriptions.Item>
                      )}
                      {selectedVersion.isRollback && selectedVersion.rollbackFromVersion && (
                        <Descriptions.Item label={t('grayscale.rollbackSource')}>
                          {t('grayscale.rollbackFrom', { version: selectedVersion.rollbackFromVersion })}
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
                        label: t('grayscale.ruleLogicComparison'),
                        children: (
                          <RuleRenderedDiff
                            oldScript={currentVersion?.groovyScript}
                            newScript={selectedVersion.groovyScript}
                            oldTitle={currentVersion ? `${t('grayscale.currentVersionTitle')} v${currentVersion.version}` : t('grayscale.currentVersionTitle')}
                            newTitle={`${t('grayscale.grayscaleVersionTitle')} v${selectedVersion.version}`}
                          />
                        ),
                      }] : [{
                        key: 'visual',
                        label: t('grayscale.visualComparison'),
                        children: (
                          <FlowGraphDiff
                            oldFlowGraph={currentVersion?.flowGraph ?? ''}
                            newFlowGraph={selectedVersion.flowGraph ?? ''}
                            oldTitle={currentVersion ? `${t('grayscale.currentVersionTitle')} v${currentVersion.version}` : t('grayscale.currentVersionTitle')}
                            newTitle={`${t('grayscale.grayscaleVersionTitle')} v${selectedVersion.version}`}
                          />
                        ),
                      }]),
                      {
                        key: 'code',
                        label: isRule ? t('grayscale.groovyCodeComparison') : t('grayscale.jsonComparison'),
                        children: (
                          <DiffViewer
                            oldText={oldText}
                            newText={newText}
                            oldTitle={currentVersion ? `${t('grayscale.currentVersionTitle')} v${currentVersion.version}` : t('grayscale.currentVersionTitle')}
                            newTitle={`${t('grayscale.grayscaleVersionTitle')} v${selectedVersion.version}`}
                            groovyHighlight={isRule}
                          />
                        ),
                      },
                    ]}
                  />
                )}
                {!oldText && !newText && (
                  <div style={{ color: '#999', textAlign: 'center', padding: 16 }}>{t('grayscale.noContentToCompare')}</div>
                )}
              </Card>
            );
          })()}

          <Form.Item
            name="description"
            label={t('grayscale.grayscaleDescription')}
          >
            <Input.TextArea rows={3} placeholder={t('grayscale.descriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 灰度对比报告弹窗 */}
      <Modal
        title={t('grayscale.reportTitle')}
        open={reportModalOpen}
        onCancel={() => {
          setReportModalOpen(false);
          setCurrentReport(null);
        }}
        footer={null}
        width={720}
      >
        {reportLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>{t('app.loading')}</div>
        ) : currentReport ? (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 24 }}>
              <Descriptions.Item label={t('grayscale.ruleKey')}>
                {currentReport.ruleKey}
              </Descriptions.Item>
              <Descriptions.Item label={t('grayscale.grayscalePercentage')}>
                {currentReport.grayscalePercentage}%
              </Descriptions.Item>
              <Descriptions.Item label={t('grayscale.currentVersion')}>
                v{currentReport.currentVersion}
              </Descriptions.Item>
              <Descriptions.Item label={t('grayscale.grayscaleVersion')}>
                v{currentReport.grayscaleVersion}
              </Descriptions.Item>
            </Descriptions>

            <Row gutter={24}>
              {/* 当前版本指标 */}
              <Col span={12}>
                <Card
                  title={t('grayscale.currentVersionTitle')}
                  size="small"
                  style={{ textAlign: 'center' }}
                  headStyle={{ background: '#e6f7ff' }}
                >
                  <Row gutter={[16, 16]}>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.executionCount')}
                        value={currentReport.currentVersionMetrics.executionCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.hitCount')}
                        value={currentReport.currentVersionMetrics.hitCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.hitRate')}
                        value={currentReport.currentVersionMetrics.hitRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.avgTime')}
                        value={currentReport.currentVersionMetrics.avgExecutionTimeMs ?? 0}
                        suffix="ms"
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.errorRate')}
                        value={currentReport.currentVersionMetrics.errorRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.errorCount')}
                        value={currentReport.currentVersionMetrics.errorCount ?? 0}
                      />
                    </Col>
                  </Row>
                </Card>
              </Col>

              {/* 灰度版本指标 */}
              <Col span={12}>
                <Card
                  title={t('grayscale.grayscaleVersionTitle')}
                  size="small"
                  style={{ textAlign: 'center' }}
                  headStyle={{ background: '#fff7e6' }}
                >
                  <Row gutter={[16, 16]}>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.executionCount')}
                        value={currentReport.grayscaleVersionMetrics.executionCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.hitCount')}
                        value={currentReport.grayscaleVersionMetrics.hitCount ?? 0}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.hitRate')}
                        value={currentReport.grayscaleVersionMetrics.hitRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.avgTime')}
                        value={currentReport.grayscaleVersionMetrics.avgExecutionTimeMs ?? 0}
                        suffix="ms"
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.errorRate')}
                        value={currentReport.grayscaleVersionMetrics.errorRate ?? 0}
                        suffix="%"
                        precision={2}
                      />
                    </Col>
                    <Col span={12}>
                      <Statistic
                        title={t('grayscale.errorCount')}
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
            {t('grayscale.noReportData')}
          </div>
        )}
      </Modal>

      {/* 灰度执行日志弹窗 */}
      <Modal
        title={`${t('grayscale.executionLogTitle')} - ${logRecord?.targetKey || logRecord?.ruleKey || ''}`}
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
          <div style={{ textAlign: 'center', padding: '40px 0' }}>{t('app.loading')}</div>
        ) : logs.length > 0 ? (
          <Table
            rowKey="id"
            columns={logColumns}
            dataSource={logs}
            size="small"
            pagination={{ pageSize: 10, showTotal: (total) => t('common.total', { count: total }) }}
            scroll={{ x: 800 }}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
            {t('grayscale.noLogs')}
          </div>
        )}
      </Modal>

      {/* 版本对比弹窗 */}
      <Modal
        title={`${t('grayscale.versionComparisonTitle')} - ${diffRecord?.targetKey || ''} (${t('grayscale.currentVersionTitle')} v${diffRecord?.currentVersion ?? '-'} vs ${t('grayscale.grayscaleVersionTitle')} v${diffRecord?.grayscaleVersion ?? '-'})`}
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
            return <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>{t('grayscale.noVersionInfo')}</div>;
          }

          return (
            <Tabs
              items={[
                ...(isRule ? [{
                  key: 'rendered',
                  label: t('grayscale.ruleLogicComparison'),
                  children: (
                    <RuleRenderedDiff
                      oldScript={cv?.groovyScript}
                      newScript={gv?.groovyScript}
                      oldTitle={cv ? `${t('grayscale.currentVersionTitle')} v${cv.version}` : t('grayscale.currentVersionTitle')}
                      newTitle={gv ? `${t('grayscale.grayscaleVersionTitle')} v${gv.version}` : t('grayscale.grayscaleVersionTitle')}
                    />
                  ),
                }] : [{
                  key: 'visual',
                  label: t('grayscale.visualComparison'),
                  children: (
                    <FlowGraphDiff
                      oldFlowGraph={cv?.flowGraph ?? ''}
                      newFlowGraph={gv?.flowGraph ?? ''}
                      oldTitle={cv ? `${t('grayscale.currentVersionTitle')} v${cv.version}` : t('grayscale.currentVersionTitle')}
                      newTitle={gv ? `${t('grayscale.grayscaleVersionTitle')} v${gv.version}` : t('grayscale.grayscaleVersionTitle')}
                    />
                  ),
                }]),
                {
                  key: 'code',
                  label: isRule ? t('grayscale.groovyCodeComparison') : t('grayscale.jsonComparison'),
                  children: (
                    <DiffViewer
                      oldText={formatContent(cv)}
                      newText={formatContent(gv)}
                      oldTitle={cv ? `${t('grayscale.currentVersionTitle')} v${cv.version}` : t('grayscale.currentVersionTitle')}
                      newTitle={gv ? `${t('grayscale.grayscaleVersionTitle')} v${gv.version}` : t('grayscale.grayscaleVersionTitle')}
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
