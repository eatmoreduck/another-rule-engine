import apiClient from './client';
import type { LoginRequest, LoginResponse, UserInfo } from '../types/auth';

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/v1/auth/login', request);
  return data;
}

export async function logout(): Promise<void> {
  await apiClient.post('/api/v1/auth/logout');
}

export async function getCurrentUser(): Promise<UserInfo> {
  const { data } = await apiClient.get<UserInfo>('/api/v1/auth/me');
  return data;
}

export async function checkLogin(): Promise<{ loggedIn: boolean; userId: number }> {
  const { data } = await apiClient.get<{ loggedIn: boolean; userId: number }>('/api/v1/auth/check');
  return data;
}
