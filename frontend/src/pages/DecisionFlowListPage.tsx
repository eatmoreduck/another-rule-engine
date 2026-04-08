import { useEffect, useState } from 'react';
import { Button, Input, Select, Space, Card, Breadcrumb, message, Popconfirm, Switch, Table, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, CheckCircleOutlined, StopOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useDecisionFlowStore } from '../stores/decisionFlowStore';
import { DecisionFlowStatus } from '../types/decisionFlow';
import type { DecisionFlow } from '../types/decisionFlow';
import { deleteDecisionFlow, enableDecisionFlow, disableDecisionFlow } from '../api/decisionFlows';
import Access from '../components/AccessControl';

export default function DecisionFlowListPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { loading, fetchFlows, currentParams, flows, total } = useDecisionFlowStore();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<DecisionFlowStatus | undefined>();

  const loadData = () => {
    fetchFlows({ keyword: keyword || undefined, status: statusFilter, page: 0, size: 20 });
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSearch = (value: string) => {
    setKeyword(value);
    fetchFlows({ keyword: value || undefined, status: statusFilter, page: 0, size: 20 });
  };

  const handleStatusChange = (value: DecisionFlowStatus | undefined) => {
    setStatusFilter(value);
    fetchFlows({ keyword: keyword || undefined, status: value, page: 0, size: 20 });
  };

  const handlePageChange = (page: number, pageSize: number) => {
    fetchFlows({ ...currentParams, page: page - 1, size: pageSize });
  };

  const handleDelete = async (flowKey: string) => {
    try {
      await deleteDecisionFlow(flowKey);
      message.success(t('flows.deleteSuccess'));
      loadData();
    } catch {
      message.error(t('flows.deleteFailed'));
    }
  };

  const handleToggleEnabled = async (record: DecisionFlow) => {
    try {
      if (record.enabled) {
        await disableDecisionFlow(record.flowKey);
        message.success(t('common.disabled'));
      } else {
        await enableDecisionFlow(record.flowKey);
        message.success(t('common.enabled'));
      }
      loadData();
    } catch {
      message.error(t('common.error'));
    }
  };

  const columns = [
    { title: 'Flow Key', dataIndex: 'flowKey', key: 'flowKey', render: (v: string) => <a onClick={() => navigate(`/decision-flows/${v}`)}>{v}</a> },
    { title: t('common.name'), dataIndex: 'flowName', key: 'flowName' },
    {
      title: t('common.status'), dataIndex: 'status', key: 'status',
      render: (status: DecisionFlowStatus) => {
        const colorMap: Record<string, string> = { DRAFT: 'default', ACTIVE: 'green', ARCHIVED: 'orange', DELETED: 'red' };
        return <Tag color={colorMap[status] ?? 'default'}>{t(`flows.status.${status}`)}</Tag>;
      },
    },
    { title: t('common.version'), dataIndex: 'version', key: 'version' },
    {
      title: t('common.enable'), dataIndex: 'enabled', key: 'enabled',
      render: (enabled: boolean, record: DecisionFlow) => (
        <Access permission="api:decision-flows:update" fallback={
          <Tag color={enabled ? 'green' : 'default'}>{enabled ? t('common.enabled') : t('common.disabled')}</Tag>
        }>
          <Switch
            size="small"
            checked={enabled}
            onChange={() => handleToggleEnabled(record)}
            checkedChildren={<CheckCircleOutlined />}
            unCheckedChildren={<StopOutlined />}
          />
        </Access>
      ),
    },
    { title: t('common.createdBy'), dataIndex: 'createdBy', key: 'createdBy' },
    {
      title: t('common.createdAt'), dataIndex: 'createdAt', key: 'createdAt',
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-',
    },
    {
      title: t('common.actions'), key: 'actions',
      render: (_: unknown, record: DecisionFlow) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/decision-flows/${record.flowKey}`)}>{t('common.detail')}</Button>
          <Access permission="api:decision-flows:update">
            <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/decision-flows/${record.flowKey}/edit`)}>{t('common.edit')}</Button>
          </Access>
          <Access permission="api:decision-flows:delete">
            <Popconfirm title={t('common.confirmDelete')} onConfirm={() => handleDelete(record.flowKey)}>
              <Button size="small" danger icon={<DeleteOutlined />}>{t('common.delete')}</Button>
            </Popconfirm>
          </Access>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('flows.pageTitle') }]} />
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Space>
            <Input.Search placeholder={t('flows.searchPlaceholder')} allowClear onSearch={handleSearch} style={{ width: 300 }} />
            <Select placeholder={t('flows.statusFilter')} allowClear style={{ width: 150 }} onChange={handleStatusChange}
              options={[
                { value: DecisionFlowStatus.DRAFT, label: t('flows.statusFilterOptions.DRAFT') },
                { value: DecisionFlowStatus.ACTIVE, label: t('flows.statusFilterOptions.ACTIVE') },
                { value: DecisionFlowStatus.ARCHIVED, label: t('flows.statusFilterOptions.ARCHIVED') },
              ]}
            />
          </Space>
          <Access permission="api:decision-flows:create">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/decision-flows/new')}>
              {t('flows.createFlow')}
            </Button>
          </Access>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={flows}
          loading={loading}
          pagination={{
            current: (currentParams.page ?? 0) + 1,
            pageSize: currentParams.size ?? 20,
            total,
            onChange: handlePageChange,
          }}
        />
      </Card>
    </>
  );
}
