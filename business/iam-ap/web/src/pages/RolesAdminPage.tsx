import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { MenuTreeNode, PageResultDto, SysMenuDto, SysPermissionDto, SysRoleDto } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import PermTree from '@/components/PermTree';
import { useHasAllPermissions } from '@/lib/usePermission';
import { buildPermPageMap, groupPermsByPage } from '@/lib/permPageMap';
import {
  ROLE_ADMIN_LIST,
  ROLE_ADMIN_CREATE,
  ROLE_ADMIN_UPDATE,
  ROLE_ADMIN_DELETE,
  ROLE_ADMIN_GRANT,
  SYS,
} from '@/lib/permCodes';
import { IconAlert, IconCheckCircle, IconPlus, IconRefresh, IconSave, IconTrash } from '@/components/Icons';

/**
 * 角色管理（`/roles-admin`）：角色 CRUD + 授权。
 *
 * <p><b>授权交互为单棵菜单树（一次保存），存储仍是两张独立授权表</b>：
 * `sys_role_perm`（接口权限）与 `sys_role_menu`（侧栏可见）。树上勾选菜单＝写菜单表，
 * 勾选入口码/按钮码＝写权限表，一个「保存授权」顺序写两张表。平台刻意让
 * 「能调用接口」与「看得见菜单」解耦（README §21.1）——解耦语义完整保留：
 * 可以只勾菜单不勾码（路由可达、接口 403），反之亦然；只是不再要求管理员分两批操作。</p>
 *
 * <p><b>树结构</b>（与菜单树同构，渲染交给共用组件 `PermTree`）：DIR 可折叠；
 * MENU 行＝菜单可见 checkbox + 入口码 checkbox + 「全选」快捷（菜单+入口+全部按钮）；
 * 其下嵌套该页按钮码。未挂页面的接口级码（平台 `sys:*` 闸门等）按 module 分组排在
 * 树后，与权限管理页同口径。</p>
 *
 * <p>数据面在平台 `/sys/**`，需同时具备控制台码与平台码（双闸门，见 `lib/permCodes.ts` 的 `SYS`）。</p>
 */
export default function RolesAdminPage() {
  const { t } = useTranslation();
  const canRead = useHasAllPermissions([ROLE_ADMIN_LIST, SYS.ROLE_LIST]);
  const canRemove = useHasAllPermissions([ROLE_ADMIN_DELETE, SYS.ROLE_REMOVE]);

  const [roles, setRoles] = useState<SysRoleDto[]>([]);
  const [selected, setSelected] = useState<SysRoleDto | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [tab, setTab] = useState<'basic' | 'grant'>('basic');

  const [allPerms, setAllPerms] = useState<SysPermissionDto[]>([]);
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([]);
  const [allMenus, setAllMenus] = useState<{ menu: SysMenuDto; depth: number }[]>([]);
  const [catalogErr, setCatalogErr] = useState('');

  const [grantedPerms, setGrantedPerms] = useState<Set<string>>(new Set());
  const [grantedMenus, setGrantedMenus] = useState<Set<string>>(new Set());
  /** 授权区：码/名检索（本地过滤）。 */
  const [permKw, setPermKw] = useState('');

  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  const saveCodes = selected ? [ROLE_ADMIN_UPDATE, SYS.ROLE_SAVE] : [ROLE_ADMIN_CREATE, SYS.ROLE_SAVE];

  async function load() {
    if (!canRead) return;
    setLoading(true);
    setErr('');
    try {
      const p = await api.rootGet<PageResultDto<SysRoleDto>>('/sys/roles/page', {
        page: 1,
        size: 500,
        sort: 'sortNo,asc',
      });
      setRoles(p?.records ?? []);
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setLoading(false);
    }
  }

  /**
   * 授权用的两份目录（权限点 / 菜单树）。
   *
   * <p>与角色列表**分开加载**：只读角色可能没有平台 `sys:permission:list` / `sys:menu:list`，
   * 此时角色列表仍可用，仅授权区显示提示——避免一个 403 把整页拖垮。</p>
   */
  async function loadCatalogs() {
    setCatalogErr('');
    try {
      const [permPage, tree] = await Promise.all([
        api.rootGet<PageResultDto<SysPermissionDto>>('/sys/permissions/page', {
          page: 1,
          size: 500,
          sort: 'code,asc',
        }),
        api.rootGet<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }),
      ]);
      setAllPerms(permPage?.records ?? []);
      setMenuTree(tree ?? []);
      setAllMenus(flatten(tree ?? []));
    } catch (e: any) {
      setAllPerms([]);
      setMenuTree([]);
      setAllMenus([]);
      setCatalogErr(e?.msg || t('iam.common.noPlatformPerm'));
    }
  }

  useEffect(() => {
    load();
    if (canRead) loadCatalogs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canRead]);

  async function selectRole(role: SysRoleDto) {
    setErr('');
    setSelected(role);
    setTab('basic');
    setDraft({
      id: role.id,
      code: role.code,
      name: role.name ?? '',
      isSuper: role.isSuper,
      sortNo: role.sortNo ?? 0,
      status: role.status,
      origin: role,
    });
    try {
      const [p, m] = await Promise.all([
        api.rootGet<string[]>(`/sys/roles/${encodeURIComponent(role.id)}/permissions`),
        api.rootGet<string[]>(`/sys/roles/${encodeURIComponent(role.id)}/menus`),
      ]);
      setGrantedPerms(new Set(p ?? []));
      setGrantedMenus(new Set(m ?? []));
    } catch (e: any) {
      setGrantedPerms(new Set());
      setGrantedMenus(new Set());
      setCatalogErr(e?.msg || t('iam.common.noPlatformPerm'));
    }
  }

  function flash(text: string) {
    setMsg(text);
    setTimeout(() => setMsg(''), 3000);
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    setErr('');
    setBusy(true);
    try {
      const edits = {
        code: draft.code.trim(),
        name: draft.name.trim() || null,
        isSuper: draft.isSuper,
        sortNo: Number(draft.sortNo) || 0,
        status: draft.status,
      };
      const saved = draft.id
        ? await api.rootPut<SysRoleDto>('/sys/roles', { ...(draft.origin ?? {}), ...edits })
        : await api.rootPost<SysRoleDto>('/sys/roles', edits);
      flash(t('iam.common.saved'));
      await load();
      if (saved) await selectRole(saved);
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!draft?.id) return;
    if (!window.confirm(`${t('iam.common.confirmDelete')}\n${draft.code}`)) return;
    setErr('');
    setBusy(true);
    try {
      await api.rootDel<void>(`/sys/roles/${encodeURIComponent(draft.id)}`);
      flash(t('iam.common.deleted'));
      setSelected(null);
      setDraft(null);
      setGrantedPerms(new Set());
      setGrantedMenus(new Set());
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  /**
   * 一次保存两张授权表（顺序执行、各自幂等覆盖写）。
   *
   * <p>两张表分属两个平台端点、无跨表事务——任一失败时把失败的那张表名列进错误提示，
   * 成功的那张不回滚（覆盖写可重试，重按一次保存即可补齐）。</p>
   */
  async function saveGrant() {
    if (!selected) return;
    setErr('');
    setBusy(true);
    const failed: string[] = [];
    try {
      await api.rootPut<void>(
        `/sys/roles/${encodeURIComponent(selected.id)}/permissions`,
        Array.from(grantedPerms),
      );
    } catch {
      failed.push(t('iam.admin.roles.tabPermGrant'));
    }
    try {
      await api.rootPut<void>(
        `/sys/roles/${encodeURIComponent(selected.id)}/menus`,
        Array.from(grantedMenus),
      );
    } catch {
      failed.push(t('iam.admin.roles.tabMenuGrant'));
    }
    setBusy(false);
    if (failed.length) {
      setErr(`${t('iam.common.saveFailed')}：${failed.join(' / ')}`);
    } else {
      flash(t('iam.common.saved'));
    }
  }

  /** 权限码按「所属页面」分组（菜单树关联）；接口级码按 module 排后；支持本地检索。 */
  const permGroups = useMemo(() => {
    const kw = permKw.trim().toLowerCase();
    const visible = kw
      ? allPerms.filter(
          (p) => p.code.toLowerCase().includes(kw) || (p.name ?? '').toLowerCase().includes(kw),
        )
      : allPerms;
    return groupPermsByPage(visible, buildPermPageMap(menuTree));
  }, [allPerms, menuTree, permKw]);

  /** 组级全选/清空：组内已全选则清空，否则全选。 */
  function toggleGroup(g: { perms: SysPermissionDto[] }) {
    const next = new Set(grantedPerms);
    const allOn = g.perms.length > 0 && g.perms.every((p) => next.has(p.id));
    for (const p of g.perms) {
      if (allOn) next.delete(p.id);
      else next.add(p.id);
    }
    setGrantedPerms(next);
  }

  /** 授权树用：permCode → 权限行 ID（授权接口按权限 ID 读写；树上只有码）。 */
  const permIdByCode = useMemo(() => {
    const m = new Map<string, string>();
    for (const p of allPerms) m.set(p.code, p.id);
    return m;
  }, [allPerms]);

  /** 检索过滤：命中菜单名/i18n/码或任一后代即保留分支；检索时目录强制展开。 */
  const filteredTree = useMemo(() => {
    const kw = permKw.trim().toLowerCase();
    if (!kw) return menuTree;
    const hit = (n: MenuTreeNode): boolean =>
      (n.menu.permCode ?? '').toLowerCase().includes(kw) ||
      (n.menu.i18nCode ?? '').toLowerCase().includes(kw) ||
      t(n.menu.i18nCode ?? n.menu.id).toLowerCase().includes(kw) ||
      (n.children ?? []).some(hit);
    const prune = (nodes: MenuTreeNode[]): MenuTreeNode[] =>
      nodes.filter(hit).map((n) => ({ menu: n.menu, children: prune(n.children ?? []) }));
    return prune(menuTree);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [menuTree, permKw]);

  /** MENU 行快捷「全选」：菜单可见 + 入口码 + 全部按钮码，整页一键开/关。 */
  function toggleMenuPage(node: MenuTreeNode) {
    const btns = (node.children ?? []).filter(
      (c) => c.menu.type === 'BUTTON' && c.menu.permCode && permIdByCode.has(c.menu.permCode),
    );
    const entryId = node.menu.permCode ? permIdByCode.get(node.menu.permCode) : undefined;
    const allOn =
      grantedMenus.has(node.menu.id) &&
      (!node.menu.permCode || (entryId !== undefined && grantedPerms.has(entryId))) &&
      btns.every((b) => grantedPerms.has(permIdByCode.get(b.menu.permCode!)!));
    const menus = new Set(grantedMenus);
    const perms = new Set(grantedPerms);
    if (allOn) menus.delete(node.menu.id);
    else menus.add(node.menu.id);
    if (entryId !== undefined) {
      if (allOn) perms.delete(entryId);
      else perms.add(entryId);
    }
    for (const b of btns) {
      const id = permIdByCode.get(b.menu.permCode!)!;
      if (allOn) perms.delete(id);
      else perms.add(id);
    }
    setGrantedMenus(menus);
    setGrantedPerms(perms);
  }

  /** 接口级码组（未挂任何页面的码，如平台 sys:* 闸门），排在授权树之后。 */
  const ifaceGroups = useMemo(
    () => permGroups.filter((g) => g.kind === 'module'),
    [permGroups],
  );

  /**
   * 授权树（共用组件 {@link PermTree}）：DIR 折叠 → MENU 行（菜单可见 + 入口码 + 页面全选）
   * → 按钮码嵌套其下。按钮码以菜单树 BUTTON 节点的 `permCode` 为准，经 {@link permIdByCode}
   * 换算成权限 ID 写入 `sys_role_perm`；码未在权限表注册时行灰显（显示码但勾不了）。
   */
  const grantTree = (
    <PermTree
      nodes={filteredTree}
      permIdByCode={permIdByCode}
      checkedPerms={grantedPerms}
      onTogglePerm={(id, on) => {
        const next = new Set(grantedPerms);
        if (on) next.add(id);
        else next.delete(id);
        setGrantedPerms(next);
      }}
      checkedMenus={grantedMenus}
      onToggleMenu={(id, on) => {
        const next = new Set(grantedMenus);
        if (on) next.add(id);
        else next.delete(id);
        setGrantedMenus(next);
      }}
      superMode={draft?.isSuper ?? false}
      pageAction={(node) => (
        <button
          type="button"
          className="btn-ghost btn-sm"
          style={{ padding: '1px 8px' }}
          onClick={() => toggleMenuPage(node)}
        >
          {t('iam.common.selectAll')}
        </button>
      )}
      groups={ifaceGroups.map((g) => ({ key: g.key, label: g.label, iface: true, perms: g.perms }))}
      groupAction={(g) => {
        const allOn = g.perms.length > 0 && g.perms.every((p) => grantedPerms.has(p.id));
        return (
          <button
            type="button"
            className="btn-ghost btn-sm"
            style={{ padding: '1px 8px' }}
            onClick={() => toggleGroup(g)}
          >
            {allOn ? t('iam.common.clearAll') : t('iam.common.selectAll')}
          </button>
        );
      }}
      kw={permKw}
    />
  );

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
          className="scroll-y"
          title={t('iam.admin.roles.list')}
          sub={`${roles.length}`}
          flush
          actions={
            <>
              <Perms codes={[ROLE_ADMIN_CREATE, SYS.ROLE_SAVE]}>
                <button
                  className="icon-btn"
                  title={t('iam.admin.roles.new')}
                  onClick={() => {
                    setErr('');
                    setSelected(null);
                    setDraft({
                      id: '',
                      code: '',
                      name: '',
                      isSuper: false,
                      sortNo: 10,
                      status: 'ENABLED',
                      origin: null,
                    });
                    setGrantedPerms(new Set());
                    setGrantedMenus(new Set());
                  }}
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
          {roles.length ? (
            <div className="tree">
              {roles.map((r) => (
                <div
                  key={r.id}
                  className={'tree-row' + (selected?.id === r.id ? ' on' : '')}
                  onClick={() => selectRole(r)}
                >
                  <span className="tree-name">{r.name ?? r.code}</span>
                  {r.isSuper && <span className="tag pri">{t('iam.admin.roles.super')}</span>}
                  {r.status === 'DISABLED' && (
                    <span className="tag err">{t('iam.common.disabled')}</span>
                  )}
                  <span className="tree-code dim">{r.code}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty">
              <IconPlus width={22} height={22} />
              <b>{t('iam.admin.roles.new')}</b>
            </div>
          )}
        </Panel>

        <div>
          {draft ? (
            <>
              <div className="tabs">
                <button
                  type="button"
                  className={'tab' + (tab === 'basic' ? ' on' : '')}
                  onClick={() => setTab('basic')}
                >
                  {t('iam.admin.roles.tabBasic')}
                </button>
                {draft.id && (
                  <button
                    type="button"
                    className={'tab' + (tab === 'grant' ? ' on' : '')}
                    onClick={() => setTab('grant')}
                  >
                    {t('iam.admin.roles.tabGrant')}
                    <span className="tag-count">
                      {grantedPerms.size}/{allPerms.length} · {grantedMenus.size}/{allMenus.length}
                    </span>
                  </button>
                )}
              </div>

              {tab === 'basic' && (
              <Panel
                title={draft.id ? t('iam.common.edit') : t('iam.admin.roles.new')}
                sub={draft.id ?? t('iam.admin.roles.new')}
                actions={
                  canRemove && draft.id ? (
                    <button className="btn-ghost btn-sm" onClick={onDelete} disabled={busy}>
                      <IconTrash width={13} height={13} />
                      {t('iam.common.delete')}
                    </button>
                  ) : null
                }
              >
                <form onSubmit={onSave}>
                  <div className="form-grid">
                    <div className="field">
                      <label className="field-label" htmlFor="r-code">
                        {t('iam.field.code')}
                        <span className="opt">{t('iam.common.required')}</span>
                      </label>
                      <input
                        id="r-code"
                        className="mono"
                        placeholder="IAM_AUDITOR"
                        value={draft.code}
                        onChange={(e) => setDraft({ ...draft, code: e.target.value })}
                      />
                    </div>
                    <div className="field">
                      <label className="field-label" htmlFor="r-name">
                        {t('iam.field.name')}
                      </label>
                      <input
                        id="r-name"
                        value={draft.name}
                        onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                      />
                    </div>
                    <div className="field">
                      <label className="field-label" htmlFor="r-sort">
                        {t('iam.field.sortNo')}
                      </label>
                      <input
                        id="r-sort"
                        type="number"
                        value={draft.sortNo}
                        onChange={(e) => setDraft({ ...draft, sortNo: Number(e.target.value) })}
                      />
                    </div>
                    <div className="field">
                      <label className="field-label" htmlFor="r-super">
                        {t('iam.admin.roles.super')}
                      </label>
                      <select
                        id="r-super"
                        value={draft.isSuper ? '1' : '0'}
                        onChange={(e) => setDraft({ ...draft, isSuper: e.target.value === '1' })}
                      >
                        <option value="0">{t('iam.common.none')}</option>
                        <option value="1">{t('iam.admin.roles.super')}</option>
                      </select>
                    </div>
                    <div className="field">
                      <label className="field-label" htmlFor="r-status">
                        {t('iam.common.enabled')} / {t('iam.common.disabled')}
                      </label>
                      <select
                        id="r-status"
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

                  {draft.isSuper && <p className="hint">{t('iam.admin.roles.superHint')}</p>}

                  <div className="sub-head" style={{ marginTop: 12 }}>
                    <Perms codes={saveCodes}>
                      <button type="submit" className="btn" disabled={busy}>
                        {busy ? t('iam.common.saving') : t('iam.common.save')}
                      </button>
                    </Perms>
                    <button type="button" className="btn-ghost btn-sm" onClick={() => setDraft(null)}>
                      {t('iam.common.close')}
                    </button>
                  </div>
                </form>
              </Panel>
              )}

              {tab === 'grant' && draft.id && (
              <Panel
                    title={t('iam.admin.roles.tabGrant')}
                    sub={`${grantedPerms.size} / ${allPerms.length} · ${grantedMenus.size} / ${allMenus.length}`}
                    actions={
                      <Perms codes={[ROLE_ADMIN_GRANT, SYS.ROLE_GRANT]}>
                        <button className="btn-ghost btn-sm" onClick={saveGrant} disabled={busy}>
                          <IconSave width={13} height={13} />
                          {t('iam.admin.roles.grantSave')}
                        </button>
                      </Perms>
                    }
                  >
                    <p className="hint">{t('iam.admin.roles.grantNote')}</p>
                    {catalogErr && <div className="alert warn">{catalogErr}</div>}
                    {!draft.isSuper && (
                      <div className="sub-head">
                        <span className="hint">{t('iam.admin.roles.groupByPage')}</span>
                        <span style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                          <input
                            className="mono"
                            style={{ width: 200 }}
                            placeholder={t('iam.common.search')}
                            value={permKw}
                            onChange={(e) => setPermKw(e.target.value)}
                          />
                          <button
                            type="button"
                            className="btn-ghost btn-sm"
                            onClick={() => setGrantedPerms(new Set(allPerms.map((p) => p.id)))}
                          >
                            {t('iam.common.selectAll')}
                          </button>
                          <button
                            type="button"
                            className="btn-ghost btn-sm"
                            onClick={() => setGrantedPerms(new Set())}
                          >
                            {t('iam.common.clearAll')}
                          </button>
                        </span>
                      </div>
                    )}
                    {menuTree.length ? (
                      <div style={{ paddingTop: 4 }}>{grantTree}</div>
                    ) : (
                      <div className="empty">
                        <IconAlert width={22} height={22} />
                        <b>{catalogErr || t('iam.common.none')}</b>
                      </div>
                    )}
                  </Panel>
              )}
            </>
          ) : (
            <Panel title={t('iam.admin.roles.selectRole')}>
              <div className="empty">
                <IconPlus width={22} height={22} />
                <b>{t('iam.admin.roles.selectRole')}</b>
              </div>
            </Panel>
          )}
        </div>
      </div>
    </div>
  );
}

interface Draft {
  id: string;
  code: string;
  name: string;
  isSuper: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
  origin: SysRoleDto | null;
}

function flatten(nodes: MenuTreeNode[], depth = 0): { menu: SysMenuDto; depth: number }[] {
  const out: { menu: SysMenuDto; depth: number }[] = [];
  for (const n of nodes) {
    out.push({ menu: n.menu, depth });
    out.push(...flatten(n.children ?? [], depth + 1));
  }
  return out;
}
