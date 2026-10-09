// 渲染契约测试（无需真浏览器）：把四个配置管理页当作 React 组件渲染成静态 HTML，
// 断言「在给定权限集下，页面渲染出哪些元素、请求了哪些文案键」。
//
// 为什么需要它：W4 的四页是**双闸门**渲染（IAM 控制台码决定入口 ∧ 平台码决定数据面），
// 单靠「tsc 通过 + 接口级 e2e」证明不了「无权限时**不挂载**写按钮」——
// 接口级只能验证后端拒绝，验证不了前端有没有把入口画出来。
//
// 三条设计取舍：
// 1. **零浏览器**：用 `react-dom/server` 的 `renderToStaticMarkup`，不装 jsdom/Playwright/RTL。
//    代价是 `useEffect` 不执行 → 页面停在「未加载」首屏，因此本测试断言的是
//    **闸门 + 骨架 + 文案键**，而不是数据渲染（数据渲染由接口级 e2e + CDP 视觉回归覆盖）。
// 2. **文案用「键标记」而非中文**：i18next 初始化时不灌入任何资源，缺失处理器把键
//    包成 `⟪key⟫` 返回。于是断言 `⟪iam.admin.menus.tree⟫` 出现的等价语义是
//    「这里请求了 iam.admin.menus.tree 这条键」——比断言中文更稳（改文案不会误伤），
//    也顺带验证了「键名没写错」。
// 3. **权限直接改真实 store**：`useAuthStore.setState` + `getServerState` 覆写
//    （zustand 4 在 SSR 下默认读 `getInitialState`，不覆写的话 setState 对渲染不可见）。
import './setup';

import assert from 'node:assert/strict';
import { createElement, type ComponentType } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import { useAuthStore } from '@/store/authStore';
import {
  I18N_LIST,
  MENU_CREATE,
  MENU_DELETE,
  MENU_LIST,
  PERM_CREATE,
  PERM_LIST,
  ROLE_ADMIN_CREATE,
  ROLE_ADMIN_DELETE,
  ROLE_ADMIN_LIST,
  SYS,
} from '@/lib/permCodes';
import MenusAdminPage from '@/pages/MenusAdminPage';
import PermissionsAdminPage from '@/pages/PermissionsAdminPage';
import RolesAdminPage from '@/pages/RolesAdminPage';
import I18nAdminPage from '@/pages/I18nAdminPage';

// ---------------------------------------------------------------- 运行环境

/** 缺失文案键的渲染标记（见文件头「设计取舍 2」）。 */
const missing = (key: string) => `⟪${key}⟫`;

/**
 * 把「当前权限码集」置入 zustand store，使 SSR 渲染可见。
 *
 * <p>为什么要动初始快照：zustand 4 的 `useStore` 用
 * `api.getServerState || api.getInitialState` 作为 `getServerSnapshot`，
 * 而 `getInitialState` 返回的是**创建时捕获的那个对象**；`setState` 只会替换
 * `state` 的引用（`Object.assign({}, state, partial)`），不会碰初始对象。
 * 于是只调 `setState` 时，`renderToStaticMarkup` 读到的权限**永远是空数组**
 * （表现：所有页面都被判成无权限）。这里同时做两件事：</p>
 * <ol>
 *   <li>就地改写初始快照 → 供 SSR 的 `getServerSnapshot` 读取；</li>
 *   <li>正常 `setState` → 保持 store 自身语义一致（客户端路径同样正确）。</li>
 * </ol>
 */
function withPermissions(permissions: string[]): void {
  const initial = useAuthStore.getInitialState() as unknown as {
    permissions: string[];
    permissionsLoaded: boolean;
  };
  initial.permissions = permissions;
  initial.permissionsLoaded = true;
  useAuthStore.setState({ permissions, permissionsLoaded: true });
}

/** 以「当前权限码集」渲染一个页面，返回其 HTML。 */
function render(page: ComponentType, permissions: string[]): string {
  withPermissions(permissions);
  return renderToStaticMarkup(createElement(page));
}

// ---------------------------------------------------------------- 断言小工具

let passed = 0;
const failures: string[] = [];

function check(name: string, fn: () => void): void {
  try {
    fn();
    passed += 1;
    console.log(`  \u2713 ${name}`);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    failures.push(`${name}\n      ${message.split('\n').join('\n      ')}`);
    console.log(`  \u2717 ${name}\n      ${message.split('\n').join('\n      ')}`);
  }
}

/** 断言 HTML 含某段文本。 */
function has(html: string, needle: string): void {
  assert.ok(html.includes(needle), `期望渲染结果包含 ${needle}`);
}

/** 断言 HTML 不含某段文本（用于「无权限时**不挂载**」）。 */
function hasNot(html: string, needle: string): void {
  assert.ok(!html.includes(needle), `期望渲染结果**不**包含 ${needle}`);
}

/** 断言渲染的是「无权限提示」而非页面主体。 */
function expectNoticeOnly(html: string, noticeKey: string, bodyMarker: string): void {
  has(html, missing(noticeKey));
  hasNot(html, missing(bodyMarker));
}

/**
 * 面板头「＋」按钮的精确标记。
 *
 * <p>必须精确到 `title` 属性：同一文案键往往还被空态提示复用
 * （如权限页右侧空态 `<b>新增权限</b>` 与面板头 ＋ 按钮同为 `iam.admin.perms.new`），
 * 只按文案断言会把「按钮已挂载」误判成「按钮不存在」。</p>
 */
const iconButton = (titleKey: string) => `<button class="icon-btn" title="${missing(titleKey)}"`;

// ---------------------------------------------------------------- 测试主体

async function main(): Promise<void> {
  // 不灌资源包：所有 `t(key)` 都走缺失处理器 → `⟪key⟫`，断言即「请求了这个键」。
  await i18n.use(initReactI18next).init({
    lng: 'zh-CN',
    fallbackLng: 'zh-CN',
    defaultNS: 'translation',
    ns: ['translation'],
    resources: { 'zh-CN': { translation: {} } },
    initImmediate: false,
    saveMissing: false,
    parseMissingKeyHandler: (key: string) => missing(key),
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
    returnNull: false,
  });

  console.log('\n[渲染契约] 菜单管理页 MenusAdminPage（双闸门）');

  check('无任何权限 → 只渲染提示，不渲染主体', () => {
    expectNoticeOnly(render(MenusAdminPage, []), 'iam.common.noPlatformPerm',
      'iam.admin.menus.tree');
  });

  check('只持控制台码 iam:menu:list → 仍只渲染提示（平台码缺失，双闸门拦住）', () => {
    const html = render(MenusAdminPage, [MENU_LIST]);
    expectNoticeOnly(html, 'iam.common.noPlatformPerm', 'iam.admin.menus.tree');
  });

  check('只持平台码 sys:menu:list → 仍只渲染提示（控制台码缺失）', () => {
    const html = render(MenusAdminPage, [SYS.MENU_LIST]);
    expectNoticeOnly(html, 'iam.common.noPlatformPerm', 'iam.admin.menus.tree');
  });

  check('双码齐备 → 渲染树面板与右侧占位，且请求正确文案键', () => {
    const html = render(MenusAdminPage, [MENU_LIST, SYS.MENU_LIST]);
    has(html, missing('iam.admin.menus.tree'));
    has(html, missing('iam.admin.menus.nodeCount'));
    has(html, missing('iam.admin.menus.selectNode'));
    has(html, missing('iam.common.refresh'));
    has(html, 'class="split"');
    // 首屏无数据（useEffect 未执行）：渲染**空态**而非空树——这是有意的兜底，不是缺陷
    has(html, 'class="empty"');
    hasNot(html, 'class="tree"');
  });

  check('只读（无 create/delete 码）→ 新增入口**不挂载**', () => {
    const html = render(MenusAdminPage, [MENU_LIST, MENU_DELETE, SYS.MENU_LIST]);
    // 删除按钮在「未选中节点」时不渲染，故只断言新增入口
    hasNot(html, iconButton('iam.admin.menus.newRoot'));
  });

  check('写入码齐备（IAM ∧ 平台）→ 新增入口挂载', () => {
    const html = render(MenusAdminPage,
      [MENU_LIST, MENU_CREATE, SYS.MENU_LIST, SYS.MENU_SAVE]);
    has(html, iconButton('iam.admin.menus.newRoot'));
  });

  check('只有 IAM 写码、缺平台码 → 新增入口仍**不挂载**（双闸门）', () => {
    const html = render(MenusAdminPage, [MENU_LIST, MENU_CREATE, SYS.MENU_LIST]);
    hasNot(html, iconButton('iam.admin.menus.newRoot'));
  });

  console.log('\n[渲染契约] 权限管理页 PermissionsAdminPage（双闸门）');

  check('无任何权限 → 只渲染提示', () => {
    expectNoticeOnly(render(PermissionsAdminPage, []), 'iam.common.noPlatformPerm',
      'iam.admin.perms.count');
  });

  check('只持 iam:perm:list（缺 sys:permission:list）→ 只渲染提示', () => {
    const html = render(PermissionsAdminPage, [PERM_LIST]);
    expectNoticeOnly(html, 'iam.common.noPlatformPerm', 'iam.admin.perms.count');
  });

  check('双码齐备 → 渲染计数面板与检索框', () => {
    const html = render(PermissionsAdminPage, [PERM_LIST, SYS.PERM_LIST]);
    has(html, missing('iam.admin.perms.count'));
    has(html, `placeholder="${missing('iam.common.search')}"`);
    has(html, missing('iam.common.none'));
  });

  check('无 create 码 → 新增按钮不挂载（含空态文案，故按按钮精确断言）', () => {
    const html = render(PermissionsAdminPage, [PERM_LIST, SYS.PERM_LIST]);
    hasNot(html, iconButton('iam.admin.perms.new'));
  });

  check('create 双码齐备 → 新增按钮挂载', () => {
    const html = render(PermissionsAdminPage,
      [PERM_LIST, PERM_CREATE, SYS.PERM_LIST, SYS.PERM_SAVE]);
    has(html, iconButton('iam.admin.perms.new'));
  });

  console.log('\n[渲染契约] 角色管理页 RolesAdminPage（双闸门）');

  check('无任何权限 → 只渲染提示', () => {
    expectNoticeOnly(render(RolesAdminPage, []), 'iam.common.noPlatformPerm',
      'iam.admin.roles.list');
  });

  check('只持 iam:role-admin:list（缺 sys:role:list）→ 只渲染提示', () => {
    const html = render(RolesAdminPage, [ROLE_ADMIN_LIST]);
    expectNoticeOnly(html, 'iam.common.noPlatformPerm', 'iam.admin.roles.list');
  });

  check('双码齐备 → 渲染角色列表与右侧占位', () => {
    const html = render(RolesAdminPage, [ROLE_ADMIN_LIST, SYS.ROLE_LIST]);
    has(html, missing('iam.admin.roles.list'));
    has(html, missing('iam.admin.roles.selectRole'));
    has(html, 'class="split"');
  });

  check('无 create 码 → 新建角色按钮不挂载（授权两表因未选中角色本也不渲染）', () => {
    const html = render(RolesAdminPage,
      [ROLE_ADMIN_LIST, ROLE_ADMIN_DELETE, SYS.ROLE_LIST, SYS.ROLE_REMOVE]);
    hasNot(html, iconButton('iam.admin.roles.new'));
    hasNot(html, missing('iam.admin.roles.permGrant'));
    hasNot(html, missing('iam.admin.roles.menuGrant'));
  });

  check('create 双码齐备 → 新建角色按钮挂载', () => {
    const html = render(RolesAdminPage,
      [ROLE_ADMIN_LIST, ROLE_ADMIN_CREATE, SYS.ROLE_LIST, SYS.ROLE_SAVE]);
    has(html, iconButton('iam.admin.roles.new'));
  });

  console.log('\n[渲染契约] 多语言页 I18nAdminPage（**单闸门**）');

  check('无任何权限 → 只渲染提示（提示键与三个配置页不同）', () => {
    expectNoticeOnly(render(I18nAdminPage, []), 'iam.common.forbidden.desc',
      'iam.admin.i18n.locales');
  });

  check('只持 iam:i18n:list、**完全不含平台码** → 正常渲染三块面板', () => {
    // 这是「单闸门」的契约：本页数据面是 IAM 自己的 `/api/v1/admin/i18n/**`，
    // 只认 `iam:i18n:*`，不需要任何 `sys:*`。若哪天被误改成走平台端点，本断言会失败。
    const html = render(I18nAdminPage, [I18N_LIST]);
    has(html, missing('iam.admin.i18n.locales'));
    has(html, missing('iam.admin.i18n.messages'));
    has(html, missing('iam.i18n.missing'));
    has(html, missing('iam.common.refresh'));
  });

  check('只持平台码 sys:menu:list → 只渲染提示（平台码对 i18n 页无意义）', () => {
    const html = render(I18nAdminPage, [SYS.MENU_LIST, SYS.PERM_LIST, SYS.ROLE_LIST]);
    expectNoticeOnly(html, 'iam.common.forbidden.desc', 'iam.admin.i18n.locales');
  });

  check('i18n 写码缺失（仅 list）→ 新建语言/新增译文的按钮不挂载', () => {
    const html = render(I18nAdminPage, [I18N_LIST]);
    hasNot(html, missing('iam.admin.i18n.newLocale'));
    hasNot(html, missing('iam.admin.i18n.newMessage'));
  });

  // ------------------------------------------------------------ 汇总

  const total = passed + failures.length;
  console.log(`\n渲染契约测试：${passed}/${total} 通过`);
  if (failures.length > 0) {
    console.log('\n失败项：');
    failures.forEach((f, i) => console.log(`  ${i + 1}. ${f}`));
    process.exit(1);
  }
}

main().catch((e) => {
  console.error('[渲染契约] 运行失败：', e);
  process.exit(1);
});
