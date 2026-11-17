// RMS 的认证 store：实现在 `@cim/shell` 的 `createAuthStore`，本文件只声明属于 RMS 的四件事。
//
// ⚠️ `permissions` 只存内存、刻意不落盘（工厂的 `partialize` 已保证）：权限随角色变更即时
//    变化，落盘会出现「已撤权却仍看到按钮」——页面凭本机缓存的旧权限码放行，点下去才被后端拒。
//
// ⚠️ 登出走 IdP 做**服务端吊销**：只在本地清 localStorage 的话，令牌在有效期内仍然可用。
import { createAuthStore } from '@cim/shell';
import { httpClient } from '@/lib/api';
import { sso } from '@/lib/sso';
import type { MeDto } from '@/types';

export const useAuthStore = createAuthStore<MeDto>({
  name: 'rms',
  client: httpClient,
  mePath: '/rms/me',
  permissionsPath: '/rms/permissions',
  // ⚠️ 动态菜单暂不启用：RmsConsoleCatalog（平面 4 组）与前端 navConfig（3 组 7 模块）
  //    的信息架构未对齐，硬切会整体变形。对齐后加 `menusPath: '/sys/me/menus'` 即可
  //    （Layout/App 按 MDS 同构模式切换，能力已在 @cim/shell 就绪）。
  logout: ({ token, refreshToken }) => sso.idpLogout(token, refreshToken),
});
