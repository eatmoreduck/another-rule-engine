import { Layout, Menu, Typography, App, Dropdown, Avatar, Space } from 'antd';
import { SafetyOutlined, SettingOutlined, ExperimentOutlined, BarChartOutlined, DashboardOutlined, CloudServerOutlined, ImportOutlined, ApartmentOutlined, UnorderedListOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';

const { Header, Content } = Layout;
const { Text } = Typography;

const menuItems = [
  {
    key: '/rules',
    icon: <SafetyOutlined />,
    label: '规则配置',
  },
  {
    key: '/decision-flows',
    icon: <ApartmentOutlined />,
    label: '决策流',
  },
  {
    key: '/name-list',
    icon: <UnorderedListOutlined />,
    label: '名单管理',
  },
  {
    key: '/grayscale',
    icon: <ExperimentOutlined />,
    label: '灰度发布',
  },
  {
    key: '/environments',
    icon: <CloudServerOutlined />,
    label: '多环境',
  },
  {
    key: '/import-export',
    icon: <ImportOutlined />,
    label: '导入导出',
  },
  {
    key: '/monitoring',
    icon: <DashboardOutlined />,
    label: '监控仪表盘',
  },
  {
    key: '/analytics',
    icon: <BarChartOutlined />,
    label: '分析中心',
  },
  {
    key: '/settings',
    icon: <SettingOutlined />,
    label: '系统设置',
    disabled: true,
  },
];

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { modal } = App.useApp();

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  const handleLogout = async () => {
    modal.confirm({
      title: '确认登出',
      content: '确定要退出登录吗？',
      onOk: async () => {
        await logout();
        navigate('/login', { replace: true });
      },
    });
  };

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  return (
    <App>
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          padding: '0 24px',
        }}
      >
        <Text
          strong
          style={{
            fontSize: 18,
            marginRight: 40,
            whiteSpace: 'nowrap',
          }}
        >
          规则引擎
        </Text>
        <Menu
          mode="horizontal"
          selectedKeys={[menuItems.find((item) => location.pathname.startsWith(item.key))?.key ?? '/rules']}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ flex: 1, border: 'none' }}
        />
        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
          <Space style={{ cursor: 'pointer', marginLeft: 16 }}>
            <Avatar size="small" icon={<UserOutlined />} />
            <Text>{user?.nickname || user?.username || '用户'}</Text>
          </Space>
        </Dropdown>
      </Header>
      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        <Outlet />
      </Content>
    </Layout>
    </App>
  );
}
