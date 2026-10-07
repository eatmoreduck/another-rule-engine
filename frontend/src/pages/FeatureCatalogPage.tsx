import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormInstance } from 'antd';
import {
  App,
  Breadcrumb,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Row,
  Col,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { usePermission } from '../hooks/usePermission';
import {
  createFeatureDefinition,
  deleteFeatureDefinition,
  getFeatureDefinitions,
  getFeatureReferences,
  testFeatureExpression,
  updateFeatureDefinition,
} from '../api/featureCatalog';
import type {
  FeatureCatalogQueryParams,
  FeatureDefinition,
  FeatureDefinitionRequest,
  FeatureReference,
} from '../types/featureCatalog';

const { Text } = Typography;

// 特征类型收敛为四种实际形态（数值/文本/布尔/日期）；决策链路不消费该字段，仅作元数据
const DATA_TYPE_OPTIONS = ['STRING', 'NUMBER', 'BOOLEAN', 'DATE'];
// 来源类型：请求输入 / 派生计算 / 外部平台
const SOURCE_TYPE_OPTIONS = ['INPUT', 'DERIVED', 'EXTERNAL'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE', 'DEPRECATED'];

export default function FeatureCatalogPage() {
  const { message } = App.useApp();
  const { t } = useTranslation();
  const { hasPermission, isSuperAdmin } = usePermission();
  const canManage = isSuperAdmin || hasPermission('api:feature-catalog:manage');

  const [data, setData] = useState<FeatureDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [referencesLoading, setReferencesLoading] = useState(false);
  const [references, setReferences] = useState<FeatureReference[]>([]);
  const [referenceOpen, setReferenceOpen] = useState(false);
  const [editingFeature, setEditingFeature] = useState<FeatureDefinition | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [filters, setFilters] = useState<FeatureCatalogQueryParams>({ status: 'ACTIVE' });
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm<FeatureDefinitionRequest>();
  const watchSourceType = Form.useWatch('sourceType', editForm);

  const loadData = useCallback(async (nextFilters?: FeatureCatalogQueryParams, nextPage?: { current?: number; pageSize?: number }) => {
    const mergedFilters = nextFilters ?? filters;
    const current = nextPage?.current ?? pagination.current;
    const pageSize = nextPage?.pageSize ?? pagination.pageSize;

    setLoading(true);
    try {
      const response = await getFeatureDefinitions({
        ...mergedFilters,
        page: current - 1,
        size: pageSize,
      });
      setData(response.content);
      setPagination({ current: response.number + 1, pageSize: response.size, total: response.totalElements });
      setFilters(mergedFilters);
    } catch (error: any) {
      message.error(`${t('featureCatalog.loadFailed')}: ${error.response?.data?.message || error.message}`);
    } finally {
      setLoading(false);
    }
  }, [filters, message, pagination.current, pagination.pageSize, t]);

  useEffect(() => {
    loadData(filters, pagination);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = async () => {
    const values = searchForm.getFieldsValue();
    await loadData(values, { current: 1, pageSize: pagination.pageSize });
  };

  const handleReset = async () => {
    searchForm.resetFields();
    const next = { status: 'ACTIVE' };
    await loadData(next, { current: 1, pageSize: pagination.pageSize });
  };

  const openCreateModal = () => {
    setEditingFeature(null);
    editForm.setFieldsValue({
      code: '',
      name: '',
      dataType: 'STRING',
      sourceType: 'INPUT',
      status: 'ACTIVE',
      aliases: [],
    });
    setModalOpen(true);
  };

  const openEditModal = (feature: FeatureDefinition) => {
    setEditingFeature(feature);
    editForm.setFieldsValue({
      code: feature.code,
      name: feature.name,
      dataType: feature.dataType,
      sourceType: feature.sourceType,
      exampleValue: feature.exampleValue ?? '',
      expression: feature.expression ?? '',
      description: feature.description ?? '',
      status: feature.status,
      owner: feature.owner ?? '',
      aliases: feature.aliases,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await editForm.validateFields();
    try {
      if (editingFeature) {
        await updateFeatureDefinition(editingFeature.code, values);
        message.success(t('featureCatalog.updateSuccess'));
      } else {
        await createFeatureDefinition(values);
        message.success(t('featureCatalog.createSuccess'));
      }
      setModalOpen(false);
      editForm.resetFields();
      await loadData();
    } catch (error: any) {
      message.error(`${t('featureCatalog.saveFailed')}: ${error.response?.data?.message || error.message}`);
    }
  };

  const openReferences = async (feature: FeatureDefinition) => {
    setReferenceOpen(true);
    setReferencesLoading(true);
    try {
      const result = await getFeatureReferences(feature.code);
      setReferences(result);
    } catch (error: any) {
      message.error(`${t('featureCatalog.loadReferencesFailed')}: ${error.response?.data?.message || error.message}`);
    } finally {
      setReferencesLoading(false);
    }
  };

  const handleDelete = async (feature: FeatureDefinition) => {
    // 删除前引用硬校验的 UI 侧预检：有引用先明示（后端仍会兜底拦截）
    let references: FeatureReference[] = [];
    try {
      references = await getFeatureReferences(feature.code);
    } catch {
      // 查询失败不阻塞删除流程，由后端引用校验兜底
    }
    if (references.length > 0) {
      message.warning(
        `${t('featureCatalog.deleteBlockedByRefs', { count: references.length })} ${references.map((r) => r.name).join('、')}`,
      );
      return;
    }
    Modal.confirm({
      title: t('featureCatalog.deleteConfirm'),
      content: t('featureCatalog.deleteConfirmDesc', { code: feature.code }),
      okText: t('common.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteFeatureDefinition(feature.code);
          message.success(t('featureCatalog.deleteSuccess'));
          await loadData();
        } catch (error: any) {
          message.error(`${t('featureCatalog.deleteFailed')}: ${error.response?.data?.message || error.message}`);
        }
      },
    });
  };

  const columns: ColumnsType<FeatureDefinition> = useMemo(() => [
    {
      title: t('featureCatalog.code'),
      dataIndex: 'code',
      key: 'code',
      width: 180,
      render: (value: string, record) => (
        <div style={record.deleted ? { opacity: 0.55 } : undefined}>
          <div style={{ fontWeight: 600, textDecoration: record.deleted ? 'line-through' : undefined }}>{value}</div>
          <div style={{ fontSize: 12, color: '#8c8c8c' }}>{record.name}</div>
        </div>
      ),
    },
    {
      title: t('featureCatalog.type'),
      dataIndex: 'dataType',
      key: 'dataType',
      width: 120,
      render: (value: string) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: t('featureCatalog.sourceType'),
      dataIndex: 'sourceType',
      key: 'sourceType',
      width: 120,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (value: string, record) =>
        record.deleted ? <Tag color="red">{t('featureCatalog.deletedTag')}</Tag> : <Tag color={value === 'ACTIVE' ? 'green' : value === 'DEPRECATED' ? 'volcano' : 'default'}>{value}</Tag>,
    },
    {
      title: t('featureCatalog.aliases'),
      dataIndex: 'aliases',
      key: 'aliases',
      width: 240,
      render: (aliases: string[]) => aliases.length > 0 ? (
        <Space size={[4, 4]} wrap>
          {aliases.map((alias) => <Tag key={alias}>{alias}</Tag>)}
        </Space>
      ) : '-',
    },
    {
      title: t('featureCatalog.exampleValue'),
      dataIndex: 'exampleValue',
      key: 'exampleValue',
      width: 180,
      render: (value?: string | null) => value || '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openReferences(record)}>
            {t('featureCatalog.references')}
          </Button>
          {canManage && !record.deleted && (
            <Button type="link" size="small" onClick={() => openEditModal(record)}>
              {t('common.edit')}
            </Button>
          )}
          {canManage && !record.deleted && (
            <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
              {t('common.delete')}
            </Button>
          )}
        </Space>
      ),
    },
  ], [canManage, t, openReferences, openEditModal, handleDelete]);

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('featureCatalog.pageTitle') }]} />

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, marginBottom: 16, flexWrap: 'wrap' }}>
          <div>
            <Text strong style={{ fontSize: 16 }}>{t('featureCatalog.pageTitle')}</Text>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>{t('featureCatalog.pageDescription')}</div>
          </div>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadData()}>
              {t('common.refresh')}
            </Button>
            {canManage && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
                {t('featureCatalog.createFeature')}
              </Button>
            )}
          </Space>
        </div>

        <Form form={searchForm} layout="inline" initialValues={filters} style={{ marginBottom: 16, rowGap: 12 }}>
          <Form.Item name="keyword">
            <Input allowClear placeholder={t('featureCatalog.searchPlaceholder')} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="dataType">
            <Select allowClear placeholder={t('featureCatalog.type')} style={{ width: 140 }} options={DATA_TYPE_OPTIONS.map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="sourceType">
            <Select allowClear placeholder={t('featureCatalog.sourceType')} style={{ width: 140 }} options={SOURCE_TYPE_OPTIONS.map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="status">
            <Select allowClear placeholder={t('common.status')} style={{ width: 140 }} options={STATUS_OPTIONS.map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="includeDeleted" valuePropName="checked" style={{ marginBottom: 0 }}>
            <Switch checkedChildren={t('featureCatalog.showDeleted')} unCheckedChildren={t('featureCatalog.showDeleted')} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleSearch}>{t('common.search')}</Button>
              <Button onClick={handleReset}>{t('common.reset')}</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          scroll={{ x: 1400 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
          }}
          onChange={(nextPagination) => {
            loadData(filters, { current: nextPagination.current, pageSize: nextPagination.pageSize });
          }}
        />
      </Card>

      <Modal
        title={editingFeature ? t('featureCatalog.editFeature') : t('featureCatalog.createFeature')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        destroyOnHidden
        width={720}
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label={t('featureCatalog.code')} rules={[{ required: true, message: t('featureCatalog.codeRequired') }]}>
                <Input disabled={!!editingFeature} placeholder="risk_score" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label={t('featureCatalog.name')} rules={[{ required: true, message: t('featureCatalog.nameRequired') }]}>
                <Input placeholder={t('featureCatalog.namePlaceholder')} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="dataType" label={t('featureCatalog.type')} rules={[{ required: true, message: t('featureCatalog.typeRequired') }]}>
                <Select options={DATA_TYPE_OPTIONS.map((value) => ({ value, label: value }))} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="sourceType" label={t('featureCatalog.sourceType')} rules={[{ required: true, message: t('featureCatalog.sourceTypeRequired') }]}>
                <Select options={SOURCE_TYPE_OPTIONS.map((value) => ({ value, label: value }))} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label={t('common.status')}>
                <Select options={STATUS_OPTIONS.map((value) => ({ value, label: value }))} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="owner" label={t('featureCatalog.owner')}>
                <Input placeholder="risk-ops" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="exampleValue" label={t('featureCatalog.exampleValue')}>
                <Input placeholder="0.85" />
              </Form.Item>
            </Col>
          </Row>
          {watchSourceType === 'DERIVED' && (
            <Form.Item
              name="expression"
              label={t('featureCatalog.expression')}
              tooltip={t('featureCatalog.expressionTooltip')}
            >
              <Input.TextArea
                rows={3}
                placeholder="amount * 0.8 + riskScore * 0.2"
                style={{ fontFamily: 'monospace' }}
              />
            </Form.Item>
          )}
          {watchSourceType === 'DERIVED' && <ExpressionTester form={editForm} />}
          <Form.Item name="aliases" label={t('featureCatalog.aliases')}>
            <Select mode="tags" tokenSeparators={[',']} placeholder={t('featureCatalog.aliasesPlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={4} placeholder={t('featureCatalog.descriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={t('featureCatalog.referenceDrawerTitle')}
        open={referenceOpen}
        width={420}
        onClose={() => setReferenceOpen(false)}
      >
        {referencesLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
            <Spin />
          </div>
        ) : references.length === 0 ? (
          <div style={{ color: '#8c8c8c' }}>{t('featureCatalog.noReferences')}</div>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            {references.map((reference) => (
              <Card key={`${reference.type}-${reference.key}`} size="small">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{reference.name}</div>
                    <div style={{ color: '#8c8c8c', fontSize: 12 }}>{reference.key}</div>
                  </div>
                  <Tag color={reference.type === 'rule' ? 'blue' : 'purple'}>{reference.type}</Tag>
                </div>
              </Card>
            ))}
          </Space>
        )}
      </Drawer>
    </>
  );
}

/** 衍生公式试算区块：表达式变化防抖提取变量 → 动态渲染采样输入 → 试算展示结果/错误 */
function ExpressionTester({ form }: { form: FormInstance<FeatureDefinitionRequest> }) {
  const { t } = useTranslation();
  const expression = Form.useWatch('expression', form);
  const [variables, setVariables] = useState<string[]>([]);
  const [sampleValues, setSampleValues] = useState<Record<string, string>>({});
  const [output, setOutput] = useState<{ ok: boolean; text: string } | null>(null);
  const [testing, setTesting] = useState(false);

  // 表达式变化 → 防抖提取变量（不带采样值，仅做语法检查与变量分析）
  useEffect(() => {
    setOutput(null);
    const expr = (expression ?? '').trim();
    if (!expr) {
      setVariables([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        const resp = await testFeatureExpression(expr);
        if (resp.ok) {
          setVariables(resp.variables);
        } else {
          setVariables([]);
          setOutput({ ok: false, text: resp.error ?? t('featureCatalog.testExpressionInvalid') });
        }
      } catch {
        // 网络失败静默（保存时后端仍会校验）
      }
    }, 600);
    return () => window.clearTimeout(timer);
  }, [expression, t]);

  const runTest = async () => {
    const expr = (expression ?? '').trim();
    if (!expr) return;
    setTesting(true);
    try {
      // 数字字符串转数值，让数值公式按数值语义求值
      const values: Record<string, unknown> = {};
      for (const [key, raw] of Object.entries(sampleValues)) {
        const trimmed = raw.trim();
        if (!trimmed) continue;
        const num = Number(trimmed);
        values[key] = trimmed !== '' && !Number.isNaN(num) && /^-?\d+(\.\d+)?$/.test(trimmed) ? num : trimmed;
      }
      const resp = await testFeatureExpression(expr, values);
      setOutput(
        resp.ok
          ? { ok: true, text: `${t('featureCatalog.testExpressionResult')}: ${String(resp.result)}` }
          : { ok: false, text: resp.error ?? t('featureCatalog.testExpressionFailed') },
      );
    } catch {
      setOutput({ ok: false, text: t('featureCatalog.testExpressionFailed') });
    } finally {
      setTesting(false);
    }
  };

  return (
    <div style={{ margin: '-8px 0 16px', padding: 10, background: '#fafafa', border: '1px dashed #d9d9d9', borderRadius: 6 }}>
      <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 6 }}>{t('featureCatalog.testExpressionHint')}</div>
      {variables.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
          {variables.map((name) => (
            <Input
              key={name}
              size="small"
              addonBefore={name}
              placeholder={t('featureCatalog.sampleValuePlaceholder')}
              style={{ width: 220 }}
              value={sampleValues[name] ?? ''}
              onChange={(e) => setSampleValues((prev) => ({ ...prev, [name]: e.target.value }))}
            />
          ))}
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Button size="small" type="primary" ghost loading={testing} disabled={!(expression ?? '').trim()} onClick={runTest}>
          {t('featureCatalog.testExpressionRun')}
        </Button>
        {output && (
          <span style={{ fontSize: 12, color: output.ok ? '#389e0d' : '#cf1322', wordBreak: 'break-all' }}>{output.text}</span>
        )}
      </div>
    </div>
  );
}
