// 认证状态（zustand + persist 落地到 localStorage，刷新不丢登录态）。
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as api from '@/lib/api';
import type { MeDto } from '@/types';

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: MeDto | null;
  setSession: (token: string, refreshToken: string) => void;
  loadMe: () => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      user: null,
      setSession: (token, refreshToken) => set({ token, refreshToken }),
      loadMe: async () => {
        const me = await api.get<MeDto>('/me');
        set({ user: me });
      },
      logout: async () => {
        const rt = get().refreshToken;
        try {
          await api.post<void>('/logout', rt ? { refreshToken: rt } : undefined);
        } catch {
          // 登出接口失败也直接前端清态
        }
        get().clear();
      },
      clear: () => set({ token: null, refreshToken: null, user: null }),
    }),
    { name: 'iam-auth' },
  ),
);
