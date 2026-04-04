import { create } from 'zustand';
import type { UserInfo } from '../types/auth';
import * as authApi from '../api/auth';

interface AuthStore {
  token: string | null;
  user: UserInfo | null;
  loading: boolean;
  initialized: boolean;

  init: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  isAuthenticated: () => boolean;
}

const TOKEN_KEY = 'auth_token';

export const useAuthStore = create<AuthStore>((set, get) => ({
  token: localStorage.getItem(TOKEN_KEY),
  user: null,
  loading: false,
  initialized: false,

  init: async () => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      set({ initialized: true, token: null, user: null });
      return;
    }

    try {
      const user = await authApi.getCurrentUser();
      set({ token, user, initialized: true });
    } catch {
      // Token invalid, clear it
      localStorage.removeItem(TOKEN_KEY);
      set({ token: null, user: null, initialized: true });
    }
  },

  login: async (username: string, password: string) => {
    set({ loading: true });
    try {
      const response = await authApi.login({ username, password });
      localStorage.setItem(TOKEN_KEY, response.token);
      // Fetch full user info
      const user = await authApi.getCurrentUser();
      set({ token: response.token, user, loading: false });
    } catch (error) {
      set({ loading: false });
      throw error;
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      // Ignore logout API errors
    } finally {
      localStorage.removeItem(TOKEN_KEY);
      set({ token: null, user: null });
    }
  },

  isAuthenticated: () => {
    return !!get().token;
  },
}));
