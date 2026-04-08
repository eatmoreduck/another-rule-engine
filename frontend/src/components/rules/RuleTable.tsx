import { Table, Switch, Popconfirm, Button, Space, Tooltip, Tag } from 'antd';
import { DeleteOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import type { Rule } from '../../types/rule';
import { useRuleStore } from '../../stores/ruleStore';
import Access from '../AccessControl';
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
  const { t } = useTranslation();
  const { deleteRule, toggleEnabled } = useRuleStore();

  const columns: ColumnsType<Rule> = [
    {
      title: t('rules.ruleKey'),
      dataIndex: 'ruleKey',
      key: 'ruleKey',
      width: 180,
      ellipsis: true,
    },
    {
      title: t('rules.ruleName'),
      dataIndex: 'ruleName',
      key: 'ruleName',
      width: 200,
      ellipsis: true,
    },
    {
      title: t('common.version'),
      dataIndex: 'version',
      key: 'version',
      width: 80,
      align: 'center',
    },
    {
      title: t('common.enable'),
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      align: 'center',
      render: (enabled: boolean, record: Rule) => (
        <Access permission="api:rules:update">
          <Switch
            checked={enabled}
            onChange={(checked) => toggleEnabled(record.ruleKey, checked)}
            size="small"
          />
        </Access>
      ),
    },
    {
      title: t('common.createdBy'),
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 120,
      ellipsis: true,
    },
    {
      title: t('common.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (val: string | null) =>
        val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: t('common.actions'),
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
            {t('common.view')}
          </Button>
          <Access permission="api:rules:update">
            <Tooltip title={t('rules.formEdit')}>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => navigate(`/rules/${record.ruleKey}/edit`)}
              >
                {t('common.edit')}
              </Button>
            </Tooltip>
          </Access>
          <Access permission="api:rules:delete">
            <Popconfirm
              title={t('common.confirmDelete')}
              description={t('rules.confirmDeleteName', { name: record.ruleName })}
              onConfirm={() => deleteRule(record.ruleKey)}
              okText={t('common.ok')}
              cancelText={t('common.cancel')}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                {t('common.delete')}
              </Button>
            </Popconfirm>
          </Access>
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
        showTotal: (t_val) => t('common.totalShort', { count: t_val }),
        onChange: (p, ps) => onPageChange(p - 1, ps),
      }}
    />
  );
}
