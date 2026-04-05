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
    key: '/system',
    icon: <SettingOutlined />,
    label: '系统管理',
    children: [
      {
        key: '/system/users',
        icon: <UserOutlined />,
        label: '用户管理',
      },
      {
        key: '/system/roles',
        icon: <SafetyOutlined />,
        label: '角色管理',
      },
    ],
  },
];

/**
 * 根据当前路径匹配菜单 key
 * 支持子菜单路径匹配（如 /system/users 匹配到 /system/users）
 */
function getSelectedMenuKey(pathname: string): string {
  // 优先匹配具体路径（子菜单项）
  for (const item of menuItems) {
    if (item.children) {
      for (const child of item.children) {
        if (pathname.startsWith(child.key)) {
          return child.key;
        }
      }
    } else if (pathname.startsWith(item.key)) {
      return item.key;
    }
  }
  return '/rules';
}

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
          selectedKeys={[getSelectedMenuKey(location.pathname)]}
          openKeys={location.pathname.startsWith('/system') ? ['/system'] : undefined}
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
