// 页面注册表：后端 `sys_menu.component`（字符串）→ 页面组件。
//
// <p>菜单入库后，前端不再有「路由表」这个硬编码清单——路由由 `GET /sys/me/menus` 下发，
// 组件由本表按 `component` 标识解析（与 `iconRegistry` 同构：后端存标识、前端存实现）。</p>
//
// <p>未知标识回退到 {@link NotFoundPage} 而不是抛错：管理端新增一个前端还没有实现的菜单时，
// 应当「路由可达但提示未实现」，而不是整页崩溃。</p>
import { lazy, type ComponentType, type LazyExoticComponent } from 'react';

// 页面一律动态 import：控制台路由由后端菜单下发，登录后先到 Dashboard，
// 其余 15 个管理页在首次进入时才拉代码（静态 import 会把它们全塞进首屏 chunk）。

/** 未知标识的回退页保持同步加载：它出现的场景（菜单里有、前端没实现）不需要为它多等一次往返。 */
import ComingSoon from './ComingSoon';
import NotFoundPage from './NotFoundPage';

/** `component` 标识 → 页面组件。键与 `IamConsoleCatalog` 里 `MenuDef.component` 一一对应。 */
export const PAGE_REGISTRY: Record<string, LazyExoticComponent<ComponentType>> = {
  Overview: lazy(() => import('./DashboardPage')),
  Orgs: lazy(() => import('./OrgPage')),
  Users: lazy(() => import('./UsersPage')),
  Lockouts: lazy(() => import('./LockoutsPage')),
  Apps: lazy(() => import('./AppMgmtPage')),
  Admissions: lazy(() => import('./AdmissionsPage')),
  OrgGrants: lazy(() => import('./OrgGrantsPage')),
  Roles: lazy(() => import('./RolesPage')),
  Sessions: lazy(() => import('./SessionsPage')),
  Audit: lazy(() => import('./AuditPage')),
  Settings: lazy(() => import('./SettingsPage')),
  Profile: lazy(() => import('./ProfilePage')),
  MenusAdmin: lazy(() => import('./MenusAdminPage')),
  PermissionsAdmin: lazy(() => import('./PermissionsAdminPage')),
  RolesAdmin: lazy(() => import('./RolesAdminPage')),
  I18nAdmin: lazy(() => import('./I18nAdminPage')),
};

/** 解析页面组件；标识缺失/未知时回退（缺失路径的提示页）。 */
export function resolvePage(component: string | null | undefined): ComponentType {
  if (component && PAGE_REGISTRY[component]) return PAGE_REGISTRY[component];
  return component ? ComingSoon : NotFoundPage;
}

/** 注册表全部键（供菜单管理页做组件选择器）。 */
export function componentKeys(): string[] {
  return Object.keys(PAGE_REGISTRY).sort();
}
