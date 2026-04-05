import { useMemo } from 'react';
import { Layout, Menu, Typography, App, Dropdown, Avatar, Space } from 'antd';
import { SafetyOutlined, SettingOutlined, ExperimentOutlined, BarChartOutlined, DashboardOutlined, CloudServerOutlined, ImportOutlined, ApartmentOutlined, UnorderedListOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import { usePermission } from '../hooks/usePermission';

const { Header, Content } = Layout;
const { Text } = Typography;

interface MenuItemConfig {
  key: string;
  icon?: React.ReactNode;
  label: string;
  permission?: string;
  children?: MenuItemConfig[];
}

/** 全量菜单配置（含权限码），渲染前根据用户权限过滤 */
const allMenuItems: MenuItemConfig[] = [
  {
    key: '/rules',
    icon: <SafetyOutlined />,
    label: '规则配置',
    permission: 'menu:rules',
  },
  {
    key: '/decision-flows',
    icon: <ApartmentOutlined />,
    label: '决策流',
    permission: 'menu:decision-flows',
  },
  {
    key: '/name-list',
    icon: <UnorderedListOutlined />,
    label: '名单管理',
    permission: 'menu:name-list',
  },
  {
    key: '/grayscale',
    icon: <ExperimentOutlined />,
    label: '灰度发布',
    permission: 'menu:grayscale',
  },
  {
    key: '/environments',
    icon: <CloudServerOutlined />,
    label: '多环境',
    permission: 'menu:environments',
  },
  {
    key: '/import-export',
    icon: <ImportOutlined />,
    label: '导入导出',
    permission: 'menu:import-export',
  },
  {
    key: '/monitoring',
    icon: <DashboardOutlined />,
    label: '监控仪表盘',
    permission: 'menu:monitoring',
  },
  {
    key: '/analytics',
    icon: <BarChartOutlined />,
    label: '分析中心',
    permission: 'menu:analytics',
  },
  {
    key: '/system',
    icon: <SettingOutlined />,
    label: '系统管理',
    permission: 'menu:settings',
    children: [
      {
        key: '/system/users',
        icon: <UserOutlined />,
        label: '用户管理',
        permission: 'menu:settings',
      },
      {
        key: '/system/roles',
        icon: <SafetyOutlined />,
        label: '角色管理',
        permission: 'menu:settings',
      },
    ],
  },
];

/** 将 MenuItemConfig[] 转换为 Ant Design Menu items 格式（去除 permission 字段） */
function toAntdItems(items: MenuItemConfig[]): NonNullable<Parameters<typeof Menu>[0]['items']> {
  return items.map(({ key, icon, label, children }) => {
    const result: Record<string, unknown> = { key, label };
    if (icon) result.icon = icon;
    if (children) result.children = toAntdItems(children);
    return result;
  });
}

/**
 * 根据当前路径匹配菜单 key
 * 支持子菜单路径匹配（如 /system/users 匹配到 /system/users）
 */
function getSelectedMenuKey(pathname: string, items: MenuItemConfig[]): string {
  for (const item of items) {
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
  const { hasPermission, isSuperAdmin } = usePermission();

  /** 根据用户权限过滤菜单项 */
  const menuItems = useMemo(() => {
    const filterItems = (items: MenuItemConfig[]): MenuItemConfig[] => {
      return items
        .filter((item) => {
          // 无权限码的菜单项始终显示
          if (!item.permission) return true;
          // 有权限码的菜单项需要检查权限
          return isSuperAdmin || hasPermission(item.permission);
        })
        .map((item) => {
          if (item.children) {
            const filteredChildren = filterItems(item.children);
            // 如果子菜单全部被过滤掉，则隐藏整个父菜单
            if (filteredChildren.length === 0) return null;
            return { ...item, children: filteredChildren };
          }
          return item;
        })
        .filter(Boolean) as MenuItemConfig[];
    };
    return filterItems(allMenuItems);
  }, [hasPermission, isSuperAdmin]);

  /** 转换为 Ant Design Menu 可用的 items 格式 */
  const antdMenuItems = useMemo(() => toAntdItems(menuItems), [menuItems]);

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
          selectedKeys={[getSelectedMenuKey(location.pathname, menuItems)]}
          openKeys={location.pathname.startsWith('/system') ? ['/system'] : undefined}
          items={antdMenuItems}
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
