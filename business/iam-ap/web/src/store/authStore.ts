// 认证状态（zustand + persist 落地到 localStorage，刷新不丢登录态）。
//
// ⚠️ `permissions` **只存内存、刻意不落盘**（见 `partialize`）：权限随角色变更即时变化，
// 若落盘会出现「本机缓存的旧权限」在新会话里被当作真值——典型表现是「已撤权却仍看到按钮」。
// 每次启动由 `loadPermissions()` 重新拉取（`/sys/me/permissions` 每请求回显当前授权，天然最新）。
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as api from '@/lib/api';
import type { MeDto } from '@/types';

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: MeDto | null;
  /** 本 ap 权限码集（内存态，不落盘）。 */
  permissions: string[];
  /** 权限是否已拉取（用于区分「未加载」与「确实无权限」）。 */
  permissionsLoaded: boolean;
  setSession: (token: string, refreshToken: string) => void;
  loadMe: () => Promise<void>;
  /** 拉取本 ap 权限码集（平台端点 `/sys/me/permissions`）。 */
  loadPermissions: () => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      user: null,
      permissions: [],
      permissionsLoaded: false,
      setSession: (token, refreshToken) => set({ token, refreshToken }),
      loadMe: async () => {
        const me = await api.get<MeDto>('/me');
        set({ user: me });
      },
      loadPermissions: async () => {
        const codes = await api.rootGet<string[]>('/sys/me/permissions');
        set({ permissions: codes ?? [], permissionsLoaded: true });
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
      clear: () => set({ token: null, refreshToken: null, user: null, permissions: [], permissionsLoaded: false }),
    }),
    {
      name: 'iam-auth',
      // 只落盘令牌（权限、用户档案每次启动重取）
      partialize: (s) => ({ token: s.token, refreshToken: s.refreshToken }),
    },
  ),
);
