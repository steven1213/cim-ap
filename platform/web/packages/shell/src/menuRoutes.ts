// 菜单驱动的动态路由工厂。
//
// 职责：把「后端菜单树（按角色过滤过的）」翻译成 react-router 的路由元素——
// 每个 MENU 节点一条路由，组件按两级解析：
//
//   1. `menu.component` 显式指定（维护页可配，最可靠）——与 pages 注册表的
//      basename（去目录去扩展名）精确比对；
//   2. 命名约定兜底：`/reason-codes` → `ReasonCodesPage`（kebab→Pascal + Page）。
//
// 约定解析是「提示性」的：单复数差异（/wip-events → WipEventPage）靠第三级
// 规范化模糊匹配（去 Page 尾、去尾 s）唯一命中时才用；零命中或多命中一律
// 跳过该路由并 console.warn——宁可 404 也不要猜错组件白屏。
//
// pages 注册表由应用侧传入 `import.meta.glob('@/pages/*.tsx')` 的结果：
// Vite 只做静态分析，动态 import 路径不可能完全运行时化，glob 是构建期
// 把「目录里所有页面」一次性注册的标准做法；新增页面文件即自动入表。
import { lazy, type ComponentType } from 'react';
import type { CimMenuNode } from './createAuthStore';

/** 页面注册表：`import.meta.glob` 的产物（value 是动态 import loader）。 */
export type PageRegistry = Record<string, () => Promise<unknown>>;

/** 动态路由条目（应用侧 map 成 `<Route>`）。 */
export interface MenuRoute {
  /** 菜单 path（绝对路径，如 `/reason-codes`）。 */
  path: string;
  /** lazy 化的页面组件。 */
  Component: ComponentType;
  /** 来源菜单节点（permCode 供路由守卫二次门禁）。 */
  menu: CimMenuNode;
}

/** kebab/snake → PascalCase：`/reason-codes` → `ReasonCodes`。 */
function pascal(path: string): string {
  return path
    .replace(/^\//, '')
    .split(/[-_/]/)
    .filter(Boolean)
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join('');
}

/** 注册表 key 的组件名：`@/pages/ReasonCodePage.tsx` → `ReasonCodePage`。 */
function baseName(key: string): string {
  const file = key.split('/').pop() ?? key;
  return file.replace(/\.(tsx|jsx)$/, '');
}

/** 规范化比较串：小写、去非字母、去 `Page` 尾、去复数尾 `s`。 */
function norm(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]/g, '').replace(/page$/, '').replace(/s$/, '');
}

export interface CreateMenuRoutesOptions {
  /** 组件解析失败时渲染的兜底页；缺省给个极简占位（应用一般传自己的 404）。 */
  NotFound?: ComponentType;
}

/**
 * 菜单树 → 动态路由条目。
 *
 * <p>只取 `type === 'MENU'`、有 `path`、`visible` 且启用的节点；DIR 只分组、
 * BUTTON 是授权粒度不产生路由。菜单来自后端已按角色过滤，permCode 在这里
 * 仍随条目返回——应用要用路由级门禁可以再包一层（纵深防御，可选）。</p>
 */
export function createMenuRoutes(
  menus: CimMenuNode[],
  pages: PageRegistry,
  options: CreateMenuRoutesOptions = {},
): MenuRoute[] {
  const keys = Object.keys(pages);
  const routes: MenuRoute[] = [];

  const walk = (nodes: CimMenuNode[]): void => {
    for (const menu of nodes) {
      if (menu.type === 'MENU' && menu.path && menu.visible && menu.status === 'ENABLED') {
        const cmp = resolveComponent(menu, keys);
        if (cmp) {
          const Component = lazy(async () => {
            const mod = (await pages[cmp]()) as { default?: ComponentType } | ComponentType;
            const C = (mod as { default?: ComponentType }).default ?? (mod as ComponentType);
            return { default: C };
          });
          routes.push({ path: menu.path, Component, menu });
        } else {
          console.warn(
            `[menu-routes] 菜单 ${menu.path} 找不到组件（component=${menu.component ?? '未指定'}），已跳过`,
          );
        }
      }
      if (menu.children?.length) walk(menu.children);
    }
  };
  walk(menus);

  void options; // NotFound 预留：由应用在 Routes 尾部统一放 `path="*"`
  return routes;
}

/** 解析菜单对应的注册表 key（返回 key 本身，未命中返回 null）。 */
function resolveComponent(menu: CimMenuNode, keys: string[]): string | null {
  const byName = (name: string): string | null => keys.find((k) => baseName(k) === name) ?? null;

  // 1. 显式 component（Catalog/维护页指定，最优先）
  if (menu.component) {
    const hit = byName(menu.component);
    if (hit) return hit;
  }

  // 2. 命名约定：/reason-codes → ReasonCodesPage
  const conventional = byName(`${pascal(menu.path!)}Page`) ?? byName(pascal(menu.path!));
  if (conventional) return conventional;

  // 3. 规范化模糊匹配（单复数容忍），要求唯一命中
  const target = norm(pascal(menu.path!));
  const fuzzy = keys.filter((k) => norm(baseName(k)) === target);
  return fuzzy.length === 1 ? fuzzy[0] : null;
}
