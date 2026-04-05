import { useAuthStore } from '../stores/authStore';

/**
 * 权限判断 Hook
 * 提供权限码检查、角色检查等工具方法
 * SUPER_ADMIN 角色自动拥有所有权限
 */
export function usePermission() {
  const user = useAuthStore((s) => s.user);

  const permissions = user?.permissions ?? [];
  const roles = user?.roles ?? [];
  const isSuperAdmin = roles.includes('SUPER_ADMIN') || roles.includes('ADMIN');

  /**
   * 检查是否拥有指定权限码
   * 超级管理员自动放行
   */
  function hasPermission(code: string): boolean {
    if (isSuperAdmin) return true;
    return permissions.includes(code);
  }

  /**
   * 检查是否拥有任一权限码
   */
  function hasAnyPermission(...codes: string[]): boolean {
    if (isSuperAdmin) return true;
    return codes.some((code) => permissions.includes(code));
  }

  /**
   * 检查是否拥有指定角色
   */
  function hasRole(role: string): boolean {
    return roles.includes(role);
  }

  /**
   * 检查是否拥有全部权限码
   */
  function hasAllPermissions(...codes: string[]): boolean {
    if (isSuperAdmin) return true;
    return codes.every((code) => permissions.includes(code));
  }

  return {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    isSuperAdmin,
    permissions,
    roles,
  };
}
