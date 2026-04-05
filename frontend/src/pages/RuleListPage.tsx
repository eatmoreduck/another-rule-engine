import { useEffect, useState } from 'react';
import { Button, Input, Space, Card, Breadcrumb, Switch, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import RuleTable from '../components/rules/RuleTable';
import CreateRuleModal from '../components/rules/CreateRuleModal';
import Access from '../components/AccessControl';
import { useRuleStore } from '../stores/ruleStore';

const { Text } = Typography;

export default function RuleListPage() {
  const navigate = useNavigate();
  const { loading, fetchRules, currentParams, rules, total } = useRuleStore();
  const [modalOpen, setModalOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [showDeleted, setShowDeleted] = useState(false);

  const loadData = () => {
    fetchRules({ keyword: keyword || undefined, showDeleted, page: 0, size: 20 });
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSearch = (value: string) => {
    setKeyword(value);
    fetchRules({ keyword: value || undefined, showDeleted, page: 0, size: 20 });
  };

  const handleShowDeletedChange = (checked: boolean) => {
    setShowDeleted(checked);
    fetchRules({ keyword: keyword || undefined, showDeleted: checked, page: 0, size: 20 });
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
            <Space size={4}>
              <Text type="secondary" style={{ fontSize: 13 }}>显示已删除</Text>
              <Switch size="small" checked={showDeleted} onChange={handleShowDeletedChange} />
            </Space>
          </Space>
          <Access permission="api:rules:create">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rules/new')}>
              新建规则
            </Button>
          </Access>
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
