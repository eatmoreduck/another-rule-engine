import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Breadcrumb,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { usePermission } from '../hooks/usePermission';
import {
  createFeatureDefinition,
  getFeatureDefinitions,
  getFeatureReferences,
  updateFeatureDefinition,
} from '../api/featureCatalog';
import type {
  FeatureCatalogQueryParams,
  FeatureDefinition,
  FeatureDefinitionRequest,
  FeatureReference,
} from '../types/featureCatalog';

const { Text } = Typography;

const DATA_TYPE_OPTIONS = ['STRING', 'NUMBER', 'BOOLEAN', 'TEXT', 'LIST', 'ARRAY', 'INTEGER', 'LONG', 'DOUBLE', 'DECIMAL'];
const SOURCE_TYPE_OPTIONS = ['INPUT', 'DERIVED', 'EXTERNAL', 'MODEL'];
const SENSITIVITY_OPTIONS = ['NORMAL', 'SENSITIVE', 'HIGHLY_SENSITIVE'];
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
      sensitivity: 'NORMAL',
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
      description: feature.description ?? '',
      scope: feature.scope ?? '',
      sensitivity: feature.sensitivity,
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

  const columns: ColumnsType<FeatureDefinition> = useMemo(() => [
    {
      title: t('featureCatalog.code'),
      dataIndex: 'code',
      key: 'code',
      width: 180,
      render: (value: string, record) => (
        <div>
          <div style={{ fontWeight: 600 }}>{value}</div>
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
      title: t('featureCatalog.scope'),
      dataIndex: 'scope',
      key: 'scope',
      width: 140,
      render: (value?: string | null) => value || '-',
    },
    {
      title: t('featureCatalog.sensitivity'),
      dataIndex: 'sensitivity',
      key: 'sensitivity',
      width: 150,
      render: (value: string) => <Tag color={value === 'HIGHLY_SENSITIVE' ? 'red' : value === 'SENSITIVE' ? 'orange' : 'green'}>{value}</Tag>,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : value === 'DEPRECATED' ? 'volcano' : 'default'}>{value}</Tag>,
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
          {canManage && (
            <Button type="link" size="small" onClick={() => openEditModal(record)}>
              {t('common.edit')}
            </Button>
          )}
        </Space>
      ),
    },
  ], [canManage, t]);

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
          <Form.Item name="sensitivity">
            <Select allowClear placeholder={t('featureCatalog.sensitivity')} style={{ width: 160 }} options={SENSITIVITY_OPTIONS.map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="status">
            <Select allowClear placeholder={t('common.status')} style={{ width: 140 }} options={STATUS_OPTIONS.map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name="scope">
            <Input allowClear placeholder={t('featureCatalog.scope')} style={{ width: 160 }} />
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
        destroyOnClose
        width={720}
      >
        <Form form={editForm} layout="vertical">
          <Space style={{ width: '100%' }} size={16} align="start">
            <Form.Item name="code" label={t('featureCatalog.code')} rules={[{ required: true, message: t('featureCatalog.codeRequired') }]} style={{ width: 280 }}>
              <Input disabled={!!editingFeature} placeholder="risk_score" />
            </Form.Item>
            <Form.Item name="name" label={t('featureCatalog.name')} rules={[{ required: true, message: t('featureCatalog.nameRequired') }]} style={{ width: 280 }}>
              <Input placeholder={t('featureCatalog.namePlaceholder')} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={16} align="start">
            <Form.Item name="dataType" label={t('featureCatalog.type')} rules={[{ required: true, message: t('featureCatalog.typeRequired') }]} style={{ width: 160 }}>
              <Select options={DATA_TYPE_OPTIONS.map((value) => ({ value, label: value }))} />
            </Form.Item>
            <Form.Item name="sourceType" label={t('featureCatalog.sourceType')} rules={[{ required: true, message: t('featureCatalog.sourceTypeRequired') }]} style={{ width: 160 }}>
              <Select options={SOURCE_TYPE_OPTIONS.map((value) => ({ value, label: value }))} />
            </Form.Item>
            <Form.Item name="sensitivity" label={t('featureCatalog.sensitivity')} style={{ width: 180 }}>
              <Select options={SENSITIVITY_OPTIONS.map((value) => ({ value, label: value }))} />
            </Form.Item>
            <Form.Item name="status" label={t('common.status')} style={{ width: 160 }}>
              <Select options={STATUS_OPTIONS.map((value) => ({ value, label: value }))} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={16} align="start">
            <Form.Item name="scope" label={t('featureCatalog.scope')} style={{ width: 220 }}>
              <Input placeholder="ORDER" />
            </Form.Item>
            <Form.Item name="owner" label={t('featureCatalog.owner')} style={{ width: 220 }}>
              <Input placeholder="risk-ops" />
            </Form.Item>
            <Form.Item name="exampleValue" label={t('featureCatalog.exampleValue')} style={{ width: 220 }}>
              <Input placeholder="0.85" />
            </Form.Item>
          </Space>
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
