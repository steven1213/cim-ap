// @cim/admin —— 系统配置六页共享组件包。
//
// 宿主用法：
//   1. 根组件外层包一次 <AdminProvider http={api} hasAll={...}>；
//   2. 合并内置译文（adminZhCN/adminEnUS）进 i18n（远端包可逐键覆盖，键前缀 admin.*）；
//   3. 路由组件树中渲染六个页面。菜单/权限/角色/多语言四页端点即平台契约；
//      组织/人员页 IAM 传缺省端点（全 CRUD），业务系统传 readOnly + 目录代理前缀。
// 判权全部经 useAdminHasAll（AdminProvider 注入），不依赖宿主权限上下文组件。
export { AdminProvider, useAdminHttp, useAdminHasAll, type AdminHttp, type AdminHasAll } from './context';
export type { AdminPermOverrides } from './perms';
export {
  MENU_PERMS,
  PERM_PERMS,
  ROLE_PERMS,
  I18N_PERMS,
  ORG_PERMS,
  USER_PERMS,
  IAM_CREATE_PERMS,
} from './perms';
export { default as MenusAdminPage, type MenusAdminPageProps, type MenuIcons } from './pages/MenusAdminPage';
export { default as PermissionsAdminPage, type PermissionsAdminPageProps } from './pages/PermissionsAdminPage';
export { default as RolesAdminPage, type RolesAdminPageProps } from './pages/RolesAdminPage';
export { default as I18nAdminPage, type I18nAdminPageProps } from './pages/I18nAdminPage';
export { default as OrgPage, type OrgPageProps } from './pages/OrgPage';
export { default as UsersPage, type UsersPageProps } from './pages/UsersPage';
export { default as PermTree, type PermTreeProps, type PermTreeGroup } from './PermTree';
export { buildPermPageMap, groupPermsByPage, type PermPageRef, type PermGroup } from './permPageMap';
export { resolveIcon, iconKeys, IconFallback, type IconComponent } from './iconRegistry';
export { orgTypeLabel, originLabel, fmtDateTime, fmtRelative } from './format';
export { PBKDF2_DEFAULT_ROUNDS, randomClientSalt, deriveClientHash } from './crypto';
export type * from './types';
export { default as adminZhCN } from './resources/zh-CN';
export { default as adminEnUS } from './resources/en-US';
