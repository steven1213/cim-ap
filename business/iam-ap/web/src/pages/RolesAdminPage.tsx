import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { MenuTreeNode, PageResultDto, SysMenuDto, SysPermissionDto, SysRoleDto } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
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
 * 角色管理（`/roles-admin`）：角色 CRUD + **两张独立授权表**（`sys_role_perm` / `sys_role_menu`）。
 *
 * <p><b>为什么不合并成一个「授权」按钮</b>：平台设计刻意让「能调用接口」与「看得见菜单」解耦
 * （README §21.1）。合并会让两者重新耦合，正是要避免的退化——例如「有菜单但无权限码」是合法态：
 * 路由可达、点进去接口 403。本页右侧用**三个标签页**分别承载（基本信息 / 权限授权 / 菜单授权），
 * 各自独立保存（覆盖式写入，幂等）——解耦语义不变，纵向堆叠三个长面板的布局问题消除。</p>
 *
 * <p><b>权限授权按页面分组</b>：与权限管理页一致，以菜单树 MENU/BUTTON 的 `permCode`
 * 关联为权威源按「所属页面」分组（组头带勾选计数 + 组级全选/清空），接口级码排后并打标。</p>
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
  const [tab, setTab] = useState<'basic' | 'perms' | 'menus'>('basic');

  const [allPerms, setAllPerms] = useState<SysPermissionDto[]>([]);
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([]);
  const [allMenus, setAllMenus] = useState<{ menu: SysMenuDto; depth: number }[]>([]);
  const [catalogErr, setCatalogErr] = useState('');

  const [grantedPerms, setGrantedPerms] = useState<Set<string>>(new Set());
  const [grantedMenus, setGrantedMenus] = useState<Set<string>>(new Set());

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

  async function savePerms() {
    if (!selected) return;
    setErr('');
    setBusy(true);
    try {
      await api.rootPut<void>(
        `/sys/roles/${encodeURIComponent(selected.id)}/permissions`,
        Array.from(grantedPerms),
      );
      flash(t('iam.common.saved'));
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function saveMenus() {
    if (!selected) return;
    setErr('');
    setBusy(true);
    try {
      await api.rootPut<void>(
        `/sys/roles/${encodeURIComponent(selected.id)}/menus`,
        Array.from(grantedMenus),
      );
      flash(t('iam.common.saved'));
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  /** 权限码按「所属页面」分组（菜单树关联）；接口级码按 module 排后。 */
  const permGroups = useMemo(
    () => groupPermsByPage(allPerms, buildPermPageMap(menuTree)),
    [allPerms, menuTree],
  );

  /** 组级全选/清空：组内已全选则清空，否则全选。 */
  function toggleGroup(g: { perms: SysPermissionDto[] }) {
    const next = new Set(grantedPerms);
    const allOn = g.perms.every((p) => next.has(p.id));
    for (const p of g.perms) {
      if (allOn) next.delete(p.id);
      else next.add(p.id);
    }
    setGrantedPerms(next);
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
                  <>
                    <button
                      type="button"
                      className={'tab' + (tab === 'perms' ? ' on' : '')}
                      onClick={() => setTab('perms')}
                    >
                      {t('iam.admin.roles.tabPermGrant')}
                      <span className="tag-count">
                        {grantedPerms.size}/{allPerms.length}
                      </span>
                    </button>
                    <button
                      type="button"
                      className={'tab' + (tab === 'menus' ? ' on' : '')}
                      onClick={() => setTab('menus')}
                    >
                      {t('iam.admin.roles.tabMenuGrant')}
                      <span className="tag-count">
                        {grantedMenus.size}/{allMenus.length}
                      </span>
                    </button>
                  </>
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

              {tab === 'perms' && draft.id && (
              <Panel
                    title={t('iam.admin.roles.permGrant')}
                    sub={`${grantedPerms.size} / ${allPerms.length}`}
                    actions={
                      <Perms codes={[ROLE_ADMIN_GRANT, SYS.ROLE_GRANT]}>
                        <button className="btn-ghost btn-sm" onClick={savePerms} disabled={busy}>
                          <IconSave width={13} height={13} />
                          {t('iam.admin.roles.grantSave')}
                        </button>
                      </Perms>
                    }
                  >
                    <p className="hint">{t('iam.admin.roles.permNote')}</p>
                    {catalogErr && <div className="alert warn">{catalogErr}</div>}
                    {!draft.isSuper && (
                      <div className="sub-head">
                        <span className="hint">{t('iam.admin.roles.groupByPage')}</span>
                        <span style={{ display: 'flex', gap: 6 }}>
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
                    {permGroups.map((g) => (
                      <div className="sub-block" key={g.key}>
                        <div className="side-cap" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span style={{ textTransform: g.kind === 'page' ? 'none' : undefined }}>
                            {g.kind === 'page' ? t(g.label) : g.label}
                          </span>
                          {g.kind === 'module' && (
                            <span className="tag">{t('iam.admin.perms.ifaceTag')}</span>
                          )}
                          <span className="tag-count">
                            {g.perms.filter((p) => grantedPerms.has(p.id)).length}/{g.perms.length}
                          </span>
                          {!draft.isSuper && (
                            <button
                              type="button"
                              className="btn-ghost btn-sm"
                              style={{ marginLeft: 'auto', padding: '1px 8px' }}
                              onClick={() => toggleGroup(g)}
                            >
                              {g.perms.every((p) => grantedPerms.has(p.id))
                                ? t('iam.common.clearAll')
                                : t('iam.common.selectAll')}
                            </button>
                          )}
                        </div>
                        <div className="grid-3">
                          {g.perms.map((p) => (
                            <label className="check" key={p.id}>
                              <input
                                type="checkbox"
                                checked={grantedPerms.has(p.id)}
                                disabled={draft.isSuper}
                                onChange={(e) => {
                                  const next = new Set(grantedPerms);
                                  if (e.target.checked) next.add(p.id);
                                  else next.delete(p.id);
                                  setGrantedPerms(next);
                                }}
                              />
                              <span className="mono">{p.code}</span>
                            </label>
                          ))}
                        </div>
                      </div>
                    ))}
                  </Panel>
              )}

              {tab === 'menus' && draft.id && (
              <Panel
                    title={t('iam.admin.roles.menuGrant')}
                    sub={`${grantedMenus.size} / ${allMenus.length}`}
                    flush
                    actions={
                      <Perms codes={[ROLE_ADMIN_GRANT, SYS.ROLE_GRANT]}>
                        <button className="btn-ghost btn-sm" onClick={saveMenus} disabled={busy}>
                          <IconSave width={13} height={13} />
                          {t('iam.admin.roles.grantSave')}
                        </button>
                      </Perms>
                    }
                  >
                    <div style={{ padding: '10px 12px 0' }}>
                      <p className="hint">{t('iam.admin.roles.menuNote')}</p>
                    </div>
                    {allMenus.length ? (
                      <div className="tree" style={{ paddingTop: 4 }}>
                        {allMenus.map(({ menu, depth }) => (
                          <label
                            className="tree-row"
                            key={menu.id}
                            style={{ paddingLeft: 12 + depth * 16, cursor: 'pointer' }}
                          >
                            <input
                              type="checkbox"
                              checked={grantedMenus.has(menu.id)}
                              disabled={draft.isSuper}
                              onChange={(e) => {
                                const next = new Set(grantedMenus);
                                if (e.target.checked) next.add(menu.id);
                                else next.delete(menu.id);
                                setGrantedMenus(next);
                              }}
                            />
                            <span className="tree-name">{t(menu.i18nCode ?? menu.id)}</span>
                            {menu.type !== 'MENU' && <span className="tag">{menu.type}</span>}
                            <span className="tree-code dim">{menu.i18nCode ?? menu.permCode ?? menu.id}</span>
                          </label>
                        ))}
                      </div>
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
