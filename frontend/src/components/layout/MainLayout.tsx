/**
 * MainLayout - 顶部导航布局
 * Plan 03-01/03-04: 主布局组件
 */

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  SafetyOutlined,
  UnorderedListOutlined,
  ExperimentOutlined,
  SettingOutlined,
} from '@ant-design/icons';

const { Header, Content } = Layout;
const { Title } = Typography;

const menuItems = [
  {
    key: '/rules',
    icon: <UnorderedListOutlined />,
    label: '规则管理',
  },
  {
    key: '/test',
    icon: <ExperimentOutlined />,
    label: '测试验证',
  },
  {
    key: '/settings',
    icon: <SettingOutlined />,
    label: '系统设置',
  },
];

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  // 计算当前选中的菜单项
  const selectedKey = menuItems.find((item) => location.pathname.startsWith(item.key))?.key ?? '/rules';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          padding: '0 24px',
          gap: '24px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }} onClick={() => navigate('/rules')}>
          <SafetyOutlined style={{ fontSize: 24, color: '#1890ff' }} />
          <Title level={4} style={{ margin: 0, color: '#333' }}>
            规则引擎
          </Title>
        </div>
        <Menu
          mode="horizontal"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, border: 'none' }}
        />
      </Header>
      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        <div style={{ maxWidth: 1400, margin: '0 auto' }}>
          <Outlet />
        </div>
      </Content>
    </Layout>
  );
}
