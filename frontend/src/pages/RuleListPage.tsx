import { useEffect, useState } from 'react';
import { Button, Input, Select, Space, Card, Breadcrumb } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import RuleTable from '../components/rules/RuleTable';
import CreateRuleModal from '../components/rules/CreateRuleModal';
import { useRuleStore } from '../stores/ruleStore';
import { RuleStatus } from '../types/rule';

export default function RuleListPage() {
  const navigate = useNavigate();
  const { loading, fetchRules, currentParams, rules, total } = useRuleStore();
  const [modalOpen, setModalOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<RuleStatus | undefined>();

  const loadData = () => {
    fetchRules({ keyword: keyword || undefined, status: statusFilter, page: 0, size: 20 });
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSearch = (value: string) => {
    setKeyword(value);
    fetchRules({ keyword: value || undefined, status: statusFilter, page: 0, size: 20 });
  };

  const handleStatusChange = (value: RuleStatus | undefined) => {
    setStatusFilter(value);
    fetchRules({ keyword: keyword || undefined, status: value, page: 0, size: 20 });
  };

  const handlePageChange = (page: number, pageSize: number) => {
    fetchRules({
      ...currentParams,
      page,
      size: pageSize,
    });
  };

  return (
    <>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[{ title: '规则配置' }]}
      />

      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder="搜索规则名称或 Key"
              allowClear
              onSearch={handleSearch}
              style={{ width: 300 }}
            />
            <Select
              placeholder="状态筛选"
              allowClear
              style={{ width: 150 }}
              onChange={handleStatusChange}
              options={[
                { value: RuleStatus.DRAFT, label: '草稿' },
                { value: RuleStatus.ACTIVE, label: '已激活' },
                { value: RuleStatus.ARCHIVED, label: '已归档' },
              ]}
            />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rules/new')}>
            新建规则
          </Button>
        </div>
        <RuleTable
          rules={rules}
          loading={loading}
          total={total}
          page={currentParams.page ?? 0}
          pageSize={currentParams.size ?? 20}
          onPageChange={handlePageChange}
        />
      </Card>

      <CreateRuleModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSuccess={loadData}
      />
    </>
  );
}
