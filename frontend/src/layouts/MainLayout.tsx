import { useMemo, useState, useEffect } from 'react';
import { Layout, Menu, Typography, App, Dropdown, Avatar, Space } from 'antd';
import { SafetyOutlined, SettingOutlined, ExperimentOutlined, BarChartOutlined, DashboardOutlined, CloudServerOutlined, ImportOutlined, ApartmentOutlined, UnorderedListOutlined, UserOutlined, LogoutOutlined, FileSearchOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores/authStore';
import { usePermission } from '../hooks/usePermission';
import { getFeatureFlags, type FeatureFlags } from '../api/features';
import LanguageSwitcher from '../components/LanguageSwitcher';

const { Header, Content } = Layout;
const { Text } = Typography;

interface MenuItemConfig {
  key: string;
  icon?: React.ReactNode;
  labelKey: string;
  permission?: string;
  feature?: keyof FeatureFlags;
  children?: MenuItemConfig[];
}

/** 全量菜单配置（含权限码），渲染前根据用户权限过滤 */
const allMenuItems: MenuItemConfig[] = [
  {
    key: '/rules',
    icon: <SafetyOutlined />,
    labelKey: 'menu.rules',
    permission: 'menu:rules',
  },
  {
    key: '/decision-flows',
    icon: <ApartmentOutlined />,
    labelKey: 'menu.decisionFlows',
    permission: 'menu:decision-flows',
  },
  {
    key: '/name-list',
    icon: <UnorderedListOutlined />,
    labelKey: 'menu.nameList',
    permission: 'menu:name-list',
  },
  {
    key: '/grayscale',
    icon: <ExperimentOutlined />,
    labelKey: 'menu.grayscale',
    permission: 'menu:grayscale',
  },
  {
    key: '/environments',
    icon: <CloudServerOutlined />,
    labelKey: 'menu.environments',
    permission: 'menu:environments',
    feature: 'multiEnvironment',
  },
  {
    key: '/import-export',
    icon: <ImportOutlined />,
    labelKey: 'menu.importExport',
    permission: 'menu:import-export',
    feature: 'importExport',
  },
  {
    key: '/monitoring',
    icon: <DashboardOutlined />,
    labelKey: 'menu.monitoring',
    permission: 'menu:monitoring',
  },
  {
    key: '/analytics',
    icon: <BarChartOutlined />,
    labelKey: 'menu.analytics',
    permission: 'menu:analytics',
  },
  {
    key: '/system',
    icon: <SettingOutlined />,
    labelKey: 'menu.system',
    permission: 'menu:settings',
    children: [
      {
        key: '/system/users',
        icon: <UserOutlined />,
        labelKey: 'menu.userManagement',
        permission: 'menu:settings',
      },
      {
        key: '/system/roles',
        icon: <SafetyOutlined />,
        labelKey: 'menu.roleManagement',
        permission: 'menu:settings',
      },
      {
        key: '/system/audit',
        icon: <FileSearchOutlined />,
        labelKey: 'menu.auditLog',
        permission: 'menu:settings',
      },
    ],
  },
];

/** 将 MenuItemConfig[] 转换为 Ant Design Menu items 格式（去除 permission 字段） */
function toAntdItems(items: MenuItemConfig[], t: (key: string) => string): NonNullable<Parameters<typeof Menu>[0]['items']> {
  return items.map(({ key, icon, labelKey, children }) => {
    const result: Record<string, unknown> = { key, label: t(labelKey) };
    if (icon) result.icon = icon;
    if (children) result.children = toAntdItems(children, t);
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
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { modal } = App.useApp();
  const { hasPermission, isSuperAdmin } = usePermission();
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [features, setFeatures] = useState<FeatureFlags>({ multiEnvironment: false, importExport: false });

  useEffect(() => {
    getFeatureFlags().then(setFeatures);
  }, []);

  /** 根据用户权限过滤菜单项 */
  const menuItems = useMemo(() => {
    const filterItems = (items: MenuItemConfig[]): MenuItemConfig[] => {
      return items
        .filter((item) => {
          // 功能开关检查
          if (item.feature && !features[item.feature]) return false;
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
  }, [hasPermission, isSuperAdmin, features]);

  /** 转换为 Ant Design Menu 可用的 items 格式 */
  const antdMenuItems = useMemo(() => toAntdItems(menuItems, t), [menuItems, t]);

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
    setOpenKeys([]); // 点击菜单项后收起子菜单
  };

  const handleLogout = async () => {
    modal.confirm({
      title: t('user.logoutTitle'),
      content: t('user.logoutConfirm'),
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
      label: t('user.logout'),
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
          {t('app.title')}
        </Text>
        <Menu
          mode="horizontal"
          selectedKeys={[getSelectedMenuKey(location.pathname, menuItems)]}
          openKeys={openKeys}
          onOpenChange={setOpenKeys}
          items={antdMenuItems}
          onClick={handleMenuClick}
          style={{ flex: 1, border: 'none' }}
        />
        <LanguageSwitcher />
        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
          <Space style={{ cursor: 'pointer', marginLeft: 16 }}>
            <Avatar size="small" icon={<UserOutlined />} />
            <Text>{user?.nickname || user?.username || t('user.defaultName')}</Text>
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
