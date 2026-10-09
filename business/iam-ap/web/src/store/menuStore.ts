// 菜单 store：登录后从后端拉取**当前用户可见的菜单树**（不再前端硬编码注册表）。
//
// <p>数据源 `GET /sys/me/menus`（平台 `cim-system`）：后端已按角色（`sys_role_menu`）过滤，
// 因此前端**不做任何权限过滤**——「侧栏显示什么」完全由库决定，改菜单不发版。</p>
//
// <p>不落盘：菜单随授权即时变化，且体积不大，每次启动重取最稳（与 `authStore.permissions` 同理）。</p>
import { create } from 'zustand';
import * as api from '@/lib/api';
import type { MenuTreeNode, SysMenuDto } from '@/types';

interface MenuState {
  tree: MenuTreeNode[];
  /** 扁平的可路由菜单（`type=MENU` 且有 `path`），供动态路由与标题映射。 */
  flat: SysMenuDto[];
  loaded: boolean;
  loading: boolean;
  error: string | null;
  load: (force?: boolean) => Promise<void>;
  clear: () => void;
}

/** 深度优先扁平化（保留后端顺序）。 */
function flatten(nodes: MenuTreeNode[]): SysMenuDto[] {
  return nodes.flatMap((n) => [n.menu, ...flatten(n.children ?? [])]);
}

export const useMenuStore = create<MenuState>()((set, get) => ({
  tree: [],
  flat: [],
  loaded: false,
  loading: false,
  error: null,
  load: async (force = false) => {
    if (get().loading) return;
    if (get().loaded && !force) return;
    set({ loading: true, error: null });
    try {
      const tree = await api.rootGet<MenuTreeNode[]>('/sys/me/menus');
      const all = flatten(tree ?? []);
      // 按 path 去重：管理端可能配置出重复路由，React Router 对重复 path 会告警/错配
      const routed = all.filter((m) => m.type === 'MENU' && !!m.path);
      const seen = new Set<string>();
      const flat = routed.filter((m) => {
        if (seen.has(m.path as string)) return false;
        seen.add(m.path as string);
        return true;
      });
      set({ tree: tree ?? [], flat, loaded: true, loading: false });
    } catch (e: any) {
      set({ loading: false, error: e?.message ?? '菜单加载失败' });
      throw e;
    }
  },
  clear: () => set({ tree: [], flat: [], loaded: false, loading: false, error: null }),
}));

/** 按路径找菜单（页头标题/面包屑）。 */
export function findMenuByPath(flat: SysMenuDto[], path: string): SysMenuDto | undefined {
  return flat.find((m) => m.path === path);
}
