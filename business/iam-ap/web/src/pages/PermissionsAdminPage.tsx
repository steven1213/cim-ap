import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { MenuTreeNode, PageResultDto, SysPermissionDto } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import PermTree from '@/components/PermTree';
import { useHasAllPermissions } from '@/lib/usePermission';
import { buildPermPageMap, groupPermsByPage } from '@/lib/permPageMap';
import { PERM_LIST, PERM_CREATE, PERM_UPDATE, PERM_DELETE, SYS } from '@/lib/permCodes';
import { IconAlert, IconCheckCircle, IconChevron, IconPlus, IconRefresh, IconTrash } from '@/components/Icons';

/**
 * 权限管理（`/permissions`）：权限点 CRUD（`module:res:action`）。
 *
 * <p>数据面复用平台 `cim-system` 的 `/sys/permissions`（无 `/api/v1` 前缀，走 rootApi 实例）；
 * 平台码 `sys:permission:*` 是接口闸门，控制台码 `iam:perm:*` 是入口闸门（双闸门）。</p>
 *
 * <p><b>为什么不需要手填 module/res/action</b>：平台侧 `SysPermissionService` 在保存时统一调用
 * `applyCode(code)` 拆解三段并落库，保证三段与 `code` 永不漂移——这是「注解里的码」与
 * 「库里的码」能对上的前提。故前端只填 `code` + 名称。</p>
 *
 * <p><b>与菜单树同构的权限点树</b>：左列不再按「页面」平铺分组，而是直接渲染菜单树
 * 层级（DIR → MENU 入口码 → BUTTON 动作码，共用组件 `PermTree` 清单模式，行点击进入
 * 编辑）；未挂页面的接口级码（平台 `sys:*` 闸门等）按 module 归组排后并打标。菜单树
 * 加载失败（如无 `sys:menu:list`）时静默回退为纯 module 分组，不影响权限 CRUD 主功能。</p>
 */
export default function PermissionsAdminPage() {
  const { t } = useTranslation();
  const canRead = useHasAllPermissions([PERM_LIST, SYS.PERM_LIST]);
  const canRemove = useHasAllPermissions([PERM_DELETE, SYS.PERM_REMOVE]);

  const [rows, setRows] = useState<SysPermissionDto[]>([]);
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([]);
  const [keyword, setKeyword] = useState('');
  /** 折叠的接口级分组 key，点击组头切换。 */
  const [closedGroups, setClosedGroups] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [draft, setDraft] = useState<Draft | null>(null);

  const saveCodes = draft?.id ? [PERM_UPDATE, SYS.PERM_SAVE] : [PERM_CREATE, SYS.PERM_SAVE];

  async function load() {
    if (!canRead) return;
    setLoading(true);
    setErr('');
    try {
      const p = await api.rootGet<PageResultDto<SysPermissionDto>>('/sys/permissions/page', {
        page: 1,
        size: 500,
        sort: 'code,asc',
      });
      setRows(p?.records ?? []);
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setLoading(false);
    }
  }

  /** 页面归属目录（可失败：无 sys:menu:list 时回退 module 分组，不拖垮主功能）。 */
  async function loadMenuTree() {
    try {
      const tree = await api.rootGet<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true });
      setMenuTree(tree ?? []);
    } catch {
      setMenuTree([]);
    }
  }

  useEffect(() => {
    load();
    loadMenuTree();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canRead]);

  function flash(text: string) {
    setMsg(text);
    setTimeout(() => setMsg(''), 3000);
  }

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return rows;
    return rows.filter(
      (r) => r.code.toLowerCase().includes(kw) || (r.name ?? '').toLowerCase().includes(kw),
    );
  }, [rows, keyword]);

  /** 按所属页面分组（仅用于取「未挂页面」的 module 组，排在树后）。 */
  const moduleGroups = useMemo(
    () => groupPermsByPage(filtered, buildPermPageMap(menuTree)).filter((g) => g.kind === 'module'),
    [filtered, menuTree],
  );

  /** 清单模式映射：code→ID / ID→行（PermTree 行点击进编辑）。 */
  const permIdByCode = useMemo(() => new Map(rows.map((r) => [r.code, r.id])), [rows]);
  const permById = useMemo(() => new Map(rows.map((r) => [r.id, r])), [rows]);
  const permByCode = useMemo(() => new Map(rows.map((r) => [r.code, r])), [rows]);

  /** 菜单树检索：命中码/名/i18n 键或任一后代即保留分支；检索时 PermTree 强制展开。 */
  const filteredTree = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return menuTree;
    const hit = (n: MenuTreeNode): boolean =>
      (n.menu.permCode ?? '').toLowerCase().includes(kw) ||
      (n.menu.i18nCode ?? '').toLowerCase().includes(kw) ||
      t(n.menu.i18nCode ?? n.menu.id).toLowerCase().includes(kw) ||
      (n.menu.permCode ? (permByCode.get(n.menu.permCode)?.name ?? '') : '')
        .toLowerCase()
        .includes(kw) ||
      (n.children ?? []).some(hit);
    const prune = (nodes: MenuTreeNode[]): MenuTreeNode[] =>
      nodes.filter(hit).map((n) => ({ menu: n.menu, children: prune(n.children ?? []) }));
    return prune(menuTree);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [menuTree, keyword, permByCode]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    setErr('');
    const code = draft.code.trim();
    if (code.split(':').length !== 3) {
      setErr(t('iam.admin.perms.invalidCode'));
      return;
    }
    setBusy(true);
    try {
      const edits = { code, name: draft.name.trim() || null, status: draft.status };
      if (draft.id) {
        await api.rootPut<SysPermissionDto>('/sys/permissions', { ...(draft.origin ?? {}), ...edits });
      } else {
        await api.rootPost<SysPermissionDto>('/sys/permissions', edits);
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
    if (!draft?.id) return;
    if (!window.confirm(`${t('iam.common.confirmDelete')}\n${draft.code}`)) return;
    setErr('');
    setBusy(true);
    try {
      await api.rootDel<void>(`/sys/permissions/${encodeURIComponent(draft.id)}`);
      flash(t('iam.common.deleted'));
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
          className="scroll-y"
          title={t('iam.admin.perms.count')}
          sub={`${filtered.length} / ${rows.length}`}
          flush
          actions={
            <>
              <Perms codes={[PERM_CREATE, SYS.PERM_SAVE]}>
                <button
                  className="icon-btn"
                  title={t('iam.admin.perms.new')}
                  onClick={() => {
                    setErr('');
                    setDraft({ id: '', code: 'iam:', name: '', status: 'ENABLED', origin: null });
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
          <div style={{ padding: '10px 12px 0' }}>
            <input
              className="mono"
              placeholder={t('iam.common.search')}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>
          {filtered.length || moduleGroups.length ? (
            <>
              <PermTree
                nodes={filteredTree}
                permIdByCode={permIdByCode}
                permById={permById}
                activePermId={draft?.id}
                onPermClick={(p) => {
                  setErr('');
                  setDraft({
                    id: p.id,
                    code: p.code,
                    name: p.name ?? '',
                    status: p.status,
                    origin: p,
                  });
                }}
                kw={keyword}
              />
              {moduleGroups.map((g) => {
                const closed = closedGroups.has(g.key);
                return (
                  <div className="tree-group" key={g.key}>
                    <button
                      type="button"
                      className="side-cap"
                      aria-expanded={!closed}
                      onClick={() =>
                        setClosedGroups((prev) => {
                          const next = new Set(prev);
                          if (next.has(g.key)) next.delete(g.key);
                          else next.add(g.key);
                          return next;
                        })
                      }
                    >
                      <span className={'tree-toggle' + (closed ? '' : ' open')}>
                        <IconChevron width={11} height={11} />
                      </span>
                      <span className="cap-text">{g.label}</span>
                      <span className="tag">{t('iam.admin.perms.ifaceTag')}</span>
                      <span className="tag-count">{g.perms.length}</span>
                    </button>
                    {!closed &&
                      g.perms.map((r) => (
                        <div
                          key={r.id}
                          className={'tree-row' + (draft?.id === r.id ? ' on' : '')}
                          onClick={() => {
                            setErr('');
                            setDraft({
                              id: r.id,
                              code: r.code,
                              name: r.name ?? '',
                              status: r.status,
                              origin: r,
                            });
                          }}
                        >
                          <span className="tree-name">{r.name ?? r.code}</span>
                          {r.status === 'DISABLED' && (
                            <span className="tag err">{t('iam.common.disabled')}</span>
                          )}
                          <span className="tree-code dim">{r.code}</span>
                        </div>
                      ))}
                  </div>
                );
              })}
            </>
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
              title={draft.id ? t('iam.common.edit') : t('iam.admin.perms.new')}
              sub={draft.id ?? t('iam.admin.perms.new')}
              actions={
                canRemove && draft.id ? (
                  <button className="btn-ghost btn-sm" onClick={onDelete} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('iam.common.delete')}
                  </button>
                ) : null
              }
            >
              <form onSubmit={onSubmit}>
                <div className="form-grid">
                  <div className="field">
                    <label className="field-label" htmlFor="p-code">
                      {t('iam.field.code')}
                      <span className="opt">{t('iam.common.required')}</span>
                    </label>
                    <input
                      id="p-code"
                      className="mono"
                      placeholder="iam:user:create"
                      value={draft.code}
                      onChange={(e) => setDraft({ ...draft, code: e.target.value })}
                    />
                  </div>
                  <div className="field">
                    <label className="field-label" htmlFor="p-name">
                      {t('iam.field.name')}
                    </label>
                    <input
                      id="p-name"
                      value={draft.name}
                      onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                    />
                  </div>
                  <div className="field">
                    <label className="field-label" htmlFor="p-status">
                      {t('iam.common.enabled')} / {t('iam.common.disabled')}
                    </label>
                    <select
                      id="p-status"
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

                <p className="hint">{t('iam.admin.perms.codeHint')}</p>
                <p className="hint">{t('iam.admin.perms.moduleOwner')}</p>

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
          ) : (
            <Panel title={t('iam.admin.perms.count')}>
              <div className="empty">
                <IconPlus width={22} height={22} />
                <b>{t('iam.admin.perms.new')}</b>
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
  status: 'ENABLED' | 'DISABLED';
  /** 编辑态保留原实体，提交时整体回填，避免丢列。 */
  origin: SysPermissionDto | null;
}
