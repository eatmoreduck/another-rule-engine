import type { ReactNode } from 'react';
import { usePermission } from '../hooks/usePermission';

interface AccessProps {
  /** 单个权限码 */
  permission?: string;
  /** 多个权限码（满足任一即可） */
  permissions?: string[];
  /** 角色检查 */
  role?: string;
  /** 无权限时的替代内容，默认不渲染 */
  fallback?: ReactNode;
  /** 子元素 */
  children: ReactNode;
}

/**
 * 权限控制组件
 * 根据用户权限决定是否渲染子元素
 *
 * 用法:
 * <Access permission="api:rules:create">
 *   <Button>新建规则</Button>
 * </Access>
 *
 * <Access permissions={["api:rules:update", "api:rules:delete"]} fallback={<Text>无操作权限</Text>}>
 *   <Space>...</Space>
 * </Access>
 */
export default function Access({ permission, permissions, role, fallback = null, children }: AccessProps) {
  const { hasPermission, hasAnyPermission, hasRole } = usePermission();

  // 角色检查
  if (role && !hasRole(role)) {
    return <>{fallback}</>;
  }

  // 单权限检查
  if (permission && !hasPermission(permission)) {
    return <>{fallback}</>;
  }

  // 多权限检查（满足任一即可）
  if (permissions && permissions.length > 0 && !hasAnyPermission(...permissions)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
