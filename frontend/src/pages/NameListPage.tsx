import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Button, Space, Select, Input, Modal, Form, Breadcrumb, Tag, Popconfirm, Alert, DatePicker } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { getNameListEntries, createNameListEntry, deleteNameListEntry } from '../api/nameList';
import type { NameListEntry } from '../api/nameList';
import { KEY_TYPE_LABELS } from '../types/flowConfig';
import { formatDateTime } from '../utils/format';

const LIST_TYPE_MAP: Record<string, { label: string; color: string }> = {
  BLACK: { label: '黑名单', color: 'red' },
  WHITE: { label: '白名单', color: 'green' },
};

export default function NameListPage() {
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
      setError(err instanceof Error ? err.message : '加载名单失败');
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
      setError(err instanceof Error ? err.message : '删除失败');
    }
  }, [fetchData]);

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    {
      title: '名单 Key',
      dataIndex: 'listKey',
      key: 'listKey',
      width: 120,
      render: (val: string) => <Tag>{val || 'GLOBAL'}</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'listType',
      key: 'listType',
      width: 100,
      render: (val: string) => {
        const info = LIST_TYPE_MAP[val];
        return info ? <Tag color={info.color}>{info.label}</Tag> : <Tag>{val}</Tag>;
      },
    },
    {
      title: '键类型',
      dataIndex: 'keyType',
      key: 'keyType',
      width: 100,
      render: (val: string) => KEY_TYPE_LABELS[val] ?? val,
    },
    { title: '键值', dataIndex: 'keyValue', key: 'keyValue', ellipsis: true, width: 180 },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true, width: 150 },
    { title: '来源', dataIndex: 'source', key: 'source', ellipsis: true, width: 100 },
    {
      title: '过期时间',
      dataIndex: 'expiredAt',
      key: 'expiredAt',
      width: 160,
      render: (val: string | null) => formatDateTime(val),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: unknown, record: NameListEntry) => (
        <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)} okText="确认" cancelText="取消">
          <Button type="link" danger icon={<DeleteOutlined />} size="small">删除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '名单管理' }]} />
      <Card>
        <div className="page-header">
          <h2 className="page-header-title">名单管理</h2>
          <div className="page-header-actions">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
          </div>
        </div>

        {error && (
          <Alert type="error" message={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
        )}

        <Space style={{ marginBottom: 16 }} wrap>
          <Input
            placeholder="名单 Key (listKey)"
            allowClear
            style={{ width: 160 }}
            value={filterListKey}
            onChange={(e) => { setFilterListKey(e.target.value || undefined); setPage(1); }}
          />
          <Select
            placeholder="名单类型"
            allowClear
            style={{ width: 140 }}
            value={filterListType}
            onChange={(val) => { setFilterListType(val); setPage(1); }}
            options={Object.entries(LIST_TYPE_MAP).map(([key, info]) => ({ value: key, label: info.label }))}
          />
          <Select
            placeholder="键类型"
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
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
          scroll={{ x: 1200 }}
        />

        <Modal
          title="新增名单条目"
          open={modalOpen}
          onOk={handleModalOk}
          onCancel={() => setModalOpen(false)}
          confirmLoading={confirmLoading}
          okText="确认"
          cancelText="取消"
        >
          <Form form={form} layout="vertical">
            <Form.Item name="listType" label="名单类型" rules={[{ required: true, message: '请选择名单类型' }]}>
              <Select
                placeholder="选择名单类型"
                options={Object.entries(LIST_TYPE_MAP).map(([key, info]) => ({ value: key, label: info.label }))}
              />
            </Form.Item>
            <Form.Item name="listKey" label="名单 Key">
              <Input placeholder="决策流 Key（留空则全局生效）" />
            </Form.Item>
            <Form.Item name="keyType" label="键类型" rules={[{ required: true, message: '请选择键类型' }]}>
              <Select
                placeholder="选择键类型"
                options={Object.entries(KEY_TYPE_LABELS).map(([key, label]) => ({ value: key, label }))}
              />
            </Form.Item>
            <Form.Item name="keyValue" label="键值" rules={[{ required: true, message: '请输入键值' }]}>
              <Input placeholder="输入键值" />
            </Form.Item>
            <Form.Item name="reason" label="原因">
              <Input.TextArea rows={2} placeholder="加入原因（可选）" />
            </Form.Item>
            <Form.Item name="source" label="来源">
              <Input placeholder="数据来源（可选）" />
            </Form.Item>
            <Form.Item name="expiredAt" label="过期时间">
              <DatePicker style={{ width: '100%' }} placeholder="过期时间（可选）" />
            </Form.Item>
          </Form>
        </Modal>
      </Card>
    </div>
  );
}
