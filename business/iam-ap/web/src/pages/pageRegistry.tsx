// 页面注册表：后端 `sys_menu.component`（字符串）→ 页面组件。
//
// <p>菜单入库后，前端不再有「路由表」这个硬编码清单——路由由 `GET /sys/me/menus` 下发，
// 组件由本表按 `component` 标识解析（与 `iconRegistry` 同构：后端存标识、前端存实现）。</p>
//
// <p>未知标识回退到 {@link NotFoundPage} 而不是抛错：管理端新增一个前端还没有实现的菜单时，
// 应当「路由可达但提示未实现」，而不是整页崩溃。</p>
import type { ComponentType } from 'react';
import DashboardPage from './DashboardPage';
import OrgPage from './OrgPage';
import UsersPage from './UsersPage';
import LockoutsPage from './LockoutsPage';
import AppMgmtPage from './AppMgmtPage';
import AdmissionsPage from './AdmissionsPage';
import OrgGrantsPage from './OrgGrantsPage';
import RolesPage from './RolesPage';
import SessionsPage from './SessionsPage';
import AuditPage from './AuditPage';
import SettingsPage from './SettingsPage';
import ProfilePage from './ProfilePage';
import MenusAdminPage from './MenusAdminPage';
import PermissionsAdminPage from './PermissionsAdminPage';
import RolesAdminPage from './RolesAdminPage';
import I18nAdminPage from './I18nAdminPage';
import ComingSoon from './ComingSoon';
import NotFoundPage from './NotFoundPage';

/** `component` 标识 → 页面组件。键与 `IamConsoleCatalog` 里 `MenuDef.component` 一一对应。 */
export const PAGE_REGISTRY: Record<string, ComponentType> = {
  Overview: DashboardPage,
  Orgs: OrgPage,
  Users: UsersPage,
  Lockouts: LockoutsPage,
  Apps: AppMgmtPage,
  Admissions: AdmissionsPage,
  OrgGrants: OrgGrantsPage,
  Roles: RolesPage,
  Sessions: SessionsPage,
  Audit: AuditPage,
  Settings: SettingsPage,
  Profile: ProfilePage,
  MenusAdmin: MenusAdminPage,
  PermissionsAdmin: PermissionsAdminPage,
  RolesAdmin: RolesAdminPage,
  I18nAdmin: I18nAdminPage,
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
