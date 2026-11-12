import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * 当前操作人（身份 + 业务域）。
 *
 * <p>两条通道都由后端 `GET /api/v1/approval/me` 的 `authEnabled` 决定，
 * 而本 store 在两种通道下都持有「当前我是谁」（统一登录时由 `authStore.hydrateIdentity` 写入）：</p>
 * <ul>
 *   <li>统一登录：userId 取自 JWT，`tenantId` 取自令牌（为空则回落共享域）；顶栏不再提供切换器；</li>
 *   <li>dev 态：以 userId / tenantId 查询参数标识身份，顶栏保留切换器。</li>
 * </ul>
 *
 * <p>⚠️ `tenantId` 是**业务域**参数，不是「随便填的租户」：后端 `SecurityGuard` 会校验
 * 请求域是否落在「令牌 apps ∪ 令牌 tenantId ∪ 共享域」内，越域一律 403。
 * 所以 dev 态下切到未授权的域不会看到别人的数据，只会拿到 403。</p>
 */

/** 平台共享域，与后端 `SecurityGuard.SHARED_DOMAIN` 同一取值（改一处必须改两处）。 */
export const SHARED_DOMAIN = 'default';

// 演示/测试用的预置用户（覆盖各流程定义的办理角色）。
export const PRESET_USERS = [
  'alice',
  'bob',
  'carol',
  'dave',
  'leader',
  'manager',
  'init',
] as const;

interface IdentityState {
  userId: string;
  tenantId: string;
  /** 当前用户角色集合（G6 / D8）：用于命中角色型候选组；dev 态随身份切换，生产由 JWT roles claim 注入。 */
  roles: string[];
  setIdentity: (userId: string, tenantId: string, roles?: string[]) => void;
  setRoles: (roles: string[]) => void;
  clear: () => void;
}

// dev 演示用预置角色：默认用户 alice 具有 manager 角色，便于在 UI 直接体验角色型候选命中
export const DEFAULT_ROLES = ['manager'];

export const useIdentityStore = create<IdentityState>()(
  persist(
    (set) => ({
      userId: 'alice',
      tenantId: SHARED_DOMAIN,
      roles: DEFAULT_ROLES,
      setIdentity: (userId, tenantId, roles) => set({ userId, tenantId, roles: roles ?? DEFAULT_ROLES }),
      setRoles: (roles) => set({ roles }),
      clear: () => set({ userId: 'alice', tenantId: SHARED_DOMAIN, roles: DEFAULT_ROLES }),
    }),
    { name: 'approval-identity' },
  ),
);
