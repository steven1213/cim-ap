import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * dev 态身份（当前操作人）。
 *
 * <p>审批流平台后端当前为 dev 态（cim.auth.enabled=false），以 userId / tenantId 查询参数标识身份，
 * 因此前端用本 store 维护「当前我是谁」。生产接入 IAM 后，userId/tenantId 改为从 JWT 解析，
 * 这里应被 authStore 取代（参考 rms-ap 的 store/authStore.ts）。</p>
 */

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
  setIdentity: (userId: string, tenantId: string) => void;
  clear: () => void;
}

export const useIdentityStore = create<IdentityState>()(
  persist(
    (set) => ({
      userId: 'alice',
      tenantId: 'default',
      setIdentity: (userId, tenantId) => set({ userId, tenantId }),
      clear: () => set({ userId: 'alice', tenantId: 'default' }),
    }),
    { name: 'approval-identity' },
  ),
);
