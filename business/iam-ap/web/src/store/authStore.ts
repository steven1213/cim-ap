// IAM 的认证 store：实现在 `@cim/shell` 的 `createAuthStore`，本文件只声明属于 IAM 的五件事。
//
// ⚠️ `permissions` 只存内存、刻意不落盘（工厂的 `partialize` 已保证）：权限随角色变更即时
//    变化，落盘会出现「已撤权却仍看到按钮」——页面凭本机缓存的旧权限码放行，点下去才被后端拒。
//
// ⚠️ 与另外三端不同：**IAM 自己是 IdP**，登出走自己的 `/api/v1/logout`（吊销令牌 + 清
//    SSO 会话 Cookie），不走 `idpLogout` —— 那条路径是给外部 RP 用的，在 IAM 里等于自己
//    fetch 自己，还要多绕一次 CORS。
import { createAuthStore } from '@cim/shell';
import { httpClient, post } from '@/lib/api';
import type { MeDto } from '@/types';

export const useAuthStore = createAuthStore<MeDto>({
  name: 'iam',
  client: httpClient,
  mePath: '/me',
  // 权限码走平台前缀（`cim-system` 的 `/sys/me/permissions` 每请求回显当前授权，天然最新）
  permissionsPath: '/sys/me/permissions',
  permissionsOnPlatform: true,
  logout: async ({ refreshToken }) => {
    try {
      await post<void>('/logout', refreshToken ? { refreshToken } : undefined);
    } catch {
      // 登出接口失败也直接前端清态
    }
  },
});
