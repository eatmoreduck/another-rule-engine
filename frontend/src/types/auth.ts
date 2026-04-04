export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  nickname: string;
  roles: string[];
}

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  email: string | null;
  phone: string | null;
  roles: string[];
  permissions: string[];
}
