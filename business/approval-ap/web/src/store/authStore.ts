// 认证状态（令牌 + 身份通道）。身份（userId/tenantId/roles）仍落在 `identityStore`：
// 全应用十余处页面都从那里读「我是谁」，此处只负责把**它从哪来**这件事讲清楚，避免每页改一遍。
//
// 两条通道由后端 `GET /api/v1/approval/me` 的 `authEnabled` 决定：
// - `sso`：`cim.auth.enabled=true`。身份取自 JWT（`/me` 的档案），无令牌即中继到 IAM 登录页；
// - `dev`：`cim.auth.enabled=false`。无 IAM 联调，身份由查询参数表达，顶栏保留身份切换器。
//
// ⚠️ 通道开关**不落盘**：它是后端配置的事实，不是一个会话属性。落盘会让「把后端切成
//    auth=true」的部署在用户浏览器里继续走 dev 通道，直接绕过登录。
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as api from '@/lib/api';
import { idpLogout } from '@/lib/sso';
import { useIdentityStore } from '@/store/identityStore';
import type { MeDto } from '@/types';

/** 身份通道。`unknown` 仅存在于启动探针返回前。 */
export type AuthMode = 'unknown' | 'dev' | 'sso';

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  mode: AuthMode;
  /** 统一登录下的用户档案；dev 通道恒为 null（身份在 identityStore）。 */
  user: MeDto | null;
  /** 启动探针：判定通道，并在已登录时带回档案。由 `main.tsx` 在挂载前 await。 */
  bootstrap: () => Promise<void>;
  setSession: (token: string, refreshToken: string) => void;
  /** 拉取档案并把身份写入 `identityStore`（供 `/sso` 回调后、BootGate 使用）。 */
  loadMe: () => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      mode: 'unknown',
      user: null,

      bootstrap: async () => {
        try {
          const me = await api.fetchMe();
          if (me && me.authEnabled === false) {
            // dev 通道：身份继续由 identityStore 的持久化值 + 顶栏切换器表达
            set({ mode: 'dev', user: null });
            return;
          }
          set({ mode: 'sso', user: me ?? null });
          if (me) {
            hydrateIdentity(me);
          } else if (get().token) {
            // 落盘的令牌已被服务端拒绝（过期/吊销）：清掉，否则首帧会凭它误放行，
            // 每个请求再各自 401 一次。
            get().clear();
          }
        } catch (err) {
          // 后端不可达：按「已启用认证」处理，让用户落到登录中继页看到明确提示，
          // 而不是把应用当成已登录、随后每个请求都失败。
          console.error('[auth] 认证探针失败', err);
          set({ mode: 'sso' });
        }
      },

      setSession: (token, refreshToken) => set({ token, refreshToken }),

      loadMe: async () => {
        const me = await api.getMe();
        if (!me) return;
        set({ user: me, mode: 'sso' });
        hydrateIdentity(me);
      },

      logout: async () => {
        const { token, refreshToken, mode } = get();
        if (mode === 'sso') {
          // 走 IdP 做服务端吊销：令牌入黑名单 + 清 SSO 会话 Cookie。
          const revoked = await idpLogout(token, refreshToken);
          if (!revoked) {
            console.warn('[sso] IdP 登出未确认，已清本地会话；服务端令牌可能仍在有效期内');
          }
        }
        get().clear();
      },

      clear: () => set({ token: null, refreshToken: null, user: null }),
    }),
    {
      name: 'approval-auth',
      // 只落盘令牌；通道与档案每次启动重取（见文件头 ⚠️）
      partialize: (s) => ({ token: s.token, refreshToken: s.refreshToken }),
    },
  ),
);

/**
 * 把 `me` 写进 `identityStore`。
 *
 * <p>`tenantId` 缺省回落 `default`：审批流的租户是**流程变量**，存量实例都以 `default` 起，
 * 令牌里 `tenantId` 为空时若原样传 null，用户会「登录成功但一条待办都看不到」。</p>
 */
function hydrateIdentity(me: MeDto): void {
  useIdentityStore
    .getState()
    .setIdentity(me.userId ?? '', me.tenantId || 'default', me.roles ?? []);
}
