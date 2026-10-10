import type { MenuTreeNode, SysMenuDto, SysPermissionDto } from '@/types';

/**
 * 「权限点 → 所属页面」映射（权威源 = 菜单树）。
 *
 * <p>菜单树里 {@link SysMenuDto}-`MENU` 节点的 `permCode` 是该页面的**入口码**，
 * 其下 `BUTTON` 节点的 `permCode` 是页面内动作码——两者共同构成「这个权限码
 * 用在哪个页面」的权威关联。按页面分组比按 `module`（code 首段）分组更贴近
 * 管理员的心智模型：「给某个页面授权」而不是「给某个 module 授权」。</p>
 *
 * <p>未挂到任何菜单节点的码（平台 `sys:*` 接口闸门、兜底码等）归入
 * `module` 组——它们本来就是接口级权限，按 code 首段分组是最诚实的呈现。</p>
 */

/** 一个权限码的页面归属：所在页面 + 页面所在目录（可为 null，顶级菜单无目录）。 */
export interface PermPageRef {
  page: SysMenuDto;
  group: SysMenuDto | null;
}

/** 权限分组：`page` = 挂在菜单页下；`module` = 接口级/未挂页面。 */
export interface PermGroup {
  key: string;
  kind: 'page' | 'module';
  /** page 组 = 页面名 i18n 键；module 组 = code 首段（module 字面量）。 */
  label: string;
  perms: SysPermissionDto[];
}

/**
 * 遍历菜单树，构建 `permCode → { page, group }`。
 *
 * <p>DFS 过程中维护「当前目录 / 当前页面」；同一码出现多次时**先到先得**
 * （菜单树不允许同码重复挂载，此兜底只为容错）。</p>
 */
export function buildPermPageMap(tree: MenuTreeNode[]): Map<string, PermPageRef> {
  const map = new Map<string, PermPageRef>();
  let currentDir: SysMenuDto | null = null;
  let currentMenu: SysMenuDto | null = null;

  const visit = (nodes: MenuTreeNode[]): void => {
    for (const { menu, children } of nodes) {
      const prevDir = currentDir;
      const prevMenu = currentMenu;
      if (menu.type === 'DIR') currentDir = menu;
      else if (menu.type === 'MENU') currentMenu = menu;
      if (menu.permCode && !map.has(menu.permCode) && currentMenu) {
        map.set(menu.permCode, { page: currentMenu, group: menu.type === 'DIR' ? null : currentDir });
      }
      visit(children ?? []);
      currentDir = prevDir;
      currentMenu = prevMenu;
    }
  };
  visit(tree);
  return map;
}

/**
 * 把权限点按「所属页面」分组；未挂页面的按 `module` 归组排在后面。
 *
 * <p>page 组顺序 = 菜单树出现顺序（与导航一致，管理员按页面找码零学习成本）；
 * module 组按字母序。组内权限按 code 排序。</p>
 */
export function groupPermsByPage(
  perms: SysPermissionDto[],
  pageMap: Map<string, PermPageRef>,
): PermGroup[] {
  const groups = new Map<string, PermGroup>();
  const pageOrder: string[] = [];

  for (const p of perms) {
    const ref = pageMap.get(p.code);
    const key = ref ? `page:${ref.page.id}` : `mod:${p.module ?? p.code.split(':')[0] ?? 'other'}`;
    let g = groups.get(key);
    if (!g) {
      g = {
        key,
        kind: ref ? 'page' : 'module',
        label: ref ? (ref.page.i18nCode ?? ref.page.id) : (p.module ?? p.code.split(':')[0] ?? 'other'),
        perms: [],
      };
      groups.set(key, g);
      if (ref) pageOrder.push(key);
    }
    g.perms.push(p);
  }

  const out: PermGroup[] = [];
  for (const k of pageOrder) {
    const g = groups.get(k);
    if (g) {
      g.perms.sort((a, b) => a.code.localeCompare(b.code));
      out.push(g);
    }
  }
  const moduleGroups = [...groups.values()].filter((g) => g.kind === 'module');
  moduleGroups.sort((a, b) => a.label.localeCompare(b.label));
  for (const g of moduleGroups) g.perms.sort((a, b) => a.code.localeCompare(b.code));
  out.push(...moduleGroups);
  return out;
}
