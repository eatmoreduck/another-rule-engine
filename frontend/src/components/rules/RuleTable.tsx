import { Table, Switch, Popconfirm, Button, Space, Tooltip } from 'antd';
import { DeleteOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import type { Rule } from '../../types/rule';
import RuleStatusBadge from './RuleStatusBadge';
import { useRuleStore } from '../../stores/ruleStore';
import dayjs from 'dayjs';

interface RuleTableProps {
  rules: Rule[];
  loading: boolean;
  total: number;
  page: number;
  pageSize: number;
  onPageChange: (page: number, pageSize: number) => void;
}

export default function RuleTable({
  rules,
  loading,
  total,
  page,
  pageSize,
  onPageChange,
}: RuleTableProps) {
  const navigate = useNavigate();
  const { deleteRule, toggleEnabled } = useRuleStore();

  const columns: ColumnsType<Rule> = [
    {
      title: '规则Key',
      dataIndex: 'ruleKey',
      key: 'ruleKey',
      width: 180,
      ellipsis: true,
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      width: 200,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: Rule['status']) => <RuleStatusBadge status={status} />,
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 80,
      align: 'center',
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      align: 'center',
      render: (enabled: boolean, record: Rule) => (
        <Switch
          checked={enabled}
          onChange={(checked) => toggleEnabled(record.ruleKey, checked)}
          size="small"
        />
      ),
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 120,
      ellipsis: true,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (val: string | null) =>
        val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/rules/${record.ruleKey}`)}
          >
            查看
          </Button>
          <Tooltip title="表单编辑">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => navigate(`/rules/${record.ruleKey}/edit`)}
            >
              编辑
            </Button>
          </Tooltip>
          <Popconfirm
            title="确认删除"
            description={`确定要删除规则 "${record.ruleName}" 吗？`}
            onConfirm={() => deleteRule(record.ruleKey)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Table<Rule>
      columns={columns}
      dataSource={rules}
      rowKey="id"
      loading={loading}
      pagination={{
        current: page + 1,
        pageSize,
        total,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
        onChange: (p, ps) => onPageChange(p - 1, ps),
      }}
    />
  );
}
