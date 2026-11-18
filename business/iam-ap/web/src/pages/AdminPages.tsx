// 六页系统配置的宿主接线：页面实现在共享包 `@cim/admin`，这里注入 IAM 适配器。
//
// 注入三件事：
//   1. http：IAM 的 httpClient（baseURL 已含 /api/v1）；
//   2. hasAll：authStore 权限码集上的全含判断；
//   3. perms：菜单/权限/角色/多语言四页的**双闸门**覆盖——入口码（iam:menu:* 等，
//      决定页面与按钮显示）∧ 平台码（sys:*，数据面 /sys/** 端点鉴权）。
//      组织/人员页用共享包缺省码（iam:org:* / iam:user:*，即 IAM 自己的码），无需覆盖。
// 页面组件与视觉全部来自共享包；本文件不含任何页面实现（旧六页已删除，不做兼容）。
import { useCallback, type ReactNode } from 'react';
import {
  AdminProvider,
  I18nAdminPage,
  MenusAdminPage,
  OrgPage,
  PermissionsAdminPage,
  RolesAdminPage,
  UsersPage,
  type AdminHasAll,
  type AdminPermOverrides,
} from '@cim/admin';
import { httpClient } from '@/lib/api';
import { usePermissions } from '@/lib/usePermission';
import {
  I18N_DELETE,
  I18N_LIST,
  I18N_LOCALE,
  I18N_UPDATE,
  MENU_DELETE,
  MENU_LIST,
  MENU_UPDATE,
  PERM_DELETE,
  PERM_LIST,
  PERM_UPDATE,
  ROLE_ADMIN_DELETE,
  ROLE_ADMIN_GRANT,
  ROLE_ADMIN_LIST,
  ROLE_ADMIN_UPDATE,
  SYS,
} from '@/lib/permCodes';
import { changeLanguage, currentLang } from '@/lib/i18n';

/** 各页双闸门覆盖。 */
const MENU_PERMS: AdminPermOverrides = {
  read: [MENU_LIST, SYS.MENU_LIST],
  // 共享页的 save 同时覆盖新建与编辑；IAM 入口侧建/改分码（MENU_CREATE/MENU_UPDATE），
  // 双闸门取编辑码（MENU_UPDATE）守门——超管全码不受影响，细分角色按种子成对持有。
  save: [MENU_UPDATE, SYS.MENU_SAVE],
  remove: [MENU_DELETE, SYS.MENU_REMOVE],
};

const PERM_PERMS: AdminPermOverrides = {
  read: [PERM_LIST, SYS.PERM_LIST],
  save: [PERM_UPDATE, SYS.PERM_SAVE],
  remove: [PERM_DELETE, SYS.PERM_REMOVE],
};

const ROLE_PERMS: AdminPermOverrides = {
  read: [ROLE_ADMIN_LIST, SYS.ROLE_LIST],
  save: [ROLE_ADMIN_UPDATE, SYS.ROLE_SAVE],
  remove: [ROLE_ADMIN_DELETE, SYS.ROLE_REMOVE],
  grant: [ROLE_ADMIN_GRANT, SYS.ROLE_GRANT],
};

const I18N_PERMS: AdminPermOverrides = {
  read: [I18N_LIST],
  save: [I18N_UPDATE],
  remove: [I18N_DELETE],
  locale: [I18N_LOCALE],
};

/**
 * 组装宿主适配器：包 <AdminProvider>（http + hasAll），node 工厂可拿到 hasAll。
 * 权限码集快照经 usePermissions 订阅，授权变更即时反映到按钮显隐。
 */
function wired(render: (hasAll: AdminHasAll) => ReactNode) {
  return function AdminWired() {
    const codes = usePermissions();
    const hasAll = useCallback((cs: string[]) => cs.every((c) => codes.includes(c)), [codes]);
    return <AdminProvider http={httpClient} hasAll={hasAll}>{render(hasAll)}</AdminProvider>;
  };
}

/** 菜单管理（共享页）。 */
export const MenusAdmin = wired(() => <MenusAdminPage perms={MENU_PERMS} />);

/** 权限管理（共享页）。 */
export const PermissionsAdmin = wired(() => <PermissionsAdminPage perms={PERM_PERMS} />);

/** 角色管理（共享页）。 */
export const RolesAdmin = wired(() => <RolesAdminPage perms={ROLE_PERMS} />);

/** 多语言（共享页；写库即缓存失效，「刷新译文」重拉当前语言整包）。 */
export const I18nAdmin = wired(() => (
  <I18nAdminPage perms={I18N_PERMS} reloadTranslations={() => void changeLanguage(currentLang(), true)} />
));

/** 组织架构（共享页；IAM 全 CRUD，缺省端点 + 缺省 iam:org:* 码）。 */
export const Orgs = wired(() => <OrgPage />);

/** 人员与档案（共享页；IAM 全 CRUD，缺省端点 + 缺省 iam:user:* 码）。 */
export const Users = wired(() => <UsersPage />);
