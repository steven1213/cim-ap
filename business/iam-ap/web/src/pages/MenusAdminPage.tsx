import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { MenuTreeNode, SysMenuDto, MenuNodeType } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { useHasAllPermissions } from '@/lib/usePermission';
import { MENU_LIST, MENU_CREATE, MENU_UPDATE, MENU_DELETE, SYS } from '@/lib/permCodes';
import { iconKeys, resolveIcon } from '@/lib/iconRegistry';
import {
  IconAlert,
  IconCheckCircle,
  IconChevron,
  IconPlus,
  IconRefresh,
  IconTrash,
} from '@/components/Icons';

/**
 * 菜单管理（`/menus`）：维护控制台菜单树（父子节点）、图标与 `i18n_code` / `perm_code` 绑定。
 *
 * <p><b>数据面在平台</b>：本页复用 `cim-system` 的 `/sys/menus[/tree]`（无 `/api/v1` 前缀，
 * 故走 `rootGet/rootPost/...`）。那些端点用平台码 `sys:menu:*` 鉴权，而控制台自己的
 * `iam:menu:*` 决定入口显隐 —— 两者是**双闸门**，见 `lib/permCodes.ts` 的 `SYS` 注释。</p>
 *
 * <p><b>菜单表不存名称</b>：节点只存 `i18n_code`，名称由前端 `t(i18n_code)` 渲染；
 * 副标题按约定用 `i18n_code + '.desc'`。故新增菜单后须去「多语言」页补译文，
 * 否则侧栏显示 humanize 兜底文案（但**绝不显示裸 key**）。</p>
 */
export default function MenusAdminPage() {
  const { t } = useTranslation();
  const canRead = useHasAllPermissions([MENU_LIST, SYS.MENU_LIST]);
  const canRemove = useHasAllPermissions([MENU_DELETE, SYS.MENU_REMOVE]);

  const [tree, setTree] = useState<MenuTreeNode[]>([]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<SysMenuDto | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  const flat = useMemo(() => flatten(tree), [tree]);
  const saveCodes = draft?.id ? [MENU_UPDATE, SYS.MENU_SAVE] : [MENU_CREATE, SYS.MENU_SAVE];

  async function load() {
    if (!canRead) return;
    setLoading(true);
    setErr('');
    try {
      const nodes = await api.rootGet<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true });
      setTree(nodes ?? []);
      setExpanded((prev) => (prev.size ? prev : new Set((nodes ?? []).map((n) => n.menu.id))));
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canRead]);

  function flash(text: string) {
    setMsg(text);
    setTimeout(() => setMsg(''), 3000);
  }

  function selectNode(menu: SysMenuDto) {
    setSelected(menu);
    setErr('');
    setDraft(toDraft(menu));
  }

  function startCreate(parentId: string | null, type: MenuNodeType = 'MENU') {
    setErr('');
    if (parentId === null) setSelected(null);
    setDraft({
      id: '',
      parentId,
      type,
      path: '',
      component: '',
      i18nCode: type === 'BUTTON' ? 'iam.btn.' : '',
      icon: type === 'MENU' ? 'list' : '',
      permCode: '',
      sortNo: type === 'BUTTON' ? 1 : 10,
      visible: type !== 'BUTTON',
      status: 'ENABLED',
    });
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    setErr('');
    if (!draft.i18nCode.trim()) {
      setErr(t('iam.field.i18nCode'));
      return;
    }
    if (draft.type === 'BUTTON' && !draft.permCode.trim()) {
      setErr(t('iam.admin.menus.buttonNeedsPerm'));
      return;
    }
    setBusy(true);
    try {
      const edits = {
        parentId: draft.parentId,
        type: draft.type,
        path: isNavLike(draft.type) ? draft.path.trim() || null : null,
        component: draft.type === 'MENU' ? draft.component.trim() || null : null,
        i18nCode: draft.i18nCode.trim(),
        icon: draft.type === 'MENU' ? draft.icon || null : null,
        permCode: draft.permCode.trim() || null,
        sortNo: Number(draft.sortNo) || 0,
        visible: draft.type !== 'BUTTON' && draft.visible,
        status: draft.status,
      };
      if (draft.id) {
        // 以「原节点 + 本次编辑」整体提交：保留基础字段（租户/审计列等），避免局部更新丢列
        await api.rootPut<SysMenuDto>('/sys/menus', { ...(selected ?? {}), ...edits });
      } else {
        const created = await api.rootPost<SysMenuDto>('/sys/menus', edits);
        if (created) selectNode(created);
      }
      flash(t('iam.common.saved'));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!selected) return;
    if (!window.confirm(`${t('iam.common.confirmDelete')}\n${selected.i18nCode ?? selected.id}`)) return;
    setErr('');
    setBusy(true);
    try {
      await api.rootDel<void>(`/sys/menus/${encodeURIComponent(selected.id)}`);
      flash(t('iam.common.deleted'));
      setSelected(null);
      setDraft(null);
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  if (!canRead) {
    return (
      <div className="page">
        <Panel>
          <div className="alert warn">
            <IconAlert width={15} height={15} />
            <span>{t('iam.common.noPlatformPerm')}</span>
          </div>
        </Panel>
      </div>
    );
  }

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {msg && (
        <div className="alert ok">
          <IconCheckCircle width={15} height={15} />
          <span>{msg}</span>
        </div>
      )}

      <div className="split">
        <Panel
          title={t('iam.admin.menus.tree')}
          sub={t('iam.admin.menus.nodeCount', { n: flat.length })}
          flush
          actions={
            <>
              <Perms codes={[MENU_CREATE, SYS.MENU_SAVE]}>
                <button
                  className="icon-btn"
                  title={t('iam.admin.menus.newRoot')}
                  onClick={() => startCreate(null)}
                >
                  <IconPlus width={14} height={14} />
                </button>
              </Perms>
              <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
                <IconRefresh width={13} height={13} />
                {loading ? t('iam.common.loading') : t('iam.common.refresh')}
              </button>
            </>
          }
        >
          {tree.length ? (
            <div className="tree">
              {tree.map((n) => (
                <TreeRow
                  key={n.menu.id}
                  node={n}
                  depth={0}
                  expanded={expanded}
                  selectedId={selected?.id ?? null}
                  onToggle={(id) =>
                    setExpanded((prev) => {
                      const next = new Set(prev);
                      if (next.has(id)) next.delete(id);
                      else next.add(id);
                      return next;
                    })
                  }
                  onSelect={selectNode}
                />
              ))}
            </div>
          ) : (
            <div className="empty">
              <IconRefresh width={22} height={22} />
              <b>{t('iam.common.none')}</b>
            </div>
          )}
        </Panel>

        <div>
          {draft ? (
            <Panel
              title={draft.id ? t('iam.common.edit') : t('iam.common.add')}
              sub={draft.id ? (selected?.i18nCode ?? draft.id) : t('iam.admin.menus.newRoot')}
              actions={
                <>
                  {canRemove && draft.id && (
                    <button className="btn-ghost btn-sm" onClick={onDelete} disabled={busy}>
                      <IconTrash width={13} height={13} />
                      {t('iam.common.delete')}
                    </button>
                  )}
                </>
              }
            >
              <form onSubmit={onSubmit}>
                <div className="form-grid">
                  <div className="field">
                    <label className="field-label" htmlFor="m-type">
                      {t('iam.field.type')}
                    </label>
                    <select
                      id="m-type"
                      value={draft.type}
                      onChange={(e) => setDraft({ ...draft, type: e.target.value as MenuNodeType })}
                    >
                      <option value="DIR">{t('iam.type.dir')}</option>
                      <option value="MENU">{t('iam.type.menu')}</option>
                      <option value="BUTTON">{t('iam.type.button')}</option>
                    </select>
                  </div>
                  <div className="field">
                    <label className="field-label" htmlFor="m-parent">
                      {t('iam.admin.menus.parent')}
                    </label>
                    <select
                      id="m-parent"
                      value={draft.parentId ?? ''}
                      onChange={(e) => setDraft({ ...draft, parentId: e.target.value || null })}
                    >
                      <option value="">{t('iam.admin.menus.root')}</option>
                      {flat
                        .filter((f) => f.menu.type !== 'BUTTON' && f.menu.id !== draft.id)
                        .map((f) => (
                          <option key={f.menu.id} value={f.menu.id}>
                            {(f.depth ? '— '.repeat(f.depth) : '') + t(f.menu.i18nCode ?? f.menu.id)}
                          </option>
                        ))}
                    </select>
                  </div>
                  <div className="field">
                    <label className="field-label" htmlFor="m-i18n">
                      {t('iam.field.i18nCode')}
                    </label>
                    <input
                      id="m-i18n"
                      className="mono"
                      placeholder="iam.menu.menus"
                      value={draft.i18nCode}
                      onChange={(e) => setDraft({ ...draft, i18nCode: e.target.value })}
                    />
                  </div>
                  <div className="field">
                    <label className="field-label" htmlFor="m-sort">
                      {t('iam.field.sortNo')}
                    </label>
                    <input
                      id="m-sort"
                      type="number"
                      value={draft.sortNo}
                      onChange={(e) => setDraft({ ...draft, sortNo: Number(e.target.value) })}
                    />
                  </div>
                  {isNavLike(draft.type) && (
                    <div className="field">
                      <label className="field-label" htmlFor="m-path">
                        {t('iam.field.path')}
                      </label>
                      <input
                        id="m-path"
                        className="mono"
                        placeholder="/menus"
                        value={draft.path}
                        onChange={(e) => setDraft({ ...draft, path: e.target.value })}
                      />
                    </div>
                  )}
                  {draft.type === 'MENU' && (
                    <>
                      <div className="field">
                        <label className="field-label" htmlFor="m-component">
                          {t('iam.field.component')}
                        </label>
                        <input
                          id="m-component"
                          className="mono"
                          placeholder="MenusAdmin"
                          value={draft.component}
                          onChange={(e) => setDraft({ ...draft, component: e.target.value })}
                        />
                      </div>
                      <div className="field">
                        <label className="field-label" htmlFor="m-icon">
                          {t('iam.field.icon')}
                        </label>
                        <select
                          id="m-icon"
                          value={draft.icon}
                          onChange={(e) => setDraft({ ...draft, icon: e.target.value })}
                        >
                          <option value="">{t('iam.common.none')}</option>
                          {iconKeys().map((k) => (
                            <option key={k} value={k}>
                              {k}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div className="field">
                        <label className="field-label" htmlFor="m-visible">
                          {t('iam.field.visible')}
                        </label>
                        <select
                          id="m-visible"
                          value={draft.visible ? '1' : '0'}
                          onChange={(e) => setDraft({ ...draft, visible: e.target.value === '1' })}
                        >
                          <option value="1">{t('iam.common.enabled')}</option>
                          <option value="0">{t('iam.common.disabled')}</option>
                        </select>
                      </div>
                    </>
                  )}
                  <div className="field">
                    <label className="field-label" htmlFor="m-perm">
                      {t('iam.field.permCode')}
                      {draft.type === 'BUTTON' && <span className="opt">{t('iam.common.required')}</span>}
                    </label>
                    <input
                      id="m-perm"
                      className="mono"
                      placeholder="iam:menu:create"
                      value={draft.permCode}
                      onChange={(e) => setDraft({ ...draft, permCode: e.target.value })}
                    />
                  </div>
                  <div className="field">
                    <label className="field-label" htmlFor="m-status">
                      {t('iam.common.enabled')} / {t('iam.common.disabled')}
                    </label>
                    <select
                      id="m-status"
                      value={draft.status}
                      onChange={(e) =>
                        setDraft({ ...draft, status: e.target.value as 'ENABLED' | 'DISABLED' })
                      }
                    >
                      <option value="ENABLED">{t('iam.common.enabled')}</option>
                      <option value="DISABLED">{t('iam.common.disabled')}</option>
                    </select>
                  </div>
                </div>

                <p className="hint">{t('iam.admin.menus.i18nCodeHint')}</p>
                <p className="hint">{t('iam.admin.menus.permHint')}</p>
                {draft.type === 'MENU' && <p className="hint">{t('iam.admin.menus.iconHint')}</p>}
                {draft.type === 'MENU' && <p className="hint">{t('iam.admin.menus.visibleHint')}</p>}

                <div className="sub-head" style={{ marginTop: 12 }}>
                  <Perms codes={saveCodes}>
                    <button type="submit" className="btn" disabled={busy}>
                      {busy ? t('iam.common.saving') : t('iam.common.save')}
                    </button>
                  </Perms>
                  <Perms codes={[MENU_CREATE, SYS.MENU_SAVE]}>
                    <>
                      {draft.type !== 'BUTTON' && (
                        <button
                          type="button"
                          className="btn-ghost btn-sm"
                          onClick={() => startCreate(draft.id || selected?.id || null, 'MENU')}
                        >
                          <IconPlus width={13} height={13} />
                          {t('iam.admin.menus.addChild')}
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn-ghost btn-sm"
                        onClick={() => startCreate(draft.id || selected?.id || null, 'BUTTON')}
                      >
                        <IconPlus width={13} height={13} />
                        {t('iam.type.button')}
                      </button>
                    </>
                  </Perms>
                </div>
              </form>
            </Panel>
          ) : (
            <Panel title={t('iam.admin.menus.selectNode')}>
              <div className="empty">
                <IconChevron width={22} height={22} />
                <b>{t('iam.admin.menus.selectNode')}</b>
              </div>
            </Panel>
          )}
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ 辅助

interface Draft {
  id: string;
  parentId: string | null;
  type: MenuNodeType;
  path: string;
  component: string;
  i18nCode: string;
  icon: string;
  permCode: string;
  sortNo: number;
  visible: boolean;
  status: 'ENABLED' | 'DISABLED';
}

/** DIR 与 MENU 有路由；BUTTON 没有。 */
function isNavLike(type: MenuNodeType): boolean {
  return type === 'DIR' || type === 'MENU';
}

function toDraft(m: SysMenuDto): Draft {
  return {
    id: m.id,
    parentId: m.parentId,
    type: m.type,
    path: m.path ?? '',
    component: m.component ?? '',
    i18nCode: m.i18nCode ?? '',
    icon: m.icon ?? '',
    permCode: m.permCode ?? '',
    sortNo: m.sortNo ?? 0,
    visible: m.visible,
    status: m.status,
  };
}

function flatten(nodes: MenuTreeNode[], depth = 0): { menu: SysMenuDto; depth: number }[] {
  const out: { menu: SysMenuDto; depth: number }[] = [];
  for (const n of nodes) {
    out.push({ menu: n.menu, depth });
    out.push(...flatten(n.children ?? [], depth + 1));
  }
  return out;
}

function TreeRow({ node, depth, expanded, selectedId, onToggle, onSelect }: {
  node: MenuTreeNode;
  depth: number;
  expanded: Set<string>;
  selectedId: string | null;
  onToggle: (id: string) => void;
  onSelect: (menu: SysMenuDto) => void;
}) {
  const { t } = useTranslation();
  const { menu, children } = node;
  const hasChildren = (children?.length ?? 0) > 0;
  const open = expanded.has(menu.id);
  const Icon = menu.type === 'BUTTON' ? null : resolveIcon(menu.icon);
  const badge = menu.type === 'BUTTON' ? t('iam.type.button')
    : menu.type === 'DIR' ? t('iam.type.dir') : null;

  return (
    <>
      <div
        className={'tree-row' + (selectedId === menu.id ? ' on' : '')}
        style={{ paddingLeft: 12 + depth * 16 }}
        onClick={() => onSelect(menu)}
      >
        <button
          className={'tree-toggle' + (open ? ' open' : '') + (hasChildren ? '' : ' hollow')}
          onClick={(e) => {
            e.stopPropagation();
            onToggle(menu.id);
          }}
        >
          <IconChevron width={12} height={12} />
        </button>
        {Icon && <Icon width={14} height={14} />}
        <span className="tree-name">{t(menu.i18nCode ?? menu.id)}</span>
        {badge && <span className="tag">{badge}</span>}
        {menu.status === 'DISABLED' && <span className="tag err">{t('iam.common.disabled')}</span>}
      </div>
      {open &&
        hasChildren &&
        children.map((c) => (
          <TreeRow
            key={c.menu.id}
            node={c}
            depth={depth + 1}
            expanded={expanded}
            selectedId={selectedId}
            onToggle={onToggle}
            onSelect={onSelect}
          />
        ))}
    </>
  );
}
