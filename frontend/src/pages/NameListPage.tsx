import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Button, Space, Select, Input, Modal, Form, Breadcrumb, Tag, Popconfirm, Alert, DatePicker } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { getNameListEntries, createNameListEntry, deleteNameListEntry } from '../api/nameList';
import type { NameListEntry } from '../api/nameList';
import { getDecisionFlows } from '../api/decisionFlows';
import { KEY_TYPE_LABELS } from '../types/flowConfig';
import { formatDateTime } from '../utils/format';
import Access from '../components/AccessControl';

export default function NameListPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<NameListEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [filterListType, setFilterListType] = useState<string | undefined>(undefined);
  const [filterKeyType, setFilterKeyType] = useState<string | undefined>(undefined);
  const [filterListKey, setFilterListKey] = useState<string | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm();

  const [flowKeyOptions, setFlowKeyOptions] = useState<{ value: string; label: string }[]>([]);

  const LIST_TYPE_MAP: Record<string, { label: string; color: string }> = {
    BLACK: { label: t('nameList.listTypes.BLACK'), color: 'red' },
    WHITE: { label: t('nameList.listTypes.WHITE'), color: 'green' },
  };

  useEffect(() => {
    getDecisionFlows({ page: 0, size: 200 })
      .then((res) => {
        const opts = res.content.map((f) => ({ value: f.flowKey, label: `${f.flowName} (${f.flowKey})` }));
        opts.unshift({ value: 'GLOBAL', label: t('nameList.globalLabel') });
        setFlowKeyOptions(opts);
      })
      .catch(() => {});
  }, []);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getNameListEntries({
        listKey: filterListKey,
        listType: filterListType,
        keyType: filterKeyType,
        page: page - 1,
        size: pageSize,
      });
      setData(result.content);
      setTotal(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('nameList.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [filterListKey, filterListType, filterKeyType, page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleAdd = useCallback(() => {
    form.resetFields();
    setModalOpen(true);
  }, [form]);

  const handleModalOk = useCallback(async () => {
    try {
      const values = await form.validateFields();
      setConfirmLoading(true);
      setError(null);
      await createNameListEntry(values);
      setModalOpen(false);
      form.resetFields();
      fetchData();
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      }
    } finally {
      setConfirmLoading(false);
    }
  }, [form, fetchData]);

  const handleDelete = useCallback(async (id: number) => {
    try {
      setError(null);
      await deleteNameListEntry(id);
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('nameList.deleteFailed'));
    }
  }, [fetchData]);

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    {
      title: t('nameList.listKey'),
      dataIndex: 'listKey',
      key: 'listKey',
      width: 120,
      render: (val: string) => <Tag>{val || 'GLOBAL'}</Tag>,
    },
    {
      title: t('common.type'),
      dataIndex: 'listType',
      key: 'listType',
      width: 100,
      render: (val: string) => {
        const info = LIST_TYPE_MAP[val];
        return info ? <Tag color={info.color}>{info.label}</Tag> : <Tag>{val}</Tag>;
      },
    },
    {
      title: t('nameList.keyType'),
      dataIndex: 'keyType',
      key: 'keyType',
      width: 100,
      render: (val: string) => KEY_TYPE_LABELS[val] ?? val,
    },
    { title: t('nameList.keyValue'), dataIndex: 'keyValue', key: 'keyValue', ellipsis: true, width: 180 },
    { title: t('nameList.reason'), dataIndex: 'reason', key: 'reason', ellipsis: true, width: 150 },
    { title: t('nameList.source'), dataIndex: 'source', key: 'source', ellipsis: true, width: 100 },
    {
      title: t('nameList.expiredAt'),
      dataIndex: 'expiredAt',
      key: 'expiredAt',
      width: 160,
      render: (val: string | null) => formatDateTime(val),
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 80,
      render: (_: unknown, record: NameListEntry) => (
        <Access permission="api:name-list:delete">
          <Popconfirm title={t('nameList.confirmDelete')} onConfirm={() => handleDelete(record.id)} okText={t('common.confirm')} cancelText={t('common.cancel')}>
            <Button type="link" danger icon={<DeleteOutlined />} size="small">{t('common.delete')}</Button>
          </Popconfirm>
        </Access>
      ),
    },
  ];

  return (
    <div>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('nameList.pageTitle') }]} />
      <Card>
        <div className="page-header">
          <h2 className="page-header-title">{t('nameList.pageTitle')}</h2>
          <div className="page-header-actions">
            <Access permission="api:name-list:manage">
              <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>{t('nameList.addButton')}</Button>
            </Access>
          </div>
        </div>

        {error && (
          <Alert type="error" message={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
        )}

        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder={t('nameList.listKeyPlaceholder')}
            allowClear
            showSearch
            style={{ width: 200 }}
            value={filterListKey}
            onChange={(val) => { setFilterListKey(val); setPage(1); }}
            options={flowKeyOptions}
            filterOption={(input, option) => (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false}
          />
          <Select
            placeholder={t('nameList.listTypePlaceholder')}
            allowClear
            style={{ width: 140 }}
            value={filterListType}
            onChange={(val) => { setFilterListType(val); setPage(1); }}
            options={Object.entries(LIST_TYPE_MAP).map(([key, info]) => ({ value: key, label: info.label }))}
          />
          <Select
            placeholder={t('nameList.keyTypePlaceholder')}
            allowClear
            style={{ width: 140 }}
            value={filterKeyType}
            onChange={(val) => { setFilterKeyType(val); setPage(1); }}
            options={Object.entries(KEY_TYPE_LABELS).map(([key, label]) => ({ value: key, label }))}
          />
        </Space>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t_val) => t('common.totalShort', { count: t_val }),
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
          scroll={{ x: 1200 }}
        />

        <Modal
          title={t('nameList.addEntry')}
          open={modalOpen}
          onOk={handleModalOk}
          onCancel={() => setModalOpen(false)}
          confirmLoading={confirmLoading}
          okText={t('common.confirm')}
          cancelText={t('common.cancel')}
        >
          <Form form={form} layout="vertical">
            <Form.Item name="listType" label={t('nameList.listType')} rules={[{ required: true, message: t('nameList.listTypeRequired') }]}>
              <Select
                placeholder={t('nameList.selectListTypePlaceholder')}
                options={Object.entries(LIST_TYPE_MAP).map(([key, info]) => ({ value: key, label: info.label }))}
              />
            </Form.Item>
            <Form.Item name="listKey" label={t('nameList.listKey')}>
              <Select
                placeholder={t('nameList.selectFlowPlaceholder')}
                allowClear
                showSearch
                options={flowKeyOptions}
                filterOption={(input, option) => (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false}
              />
            </Form.Item>
            <Form.Item name="keyType" label={t('nameList.keyType')} rules={[{ required: true, message: t('nameList.keyTypeRequired') }]}>
              <Select
                placeholder={t('nameList.selectKeyTypePlaceholder')}
                options={Object.entries(KEY_TYPE_LABELS).map(([key, label]) => ({ value: key, label }))}
              />
            </Form.Item>
            <Form.Item name="keyValue" label={t('nameList.keyValue')} rules={[{ required: true, message: t('nameList.keyValueRequired') }]}>
              <Input placeholder={t('nameList.keyValuePlaceholder')} />
            </Form.Item>
            <Form.Item name="reason" label={t('nameList.reason')}>
              <Input.TextArea rows={2} placeholder={t('nameList.reasonPlaceholder')} />
            </Form.Item>
            <Form.Item name="source" label={t('nameList.source')}>
              <Input placeholder={t('nameList.sourcePlaceholder')} />
            </Form.Item>
            <Form.Item name="expiredAt" label={t('nameList.expiredAt')}>
              <DatePicker style={{ width: '100%' }} placeholder={t('nameList.expiredAtPlaceholder')} />
            </Form.Item>
          </Form>
        </Modal>
      </Card>
    </div>
  );
}
