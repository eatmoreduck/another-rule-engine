import apiClient from './client';

// ==================== 类型定义 ====================

export interface RoleDTO {
  id: number;
  roleCode: string;
  roleName: string;
  description: string | null;
  status: string;
  permissionCodes: string[];
}

export interface UserDTO {
  id: number;
  username: string;
  nickname: string | null;
  email: string | null;
  phone: string | null;
  status: string;
  roles: RoleDTO[];
  createdAt: string;
}

export interface PermissionDTO {
  id: number;
  permissionCode: string;
  permissionName: string;
  resourceType: string;
  resourcePath: string | null;
  method: string | null;
  parentId: number | null;
  sortOrder: number;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  phone?: string;
  roleIds?: number[];
}

export interface UpdateUserRequest {
  nickname?: string;
  email?: string;
  phone?: string;
  roleIds?: number[];
  status?: string;
}

export interface UpdateRolePermissionsRequest {
  permissionIds: number[];
}

// ==================== 用户管理 API ====================

export async function listUsers(): Promise<UserDTO[]> {
  const { data } = await apiClient.get<UserDTO[]>('/api/v1/system/users');
  return data;
}

export async function createUser(request: CreateUserRequest): Promise<UserDTO> {
  const { data } = await apiClient.post<UserDTO>('/api/v1/system/users', request);
  return data;
}

export async function updateUser(id: number, request: UpdateUserRequest): Promise<UserDTO> {
  const { data } = await apiClient.put<UserDTO>(`/api/v1/system/users/${id}`, request);
  return data;
}

export async function updateUserStatus(id: number, status: string): Promise<UserDTO> {
  const { data } = await apiClient.put<UserDTO>(`/api/v1/system/users/${id}/status`, { status });
  return data;
}

export async function resetPassword(id: number): Promise<void> {
  await apiClient.put(`/api/v1/system/users/${id}/reset-password`);
}

// ==================== 角色管理 API ====================

export async function listRoles(): Promise<RoleDTO[]> {
  const { data } = await apiClient.get<RoleDTO[]>('/api/v1/system/roles');
  return data;
}

export async function updateRolePermissions(roleId: number, request: UpdateRolePermissionsRequest): Promise<RoleDTO> {
  const { data } = await apiClient.put<RoleDTO>(`/api/v1/system/roles/${roleId}/permissions`, request);
  return data;
}

// ==================== 权限管理 API ====================

export async function listPermissions(): Promise<PermissionDTO[]> {
  const { data } = await apiClient.get<PermissionDTO[]>('/api/v1/system/permissions');
  return data;
}

// ==================== 审计日志 API ====================

export interface AuditLogDTO {
  id: number;
  entityType: string;
  entityId: string;
  operation: string;
  operationDetail: string | null;
  operator: string;
  operatorIp: string | null;
  operationTime: string;
  status: string;
  errorMessage: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditLogQueryParams {
  operator?: string;
  entityType?: string;
  operation?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export async function queryAuditLogs(params: AuditLogQueryParams): Promise<PageResponse<AuditLogDTO>> {
  const { data } = await apiClient.get<PageResponse<AuditLogDTO>>('/api/v1/audit/logs', { params });
  return data;
}
