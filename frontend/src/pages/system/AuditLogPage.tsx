import { useState, useCallback } from 'react';
import {
  Table, Card, Space, DatePicker, Input, Select, Tag, Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import { queryAuditLogs } from '../../api/system';
import type { AuditLogDTO } from '../../api/system';

const { Text } = Typography;
const { RangePicker } = DatePicker;

export default function AuditLogPage() {
  const { t } = useTranslation();

  const ENTITY_TYPE_OPTIONS = [
    { label: t('auditLog.entityTypes.RULE'), value: 'RULE' },
    { label: t('auditLog.entityTypes.DECISION_FLOW'), value: 'DECISION_FLOW' },
    { label: t('auditLog.entityTypes.VERSION'), value: 'VERSION' },
    { label: t('auditLog.entityTypes.USER'), value: 'USER' },
    { label: t('auditLog.entityTypes.ROLE'), value: 'ROLE' },
    { label: t('auditLog.entityTypes.TEAM'), value: 'TEAM' },
  ];

  const OPERATION_OPTIONS = [
    { label: t('auditLog.operations.RULE_CREATE'), value: 'RULE_CREATE' },
    { label: t('auditLog.operations.RULE_UPDATE'), value: 'RULE_UPDATE' },
    { label: t('auditLog.operations.RULE_DELETE'), value: 'RULE_DELETE' },
    { label: t('auditLog.operations.RULE_ENABLE'), value: 'RULE_ENABLE' },
    { label: t('auditLog.operations.RULE_DISABLE'), value: 'RULE_DISABLE' },
    { label: t('auditLog.operations.FLOW_CREATE'), value: 'FLOW_CREATE' },
    { label: t('auditLog.operations.FLOW_UPDATE'), value: 'FLOW_UPDATE' },
    { label: t('auditLog.operations.FLOW_DELETE'), value: 'FLOW_DELETE' },
    { label: t('auditLog.operations.FLOW_ENABLE'), value: 'FLOW_ENABLE' },
    { label: t('auditLog.operations.FLOW_DISABLE'), value: 'FLOW_DISABLE' },
    { label: t('auditLog.operations.USER_CREATE'), value: 'USER_CREATE' },
    { label: t('auditLog.operations.USER_UPDATE'), value: 'USER_UPDATE' },
    { label: t('auditLog.operations.USER_DISABLE'), value: 'USER_DISABLE' },
    { label: t('auditLog.operations.USER_RESET_PASSWORD'), value: 'USER_RESET_PASSWORD' },
    { label: t('auditLog.operations.ROLE_UPDATE_PERMISSIONS'), value: 'ROLE_UPDATE_PERMISSIONS' },
    { label: t('auditLog.operations.TEAM_CREATE'), value: 'TEAM_CREATE' },
    { label: t('auditLog.operations.TEAM_UPDATE'), value: 'TEAM_UPDATE' },
    { label: t('auditLog.operations.TEAM_DELETE'), value: 'TEAM_DELETE' },
    { label: t('auditLog.operations.SYSTEM_LOGIN'), value: 'SYSTEM_LOGIN' },
    { label: t('auditLog.operations.SYSTEM_LOGOUT'), value: 'SYSTEM_LOGOUT' },
  ];

  const STATUS_TAG_MAP: Record<string, { color: string; label: string }> = {
    SUCCESS: { color: 'green', label: t('auditLog.success') },
    FAILED: { color: 'red', label: t('auditLog.failed') },
  };

  const columns: ColumnsType<AuditLogDTO> = [
    {
      title: t('auditLog.time'),
      dataIndex: 'operationTime',
      key: 'operationTime',
      width: 180,
      render: (val: string) => val ? new Date(val).toLocaleString('zh-CN') : '-',
    },
    {
      title: t('auditLog.operator'),
      dataIndex: 'operator',
      key: 'operator',
      width: 140,
    },
    {
      title: t('auditLog.operationType'),
      dataIndex: 'operation',
      key: 'operation',
      width: 160,
      render: (val: string) => <Tag>{val}</Tag>,
    },
    {
      title: t('auditLog.resourceType'),
      dataIndex: 'entityType',
      key: 'entityType',
      width: 100,
      render: (val: string) => t(`auditLog.entityTypes.${val}`, val),
    },
    {
      title: t('auditLog.resourceId'),
      dataIndex: 'entityId',
      key: 'entityId',
      width: 180,
      render: (val: string) => <Text copyable={{ text: val }}>{val}</Text>,
    },
    {
      title: t('auditLog.ipAddress'),
      dataIndex: 'operatorIp',
      key: 'operatorIp',
      width: 130,
      render: (val: string) => val || '-',
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (val: string) => {
        const cfg = STATUS_TAG_MAP[val] || { color: 'default', label: val };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: t('auditLog.detail'),
      dataIndex: 'operationDetail',
      key: 'operationDetail',
      width: 200,
      ellipsis: true,
      render: (val: string | null) => {
        if (!val) return '-';
        try {
          const parsed = JSON.parse(val);
          return <Text type="secondary" ellipsis title={val}>{parsed.method || val}</Text>;
        } catch {
          return <Text type="secondary" ellipsis title={val}>{val}</Text>;
        }
      },
    },
  ];

  const [data, setData] = useState<AuditLogDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [filters, setFilters] = useState<{
    operator?: string;
    entityType?: string;
    operation?: string;
    startTime?: string;
    endTime?: string;
  }>({});

  const loadData = useCallback(async (page = 1, size = 20) => {
    setLoading(true);
    try {
      const result = await queryAuditLogs({
        ...filters,
        page: page - 1,
        size,
      });
      setData(result.content);
      setPagination({
        current: result.number + 1,
        pageSize: result.size,
        total: result.totalElements,
      });
    } catch {
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const handleTableChange = (paginationConfig: any) => {
    loadData(paginationConfig.current, paginationConfig.pageSize);
  };

  const handleDateRangeChange = (_: any, dateStrings: [string | null, string | null] | null) => {
    if (dateStrings && dateStrings[0] && dateStrings[1]) {
      setFilters(prev => ({
        ...prev,
        startTime: new Date(dateStrings[0]!).toISOString(),
        endTime: new Date(dateStrings[1]!).toISOString(),
      }));
    } else {
      setFilters(prev => {
        const next = { ...prev };
        delete next.startTime;
        delete next.endTime;
        return next;
      });
    }
  };

  return (
    <Card
      title={t('auditLog.pageTitle')}
      extra={
        <Space>
          <Input
            placeholder={t('auditLog.operatorPlaceholder')}
            allowClear
            style={{ width: 120 }}
            onPressEnter={(e) => {
              setFilters(prev => ({ ...prev, operator: (e.target as HTMLInputElement).value }));
            }}
            onChange={(e) => {
              if (!e.target.value) {
                setFilters(prev => {
                  const next = { ...prev };
                  delete next.operator;
                  return next;
                });
              }
            }}
          />
          <Select
            placeholder={t('auditLog.resourceTypePlaceholder')}
            allowClear
            style={{ width: 120 }}
            options={ENTITY_TYPE_OPTIONS}
            onChange={(val) => {
              if (val) {
                setFilters(prev => ({ ...prev, entityType: val }));
              } else {
                setFilters(prev => {
                  const next = { ...prev };
                  delete next.entityType;
                  return next;
                });
              }
            }}
          />
          <Select
            placeholder={t('auditLog.operationTypePlaceholder')}
            allowClear
            style={{ width: 140 }}
            options={OPERATION_OPTIONS}
            onChange={(val) => {
              if (val) {
                setFilters(prev => ({ ...prev, operation: val }));
              } else {
                setFilters(prev => {
                  const next = { ...prev };
                  delete next.operation;
                  return next;
                });
              }
            }}
          />
          <RangePicker
            showTime
            format="YYYY-MM-DD HH:mm"
            onChange={handleDateRangeChange}
          />
          <ReloadOutlined
            style={{ cursor: 'pointer', fontSize: 16 }}
            onClick={() => loadData(pagination.current, pagination.pageSize)}
          />
        </Space>
      }
    >
      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        pagination={{
          ...pagination,
          showTotal: (total) => t('common.totalShort', { count: total }),
          showSizeChanger: true,
        }}
        onChange={handleTableChange}
        scroll={{ x: 1200 }}
      />
    </Card>
  );
}
