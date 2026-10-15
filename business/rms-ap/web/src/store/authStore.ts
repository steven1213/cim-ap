// 认证状态（zustand + persist 落地到 localStorage，刷新不丢登录态）。
//
// ⚠️ `permissions` **只存内存、刻意不落盘**（见 iam 同款考量）：权限随角色变更即时变化，
// 若落盘会出现「本机缓存的旧权限」被当作真值。每次启动由 `loadPermissions()` 重新拉取。
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as api from '@/lib/api';
import { idpLogout } from '@/lib/sso';
import type { MeDto } from '@/types';

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: MeDto | null;
  /** 本 ap 权限码集（内存态，不落盘）。 */
  permissions: string[];
  permissionsLoaded: boolean;
  setSession: (token: string, refreshToken: string) => void;
  loadMe: () => Promise<void>;
  /** 拉取本 ap 权限码集（/api/v1/rms/permissions）。 */
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
        const me = await api.get<MeDto>('/rms/me');
        set({ user: me });
      },
      loadPermissions: async () => {
        const codes = await api.get<string[]>('/rms/permissions');
        set({ permissions: codes ?? [], permissionsLoaded: true });
      },
      logout: async () => {
        // 走 IdP（IAM）做**服务端吊销**：令牌入黑名单 + 清 SSO 会话 Cookie。
        // 不能只在本地清 localStorage——那样令牌在有效期内依旧可用（生产缺陷）。
        const { token, refreshToken } = get();
        const revoked = await idpLogout(token, refreshToken);
        if (!revoked) {
          // 吊销未确认（IdP 不可达 / CORS 未放行）：仍清本地态，但留痕便于排查
          console.warn('[sso] IdP 登出未确认，已清本地会话；服务端令牌可能仍在有效期内');
        }
        get().clear();
      },
      clear: () =>
        set({ token: null, refreshToken: null, user: null, permissions: [], permissionsLoaded: false }),
    }),
    {
      name: 'rms-auth',
      // 只落盘令牌（权限、用户档案每次启动重取）
      partialize: (s) => ({ token: s.token, refreshToken: s.refreshToken }),
    },
  ),
);
