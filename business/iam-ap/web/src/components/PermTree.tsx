import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { MenuTreeNode, SysPermissionDto } from '@/types';
import { IconChevron } from '@/components/Icons';

/**
 * 权限点树（权限管理 / 角色授权共用）——与菜单树**同构**的 DIR → MENU → BUTTON 三层树。
 *
 * <p>设计目标（用户反馈驱动）：授权/清单视图不能只平铺叶子码，必须完整呈现菜单树的
 * 层级——目录可折叠、页面行挂入口码、按钮码嵌在页面之下，用缩进引导线
 * （`.ptree-kids` 左边线）表达从属关系。</p>
 *
 * <p>行内为五列网格：折叠钮 / 复选框 / 名称 / 权限码 / 动作。码列固定弹性宽度，
 * 同一列垂直对齐；名称列吃掉剩余空间。两种模式：</p>
 * <ul>
 *   <li><b>授权模式</b>（角色管理）：传 `checkedPerms`/`onTogglePerm`——页面行有
 *   菜单可见 + 入口码两个复选框，按钮码逐行复选；`pageAction` 渲染页面级快捷键
 *   （「全选」）。超管时全部禁用（超管短路拥有全部码）。</li>
 *   <li><b>清单模式</b>（权限管理）：传 `permById`——行点击进入编辑，`activePermId`
 *   高亮当前编辑行；不渲染复选框。</li>
 * </ul>
 *
 * <p>码在权限表无对应行（菜单树挂了码但未注册）→ 行灰显、复选框禁用并打
 * 「未注册」标。检索过滤由调用方完成（传修剪后的 `nodes`）；`kw` 非空时树强制展开。</p>
 */
/** 接口级/未挂页面分组（渲染在菜单树节点之后，同一棵树内）。 */
export interface PermTreeGroup {
  key: string;
  /** 组名（module 字面量或已翻译文本）。 */
  label: string;
  /** 打「接口级」标。 */
  iface?: boolean;
  perms: SysPermissionDto[];
}

export interface PermTreeProps {
  nodes: MenuTreeNode[];
  /** code → 权限行 ID；未注册的码灰显不可勾。 */
  permIdByCode: Map<string, string>;
  /** 清单模式：ID → 权限行（提供即切换为清单模式）。 */
  permById?: Map<string, SysPermissionDto>;
  /** 清单模式：当前编辑的权限行 ID（高亮）。 */
  activePermId?: string;
  /** 清单模式：行点击选取权限进入编辑。 */
  onPermClick?: (perm: SysPermissionDto) => void;
  /** 授权模式：已勾选权限行 ID 集合。 */
  checkedPerms?: Set<string>;
  /** 授权模式：勾选/取消某个权限码。 */
  onTogglePerm?: (permId: string, on: boolean) => void;
  /** 授权模式：已勾选菜单（侧栏可见）ID 集合。 */
  checkedMenus?: Set<string>;
  /** 授权模式：勾选/取消菜单可见。 */
  onToggleMenu?: (menuId: string, on: boolean) => void;
  /** 超管：拥有全部启用码，勾选全部禁用。 */
  superMode?: boolean;
  /** 页面行右侧动作区（如「全选」整页快捷）。 */
  pageAction?: (node: MenuTreeNode) => ReactNode;
  /** 末尾追加的分组（未挂页面的接口级码等），与菜单树同一棵树渲染。 */
  groups?: PermTreeGroup[];
  /** 分组行右侧动作（如「全选」整组快捷）。 */
  groupAction?: (group: PermTreeGroup) => ReactNode;
  /** 检索词非空 → 强制展开全部层级（过滤由调用方做）。 */
  kw?: string;
}

/** 切换集合成员（不可变更新）。 */
function toggleIn(set: Set<string>, id: string): Set<string> {
  const next = new Set(set);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

export default function PermTree({
  nodes,
  permIdByCode,
  permById,
  activePermId,
  onPermClick,
  checkedPerms,
  onTogglePerm,
  checkedMenus,
  onToggleMenu,
  superMode,
  pageAction,
  groups,
  groupAction,
  kw,
}: PermTreeProps) {
  const { t } = useTranslation();
  const [closedDirs, setClosedDirs] = useState<Set<string>>(new Set());
  const [closedPages, setClosedPages] = useState<Set<string>>(new Set());
  const [closedGroups, setClosedGroups] = useState<Set<string>>(new Set());
  const expandAll = (kw ?? '').trim().length > 0;
  const grantMode = !permById;
  const unregisteredTip = t('iam.admin.perms.unregistered');

  /** 子树统计（授权模式：已授/总数；清单模式只看总数）。 */
  function statsOf(n: MenuTreeNode): { on: number; total: number } {
    let on = 0;
    let total = 0;
    if (n.menu.type === 'MENU' && n.menu.permCode) {
      const id = permIdByCode.get(n.menu.permCode);
      if (id) {
        total += 1;
        if (checkedPerms?.has(id)) on += 1;
      }
    }
    for (const c of n.children ?? []) {
      if (c.menu.type === 'BUTTON' && c.menu.permCode) {
        const id = permIdByCode.get(c.menu.permCode);
        if (id) {
          total += 1;
          if (checkedPerms?.has(id)) on += 1;
        }
      } else {
        const s = statsOf(c);
        on += s.on;
        total += s.total;
      }
    }
    return { on, total };
  }

  /** 通用权限叶行：授权模式勾选 / 清单模式点击进编辑；`id` 缺失 = 未注册灰显。 */
  function permRow(opts: {
    key: string;
    id?: string;
    code: string;
    label: string;
    perm?: SysPermissionDto;
  }): ReactNode {
    const { key, id, code, label, perm } = opts;
    const active = id !== undefined && activePermId === id;
    const clickable = !grantMode && perm && onPermClick;
    const cls = [
      'ptree-row',
      'is-leaf',
      active ? 'on' : '',
      id === undefined ? 'is-off' : '',
      clickable ? 'clickable' : '',
    ]
      .filter(Boolean)
      .join(' ');
    return (
      <div
        key={key}
        className={cls}
        title={id === undefined ? unregisteredTip : undefined}
        onClick={clickable ? () => onPermClick?.(perm!) : undefined}
      >
        <span className="tree-toggle hollow" />
        {grantMode ? (
          <input
            type="checkbox"
            checked={id !== undefined && (checkedPerms?.has(id) ?? false)}
            disabled={superMode || id === undefined}
            onChange={() => {
              if (id !== undefined) onTogglePerm?.(id, !checkedPerms?.has(id));
            }}
          />
        ) : (
          <span />
        )}
        <span className="ptree-name" title={label}>
          {label}
        </span>
        {code ? (
          <span className="tree-code dim mono">{code}</span>
        ) : (
          <span />
        )}
        {!grantMode && perm?.status === 'DISABLED' ? (
          <span className="tag err">{t('iam.common.disabled')}</span>
        ) : (
          <span />
        )}
      </div>
    );
  }

  /** BUTTON 叶行（挂菜单树节点上；码未注册 → 灰显禁用）。 */
  function renderLeaf(n: MenuTreeNode): ReactNode {
    const m = n.menu;
    const code = m.permCode ?? '';
    const id = code ? permIdByCode.get(code) : undefined;
    const perm = id !== undefined ? permById?.get(id) : undefined;
    const label = (m.i18nCode ? t(m.i18nCode) : '') || perm?.name || code || m.id;
    return permRow({ key: m.id, id, code, label, perm });
  }

  /** 末尾分组行（接口级码等）：与 DIR 行同款交互，成员行嵌套其下。 */
  function renderGroup(g: PermTreeGroup): ReactNode {
    const open = expandAll || !closedGroups.has(g.key);
    const on = g.perms.filter((p) => checkedPerms?.has(p.id)).length;
    return (
      <div className="ptree-node" key={g.key}>
        <div
          className="ptree-row is-dir clickable"
          onClick={() => setClosedGroups((prev) => toggleIn(prev, g.key))}
        >
          <span className={'tree-toggle' + (open ? ' open' : '')}>
            <IconChevron width={11} height={11} />
          </span>
          <span />
          <span className="ptree-name">
            {g.label}
            {g.iface && <span className="tag">{t('iam.admin.perms.ifaceTag')}</span>}
          </span>
          <span className="ptree-count">
            {grantMode ? `${on} / ${g.perms.length}` : `${g.perms.length}`}
          </span>
          <span className="ptree-actions">
            {grantMode && !superMode && groupAction?.(g)}
          </span>
        </div>
        {open && (
          <div className="ptree-kids">{g.perms.map((p) => permRow({ key: p.id, id: p.id, code: p.code, label: p.name || p.code, perm: p }))}</div>
        )}
      </div>
    );
  }

  function renderNodes(list: MenuTreeNode[]): ReactNode {
    return list.map((n) => {
      const m = n.menu;
      if (m.type === 'BUTTON') return renderLeaf(n); // 顶层 BUTTON 兜底
      if (m.type === 'DIR') {
        const open = expandAll || !closedDirs.has(m.id);
        const st = statsOf(n);
        return (
          <div className="ptree-node" key={m.id}>
            <div
              className="ptree-row is-dir clickable"
              onClick={() => setClosedDirs((prev) => toggleIn(prev, m.id))}
            >
              <span className={'tree-toggle' + (open ? ' open' : '')}>
                <IconChevron width={11} height={11} />
              </span>
              <span />
              <span className="ptree-name">{t(m.i18nCode ?? m.id)}</span>
              <span className="ptree-count">
                {grantMode ? `${st.on} / ${st.total}` : `${st.total}`}
              </span>
              <span />
            </div>
            {open && <div className="ptree-kids">{renderNodes(n.children ?? [])}</div>}
          </div>
        );
      }
      // MENU：页面行 = 菜单可见 + 入口码（+ 页面级动作），按钮码嵌于其下
      const btns = (n.children ?? []).filter((c) => c.menu.type === 'BUTTON');
      const open = expandAll || !closedPages.has(m.id);
      const entryId = m.permCode ? permIdByCode.get(m.permCode) : undefined;
      const entryPerm = entryId !== undefined ? permById?.get(entryId) : undefined;
      const active = entryId !== undefined && activePermId === entryId;
      const pickable = !grantMode && entryPerm && onPermClick;
      return (
        <div className="ptree-node" key={m.id}>
          <div className={'ptree-row is-page' + (active ? ' on' : '')}>
            {btns.length ? (
              <button
                type="button"
                className={'tree-toggle' + (open ? ' open' : '')}
                aria-expanded={open}
                onClick={(e) => {
                  e.stopPropagation();
                  setClosedPages((prev) => toggleIn(prev, m.id));
                }}
              >
                <IconChevron width={11} height={11} />
              </button>
            ) : (
              <span className="tree-toggle hollow" />
            )}
            {grantMode ? (
              <input
                type="checkbox"
                title={t('iam.admin.roles.menuTip')}
                checked={checkedMenus?.has(m.id) ?? false}
                disabled={superMode}
                onChange={() => onToggleMenu?.(m.id, !checkedMenus?.has(m.id))}
              />
            ) : (
              <span />
            )}
            <span
              className={'ptree-name' + (pickable ? ' clickable' : '')}
              onClick={pickable ? () => onPermClick?.(entryPerm!) : undefined}
            >
              {t(m.i18nCode ?? m.id)}
            </span>
            {m.permCode ? (
              grantMode ? (
                <label className="check ptree-entry" title={t('iam.admin.roles.entryTip')}>
                  <input
                    type="checkbox"
                    checked={entryId !== undefined && (checkedPerms?.has(entryId) ?? false)}
                    disabled={superMode || entryId === undefined}
                    onChange={() => {
                      if (entryId !== undefined) onTogglePerm?.(entryId, !checkedPerms?.has(entryId));
                    }}
                  />
                  <span className="tree-code dim mono">{m.permCode}</span>
                </label>
              ) : (
                <span
                  className={'tree-code dim mono' + (pickable ? ' clickable' : '')}
                  onClick={pickable ? () => onPermClick?.(entryPerm!) : undefined}
                >
                  {m.permCode}
                </span>
              )
            ) : (
              <span />
            )}
            <span className="ptree-actions">
              {grantMode && !superMode && pageAction?.(n)}
            </span>
          </div>
          {open && btns.length > 0 && (
            <div className="ptree-kids">{btns.map(renderLeaf)}</div>
          )}
        </div>
      );
    });
  }

  return (
    <div className="ptree">
      {renderNodes(nodes)}
      {(groups ?? []).map(renderGroup)}
    </div>
  );
}
