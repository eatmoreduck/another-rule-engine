import { Layout, Menu, Typography } from 'antd';
import { SafetyOutlined, SettingOutlined, ExperimentOutlined, BugOutlined, BarChartOutlined, DashboardOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';

const { Header, Content } = Layout;
const { Text } = Typography;

const menuItems = [
  {
    key: '/rules',
    icon: <SafetyOutlined />,
    label: '规则管理',
  },
  {
    key: '/grayscale',
    icon: <ExperimentOutlined />,
    label: '灰度发布',
  },
  {
    key: '/monitoring',
    icon: <DashboardOutlined />,
    label: '监控仪表盘',
  },
  {
    key: '/test',
    icon: <BugOutlined />,
    label: '测试验证',
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

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  return (
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
      </Header>
      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
