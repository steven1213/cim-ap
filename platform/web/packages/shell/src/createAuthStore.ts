// 认证 store 工厂：`iam` / `rms` / `mds` 三份 `authStore.ts` 的差异只有「档案路径、
// 权限码路径、登出怎么发」，其余（含两条关键约定）逐行相同。
//
// 收在这里的**只有两条约定**，但两条都是「漏了就出生产问题」：
//
//   1. **`permissions` 只存内存、刻意不落盘**。权限随角色变更即时变化，落盘会让
//      「已撤权却仍看到按钮」——页面凭本机缓存的旧权限码放行，一直到点下去才被后端拒。
//      实现方式是 `partialize` 只保留令牌，每次启动由 `loadPermissions()` 重取。
//   2. **登出走 IdP 做服务端吊销**（RP-initiated logout）。只在本地清 localStorage
//      等于「用户以为退出了，令牌在有效期内仍然可用」——这是生产缺陷，不是体验问题。
//
// 为什么不把 `approval` 也纳进来：它有 `dev` / `sso` **双通道**（`mode`），还要把档案
// 同步进 `identityStore`（十余处页面从那里读「我是谁」）。硬塞进这个工厂会让参数表
// 长到没人看得懂，收益却不增加——它的 `authStore` 保留自写。
//
// ⚠️ 依赖方向：本工厂**不认识 axios**，HTTP 能力由应用注入（`client`）。这正是
// `@cim/data` 的 `createHttpClient` 产物，但这里只按结构化类型取两个方法，避免
// `@cim/shell` 反向依赖 `@cim/data`。
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** 可被吊销的会话凭据。 */
export interface AuthSession {
  token: string | null;
  refreshToken: string | null;
}

export interface AuthState<TMe> {
  token: string | null;
  refreshToken: string | null;
  user: TMe | null;
  /** 本 ap 权限码集（**内存态，不落盘**，见文件头第 1 条）。 */
  permissions: string[];
  /** 权限是否已拉取（用于区分「未加载」与「确实无权限」）。 */
  permissionsLoaded: boolean;
  setSession: (token: string, refreshToken: string) => void;
  loadMe: () => Promise<void>;
  loadPermissions: () => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

/**
 * 工厂参数。
 *
 * <p>刻意不在这里带 `TMe`：档案形状只影响 store 的 `user` 字段，与「怎么拉、怎么清」无关。
 * 档案类型由调用方在 `createAuthStore&lt;MeDto&gt;(...)` 上显式给出，这样 options 不必为了
 * 一个不参与运行时的类型参数而多一个占位字段。</p>
 */
export interface CreateAuthStoreOptions {
  /** 应用标识，落盘键为 `${name}-auth`（各 ap 前缀隔离，互不串味）。 */
  name: string;
  /** 业务 HTTP 客户端（`createHttpClient` 的产物，只需这两个方法）。 */
  client: {
    get<T>(url: string, params?: Record<string, unknown>): Promise<T>;
    rootGet<T>(url: string, params?: Record<string, unknown>): Promise<T>;
  };
  /** 当前用户档案路径，如 `/rms/me`。 */
  mePath: string;
  /** 档案走平台前缀（`/sys/me`）而非业务前缀。 */
  meOnPlatform?: boolean;
  /**
   * 权限码路径，如 `/rms/permissions`。
   *
   * <p>不传表示本 ap 没有权限码概念（approval 就属于这种），此时 `loadPermissions()`
   * 是空操作、`permissions` 恒为 `[]`。</p>
   */
  permissionsPath?: string;
  /** 权限码走平台前缀（IAM 的 `/sys/me/permissions`）。 */
  permissionsOnPlatform?: boolean;
  /**
   * 登出实现。缺省只清本地态。
   *
   * <p>返回 `false` 表示 IdP 未确认吊销（网络不可达 / CORS 未放行）：本地照清，但会
   * `console.warn` 留痕——否则「点了退出、令牌还有效」不会有任何人发现。</p>
   *
   * <p>⚠️ IAM 自己**不是** RP：它是 IdP，登出走自己的 `/api/v1/logout`，不走
   * `idpLogout`（那个会带上 `credentials: 'include'` 去清 SSO 会话 Cookie）。</p>
   */
  logout?: (session: AuthSession) => Promise<boolean | void>;
}

/**
 * 建一个认证 store。
 *
 * <pre>{@code
 * // app/src/store/authStore.ts —— 应用侧只剩这一段
 * export const useAuthStore = createAuthStore<MeDto>({
 *   name: 'mds',
 *   client: http,
 *   mePath: '/mds/me',
 *   permissionsPath: '/mds/permissions',
 *   logout: ({ token, refreshToken }) => sso.idpLogout(token, refreshToken),
 * });
 * }</pre>
 */
export function createAuthStore<TMe>(options: CreateAuthStoreOptions) {
  const { name, client, mePath, meOnPlatform, permissionsPath, permissionsOnPlatform, logout } = options;

  return create<AuthState<TMe>>()(
    persist(
      (set, get) => ({
        token: null,
        refreshToken: null,
        user: null,
        permissions: [],
        permissionsLoaded: false,

        setSession: (token, refreshToken) => set({ token, refreshToken }),

        loadMe: async () => {
          const me = meOnPlatform ? await client.rootGet<TMe>(mePath) : await client.get<TMe>(mePath);
          set({ user: me });
        },

        loadPermissions: async () => {
          if (!permissionsPath) return;
          const codes = permissionsOnPlatform
            ? await client.rootGet<string[]>(permissionsPath)
            : await client.get<string[]>(permissionsPath);
          set({ permissions: codes ?? [], permissionsLoaded: true });
        },

        logout: async () => {
          const { token, refreshToken } = get();
          if (logout) {
            const revoked = await logout({ token, refreshToken });
            if (revoked === false) {
              // 吊销未确认（IdP 不可达 / CORS 未放行）：仍清本地态，但留痕便于排查
              console.warn('[sso] IdP 登出未确认，已清本地会话；服务端令牌可能仍在有效期内');
            }
          }
          get().clear();
        },

        clear: () =>
          set({ token: null, refreshToken: null, user: null, permissions: [], permissionsLoaded: false }),
      }),
      {
        name: `${name}-auth`,
        // 只落盘令牌（权限、用户档案每次启动重取）——见文件头第 1 条
        partialize: (s) => ({ token: s.token, refreshToken: s.refreshToken }),
      },
    ),
  );
}
