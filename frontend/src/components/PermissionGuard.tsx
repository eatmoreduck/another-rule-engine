import type { ReactNode } from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { usePermission } from '../hooks/usePermission';

interface PermissionGuardProps {
  /** 需要的权限码 */
  permission?: string;
  /** 需要的角色 */
  role?: string;
  children: ReactNode;
}

/**
 * 路由级别权限守卫
 * 无权限时显示 403 页面
 */
export default function PermissionGuard({ permission, role, children }: PermissionGuardProps) {
  const { hasPermission, hasRole } = usePermission();
  const navigate = useNavigate();

  if (role && !hasRole(role)) {
    return (
      <Result
        status="403"
        title="无访问权限"
        subTitle="您没有权限访问此页面，请联系管理员"
        extra={
          <Button type="primary" onClick={() => navigate(-1)}>
            返回上一页
          </Button>
        }
      />
    );
  }

  if (permission && !hasPermission(permission)) {
    return (
      <Result
        status="403"
        title="无访问权限"
        subTitle="您没有权限访问此页面，请联系管理员"
        extra={
          <Button type="primary" onClick={() => navigate(-1)}>
            返回上一页
          </Button>
        }
      />
    );
  }

  return <>{children}</>;
}
