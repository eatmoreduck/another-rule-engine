import { useState, useCallback } from 'react';
import {
  Table, Card, Space, DatePicker, Input, Select, Tag, Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { queryAuditLogs } from '../../api/system';
import type { AuditLogDTO } from '../../api/system';

const { Text } = Typography;
const { RangePicker } = DatePicker;

const ENTITY_TYPE_OPTIONS = [
  { label: '规则', value: 'RULE' },
  { label: '决策流', value: 'DECISION_FLOW' },
  { label: '版本', value: 'VERSION' },
  { label: '用户', value: 'USER' },
  { label: '角色', value: 'ROLE' },
  { label: '团队', value: 'TEAM' },
];

const OPERATION_OPTIONS = [
  { label: '规则创建', value: 'RULE_CREATE' },
  { label: '规则更新', value: 'RULE_UPDATE' },
  { label: '规则删除', value: 'RULE_DELETE' },
  { label: '规则启用', value: 'RULE_ENABLE' },
  { label: '规则禁用', value: 'RULE_DISABLE' },
  { label: '决策流创建', value: 'FLOW_CREATE' },
  { label: '决策流更新', value: 'FLOW_UPDATE' },
  { label: '决策流删除', value: 'FLOW_DELETE' },
  { label: '决策流启用', value: 'FLOW_ENABLE' },
  { label: '决策流禁用', value: 'FLOW_DISABLE' },
  { label: '用户创建', value: 'USER_CREATE' },
  { label: '用户更新', value: 'USER_UPDATE' },
  { label: '用户禁用', value: 'USER_DISABLE' },
  { label: '密码重置', value: 'USER_RESET_PASSWORD' },
  { label: '角色权限更新', value: 'ROLE_UPDATE_PERMISSIONS' },
  { label: '团队创建', value: 'TEAM_CREATE' },
  { label: '团队更新', value: 'TEAM_UPDATE' },
  { label: '团队删除', value: 'TEAM_DELETE' },
  { label: '系统登录', value: 'SYSTEM_LOGIN' },
  { label: '系统登出', value: 'SYSTEM_LOGOUT' },
];

const STATUS_TAG_MAP: Record<string, { color: string; label: string }> = {
  SUCCESS: { color: 'green', label: '成功' },
  FAILED: { color: 'red', label: '失败' },
};

const columns: ColumnsType<AuditLogDTO> = [
  {
    title: '时间',
    dataIndex: 'operationTime',
    key: 'operationTime',
    width: 180,
    render: (val: string) => val ? new Date(val).toLocaleString('zh-CN') : '-',
  },
  {
    title: '操作人',
    dataIndex: 'operator',
    key: 'operator',
    width: 140,
  },
  {
    title: '操作类型',
    dataIndex: 'operation',
    key: 'operation',
    width: 160,
    render: (val: string) => <Tag>{val}</Tag>,
  },
  {
    title: '资源类型',
    dataIndex: 'entityType',
    key: 'entityType',
    width: 100,
    render: (val: string) => {
        const map: Record<string, string> = {
          RULE: '规则',
          DECISION_FLOW: '决策流',
          VERSION: '版本',
          USER: '用户',
          ROLE: '角色',
          TEAM: '团队',
        };
        return map[val] || val;
      },
  },
  {
    title: '资源标识',
    dataIndex: 'entityId',
    key: 'entityId',
    width: 180,
    render: (val: string) => <Text copyable={{ text: val }}>{val}</Text>,
  },
  {
    title: 'IP地址',
    dataIndex: 'operatorIp',
    key: 'operatorIp',
    width: 130,
    render: (val: string) => val || '-',
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 80,
    render: (val: string) => {
      const cfg = STATUS_TAG_MAP[val] || { color: 'default', label: val };
      return <Tag color={cfg.color}>{cfg.label}</Tag>;
    },
  },
  {
    title: '详情',
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

interface FilterState {
  operator?: string;
  entityType?: string;
  operation?: string;
  startTime?: string;
  endTime?: string;
}

export default function AuditLogPage() {
  const [data, setData] = useState<AuditLogDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [filters, setFilters] = useState<FilterState>({});

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
        startTime: new Date(dateStrings[0]).toISOString(),
        endTime: new Date(dateStrings[1]).toISOString(),
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
      title="审计日志"
      extra={
        <Space>
          <Input
            placeholder="操作人"
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
            placeholder="资源类型"
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
            placeholder="操作类型"
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
          showTotal: (total) => `共 ${total} 条`,
          showSizeChanger: true,
        }}
        onChange={handleTableChange}
        scroll={{ x: 1200 }}
      />
    </Card>
  );
}
