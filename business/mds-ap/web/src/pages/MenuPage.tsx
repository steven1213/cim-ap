import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimModal,
  CimTag,
  CimTree,
  EmptyState,
  FormGrid,
  InlineAlert,
  Page,
  Panel,
  CimSplitPane,
  useConfirm,
  useToast,
  type CimTreeNode,
  type FormField,
  type FormOption,
} from '@cim/ui';
import * as mdsApi from '@/lib/mdsApi';
import type { MenuTreeNode, SysMenuDto, SysRoleDto, MenuNodeType } from '@/types';
import { SYS } from '@/lib/permCodes';
import { useHasAllPermissions } from '@/lib/usePermission';
import { iconKeys, resolveIcon } from '@/lib/iconRegistry';

/**
 * 菜单管理（`/menus`）：在线维护控制台菜单树（父子/图标/路由/按钮权限码）、
 * 节点显示名（译文落 `sys_i18n`）与角色授权（`sys_role_menu` 覆盖式勾选）。
 *
 * <p><b>数据面在平台</b>：端点来自 `cim-system` 的 `/sys/menus` 与 `/sys/roles`、
 * `cim-i18n-starter` 的 `/i18n/messages`（平台端点已统一 `/api/v1` 前缀）。
 * 鉴权双闸门：平台码 `sys:menu:*` 守端点，MDS 菜单树的 `mds.nav.menus` 节点
 * （permCode=`sys:menu:list`）守入口显隐。</p>
 *
 * <p><b>菜单表不存名称</b>：节点只存 `i18n_code`，名称由 `t(i18n_code)` 渲染——
 * 本页提供「显示名」编辑区（zh-CN / en-US 双语言），保存即写 `sys_i18n`（覆盖式），
 * 前端下次拉译文包自动生效。这正是「菜单在数据库维护」的最后一环。</p>
 */
export default function MenuPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canRead = useHasAllPermissions([SYS.MENU_LIST]);
  const canSave = useHasAllPermissions([SYS.MENU_SAVE]);
  const canRemove = useHasAllPermissions([SYS.MENU_REMOVE]);
  const canGrant = useHasAllPermissions([SYS.ROLE_LIST, SYS.ROLE_GRANT]);
  const canI18n = useHasAllPermissions([SYS.I18N_SAVE]);

  const [expanded, setExpanded] = useState<string[]>([]);
  const [selected, setSelected] = useState<SysMenuDto | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [fieldErr, setFieldErr] = useState<{ i18nCode?: string; permCode?: string }>({});
  const [busy, setBusy] = useState(false);

  // ---------------------------------------------------------------- 菜单树
  const treeRes = useResource<MenuTreeNode[]>(
    ({ signal }) => mdsApi.listMenuTree(signal),
    { enabled: canRead },
  );
  const tree = treeRes.data ?? [];
  const flat = useMemo(() => flatten(tree), [tree]);

  /** 首屏展开一级节点：只做一次，之后用户折叠状态不被覆盖。 */
  const seeded = useRef(false);
  useEffect(() => {
    if (seeded.current || !tree.length) return;
    seeded.current = true;
    setExpanded(tree.map((n) => n.menu.id));
  }, [tree]);

  /** 保存/删除后 `selected` 跟随新对象（整体提交模式要求原节点 + 本次编辑）。 */
  useEffect(() => {
    setSelected((prev) => {
      if (!prev) return prev;
      const fresh = flat.find((f) => f.menu.id === prev.id)?.menu;
      return fresh && fresh !== prev ? fresh : prev;
    });
  }, [flat]);

  const allParentIds = useMemo(() => {
    const ids: string[] = [];
    const walk = (nodes: MenuTreeNode[]) => {
      for (const n of nodes) {
        if (n.children?.length) {
          ids.push(n.menu.id);
          walk(n.children);
        }
      }
    };
    walk(tree);
    return ids;
  }, [tree]);
  const allExpanded = allParentIds.length > 0 && allParentIds.every((id) => expanded.includes(id));

  const treeNodes = useMemo<CimTreeNode[]>(() => {
    const toNode = (n: MenuTreeNode): CimTreeNode => {
      const name = t(n.menu.i18nCode ?? n.menu.id);
      const Icon = n.menu.type === 'BUTTON' ? null : resolveIcon(n.menu.icon);
      const badge =
        n.menu.type === 'BUTTON' ? t('mds.menu.type.button')
          : n.menu.type === 'DIR' ? t('mds.menu.type.dir') : null;
      return {
        key: n.menu.id,
        searchText: `${name} ${n.menu.i18nCode ?? ''} ${n.menu.path ?? ''}`,
        icon: Icon ? <Icon width={14} height={14} /> : undefined,
        labelNode: (
          <span className="tree-node-inline">
            <span className="tree-name">{name}</span>
            {badge && <CimTag size="small">{badge}</CimTag>}
            {n.menu.status === 'DISABLED' && (
              <CimTag size="small" tone="err">
                {t('mds.common.disabled')}
              </CimTag>
            )}
          </span>
        ),
        children: (n.children ?? []).map(toNode),
      };
    };
    return tree.map(toNode);
  }, [tree, t]);

  /** 「上级」下拉：排除按钮节点、自身与全部后代（成环）。 */
  const parentOptions = useMemo<FormOption[]>(() => {
    const blocked = new Set<string>();
    if (draft?.id) {
      const selfNode = findNode(tree, draft.id);
      if (selfNode) collectIds(selfNode, blocked);
    }
    return flat
      .filter((f) => f.menu.type !== 'BUTTON' && !blocked.has(f.menu.id))
      .map((f) => ({
        value: f.menu.id,
        label: (f.depth ? '— '.repeat(f.depth) : '') + t(f.menu.i18nCode ?? f.menu.id),
      }));
  }, [flat, tree, draft?.id, t]);

  const fields = useMemo<Array<FormField<Draft>>>(() => {
    if (!draft) return [];
    const list: Array<FormField<Draft>> = [
      {
        key: 'type',
        labelKey: 'mds.menu.field.type',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'DIR', labelKey: 'mds.menu.type.dir' },
          { value: 'MENU', labelKey: 'mds.menu.type.menu' },
          { value: 'BUTTON', labelKey: 'mds.menu.type.button' },
        ],
      },
      {
        key: 'parentId',
        labelKey: 'mds.menu.field.parent',
        kind: 'select',
        options: parentOptions,
        placeholderKey: 'mds.menu.parent.root',
      },
      {
        key: 'i18nCode',
        labelKey: 'mds.menu.field.i18nCode',
        kind: 'text',
        mono: true,
        placeholder: 'mds.nav.menus',
        required: true,
        hint: t('mds.menu.i18nCodeHint'),
      },
      { key: 'sortNo', labelKey: 'mds.menu.field.sortNo', kind: 'number', min: 0 },
    ];
    if (isNavLike(draft.type)) {
      list.push({ key: 'path', labelKey: 'mds.menu.field.path', kind: 'text', mono: true, placeholder: '/menus' });
    }
    if (draft.type === 'MENU') {
      list.push(
        { key: 'component', labelKey: 'mds.menu.field.component', kind: 'text', mono: true, placeholder: 'MenuPage' },
        {
          key: 'icon',
          labelKey: 'mds.menu.field.icon',
          kind: 'select',
          options: iconKeys().map((k) => ({ value: k, label: k })),
          hint: t('mds.menu.iconHint'),
        },
        { key: 'visible', labelKey: 'mds.menu.field.visible', kind: 'switch', hint: t('mds.menu.visibleHint') },
      );
    }
    list.push(
      {
        key: 'permCode',
        labelKey: 'mds.menu.field.permCode',
        kind: 'text',
        mono: true,
        placeholder: 'mds:reason:view',
        required: draft.type === 'BUTTON',
        hint: t('mds.menu.permHint'),
      },
      {
        key: 'status',
        labelKey: 'mds.menu.field.status',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'ENABLED', labelKey: 'mds.common.enabled' },
          { value: 'DISABLED', labelKey: 'mds.common.disabled' },
        ],
      },
    );
    return list;
  }, [draft, parentOptions, t]);

  // ---------------------------------------------------------------- 显示名（译文）
  /** 当前节点的两语言显示名；整包拉回后取 `i18nCode` 的值。 */
  const [names, setNames] = useState<Record<string, string>>({});
  const [namesBusy, setNamesBusy] = useState(false);
  useEffect(() => {
    if (!selected?.i18nCode) return;
    let alive = true;
    mdsApi
      .fetchI18nBundle('zh-CN')
      .then((zh) => {
        if (!alive) return;
        mdsApi.fetchI18nBundle('en-US').then((en) => {
          if (!alive) return;
          setNames({
            'zh-CN': zh.messages[selected.i18nCode!] ?? '',
            'en-US': en.messages[selected.i18nCode!] ?? '',
          });
        });
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [selected?.id, selected?.i18nCode]);

  async function saveNames() {
    if (!selected?.i18nCode) return;
    setNamesBusy(true);
    try {
      for (const [locale, content] of Object.entries(names)) {
        await mdsApi.saveI18nMessage(locale, selected.i18nCode, content ?? '');
      }
      toast.success(t('mds.menu.names.saved'));
    } catch (e) {
      toast.error((e as { msg?: string })?.msg || t('mds.common.saveFailed'));
    } finally {
      setNamesBusy(false);
    }
  }

  // ---------------------------------------------------------------- 角色授权
  const [grantOpen, setGrantOpen] = useState(false);
  const [roles, setRoles] = useState<SysRoleDto[]>([]);
  const [roleId, setRoleId] = useState<string>('');
  const [checked, setChecked] = useState<string[]>([]);
  const [grantBusy, setGrantBusy] = useState(false);

  const grantTreeNodes = useMemo<CimTreeNode[]>(() => {
    const toNode = (n: MenuTreeNode): CimTreeNode => ({
      key: n.menu.id,
      label: t(n.menu.i18nCode ?? n.menu.id),
      children: (n.children ?? []).map(toNode),
    });
    return tree.map(toNode);
  }, [tree, t]);

  useEffect(() => {
    if (!grantOpen || !canGrant) return;
    mdsApi
      .listRoles()
      .then((rs) => {
        setRoles(rs.filter((r) => r.status !== 'DISABLED'));
        if (rs.length) setRoleId((prev) => prev || rs[0].id);
      })
      .catch((e) => toast.error((e as { msg?: string })?.msg || t('mds.common.loadFailed')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grantOpen]);

  useEffect(() => {
    if (!grantOpen || !roleId) return;
    mdsApi
      .listRoleMenus(roleId)
      .then(setChecked)
      .catch(() => setChecked([]));
  }, [grantOpen, roleId]);

  async function saveGrant() {
    if (!roleId) return;
    setGrantBusy(true);
    try {
      await mdsApi.assignRoleMenus(roleId, checked);
      toast.success(t('mds.menu.grant.saved'));
      setGrantOpen(false);
    } catch (e) {
      toast.error((e as { msg?: string })?.msg || t('mds.common.saveFailed'));
    } finally {
      setGrantBusy(false);
    }
  }

  // ---------------------------------------------------------------- 节点 CRUD
  function selectNode(menu: SysMenuDto) {
    setSelected(menu);
    setFieldErr({});
    setDraft(toDraft(menu));
  }

  function startCreate(parentId: string | null, type: MenuNodeType = 'MENU') {
    setFieldErr({});
    if (parentId === null) setSelected(null);
    setDraft({
      id: '',
      parentId,
      type,
      path: '',
      component: '',
      i18nCode: type === 'BUTTON' ? 'mds.btn.' : '',
      icon: type === 'MENU' ? 'list' : '',
      permCode: '',
      sortNo: type === 'BUTTON' ? 1 : 10,
      visible: type !== 'BUTTON',
      status: 'ENABLED',
    });
  }

  async function onSave() {
    if (!draft) return;
    const errs: typeof fieldErr = {};
    if (!draft.i18nCode.trim()) errs.i18nCode = t('mds.common.required');
    if (draft.type === 'BUTTON' && !draft.permCode.trim()) {
      errs.permCode = t('mds.menu.buttonNeedsPerm');
    }
    setFieldErr(errs);
    if (Object.keys(errs).length) return;

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
        // 以「原节点 + 本次编辑」整体提交：保留基础字段（租户/审计列），避免局部更新丢列
        await mdsApi.updateMenu({ ...(selected ?? {}), ...edits } as SysMenuDto);
      } else {
        const created = await mdsApi.createMenu(edits);
        if (created) {
          setSelected(created);
          setDraft(toDraft(created));
        }
      }
      toast.success(t('mds.common.saved'));
      treeRes.refresh();
    } catch (e) {
      toast.error((e as { msg?: string })?.msg || t('mds.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!selected) return;
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.menu.deleteConfirm', { key: t(selected.i18nCode ?? selected.id) }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await mdsApi.deleteMenu(selected.id);
      toast.success(t('mds.common.deleted'));
      setSelected(null);
      setDraft(null);
      treeRes.refresh();
    } catch (e) {
      toast.error((e as { msg?: string })?.msg || t('mds.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <InlineAlert tone="warn">{t('mds.menu.noPerm')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('mds.menu.tree')}
          sub={t('mds.menu.nodeCount', { n: flat.length })}
          flush
          actions={
            <>
              <CimButton
                size="small"
                onClick={() => setExpanded(allExpanded ? [] : allParentIds)}
                disabled={!allParentIds.length}
              >
                {allExpanded ? t('mds.menu.collapseAll') : t('mds.menu.expandAll')}
              </CimButton>
              {canGrant && (
                <CimButton size="small" onClick={() => setGrantOpen(true)}>
                  {t('mds.menu.grant.open')}
                </CimButton>
              )}
              {canSave && (
                <CimButton bare className="icon-btn" title={t('mds.menu.newRoot')} onClick={() => startCreate(null)}>
                  +
                </CimButton>
              )}
              <CimButton size="small" onClick={treeRes.refresh} disabled={treeRes.loading}>
                {treeRes.loading ? t('mds.common.loading') : t('mds.common.refresh')}
              </CimButton>
            </>
          }
        >
          {treeRes.error ? (
            <div style={{ padding: 14 }}>
              <InlineAlert tone="err">{treeRes.error.message || t('mds.common.loadFailed')}</InlineAlert>
              <CimButton type="primary" size="small" style={{ marginTop: 10 }} onClick={treeRes.refresh}>
                {t('mds.common.retry')}
              </CimButton>
            </div>
          ) : treeNodes.length ? (
            <div className="tree-pad">
              <CimTree
                nodes={treeNodes}
                selectedKeys={selected ? [selected.id] : []}
                onSelect={(key) => {
                  const hit = flat.find((f) => f.menu.id === key)?.menu;
                  if (hit) selectNode(hit);
                }}
                expandedKeys={expanded}
                onExpand={setExpanded}
                showLine
              />
            </div>
          ) : (
            <div style={{ padding: 14 }}>
              <EmptyState titleKey="mds.menu.empty" hintKey="mds.menu.emptyHint" />
            </div>
          )}
        </Panel>

        <div>
          {draft ? (
            <>
              <Panel
                title={draft.id ? t('mds.common.edit') : t('mds.common.add')}
                sub={draft.id ? t(draft.i18nCode || draft.id) : t('mds.menu.newRoot')}
                actions={
                  canRemove && draft.id ? (
                    <CimButton size="small" onClick={onDelete} disabled={busy}>
                      {t('mds.common.delete')}
                    </CimButton>
                  ) : undefined
                }
              >
                <FormGrid
                  fields={fields}
                  value={draft}
                  onChange={(patch) => setDraft({ ...draft, ...patch })}
                  errors={fieldErr}
                  columns={2}
                  disabled={busy}
                />
                {canSave && (
                  <div className="reason-event-actions">
                    <CimButton type="primary" onClick={onSave} disabled={busy}>
                      {busy ? t('mds.common.saving') : t('mds.common.save')}
                    </CimButton>
                  </div>
                )}
              </Panel>

              {draft.id && selected?.i18nCode && (
                <Panel title={t('mds.menu.names.title')} sub={selected.i18nCode}>
                  <div className="menu-names">
                    <label className="menu-name-row">
                      <span className="menu-name-lang">zh-CN</span>
                      <input
                        className="cim-input"
                        value={names['zh-CN'] ?? ''}
                        onChange={(e) => setNames({ ...names, 'zh-CN': e.target.value })}
                        disabled={!canI18n || namesBusy}
                      />
                    </label>
                    <label className="menu-name-row">
                      <span className="menu-name-lang">en-US</span>
                      <input
                        className="cim-input"
                        value={names['en-US'] ?? ''}
                        onChange={(e) => setNames({ ...names, 'en-US': e.target.value })}
                        disabled={!canI18n || namesBusy}
                      />
                    </label>
                    {canI18n && (
                      <div className="reason-event-actions">
                        <CimButton type="primary" size="small" onClick={saveNames} disabled={namesBusy}>
                          {namesBusy ? t('mds.common.saving') : t('mds.menu.names.save')}
                        </CimButton>
                      </div>
                    )}
                    <InlineAlert tone="info">{t('mds.menu.names.hint')}</InlineAlert>
                  </div>
                </Panel>
              )}
            </>
          ) : (
            <Panel title={t('mds.menu.selectNode')}>
              <EmptyState titleKey="mds.menu.selectNode" hintKey="mds.menu.emptyHint" />
            </Panel>
          )}
        </div>
      </CimSplitPane>

      <CimModal
        open={grantOpen}
        title={t('mds.menu.grant.title')}
        onClose={() => setGrantOpen(false)}
        footer={
          <>
            <CimButton onClick={() => setGrantOpen(false)}>{t('mds.common.cancel')}</CimButton>
            <CimButton type="primary" onClick={saveGrant} disabled={grantBusy || !roleId}>
              {grantBusy ? t('mds.common.saving') : t('mds.common.save')}
            </CimButton>
          </>
        }
        width={520}
      >
        <div style={{ marginBottom: 10 }}>
          <select
            className="cim-input"
            value={roleId}
            onChange={(e) => setRoleId(e.target.value)}
            style={{ width: '100%' }}
          >
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {(r.name ?? r.code ?? r.id) + (r.code ? ` (${r.code})` : '')}
              </option>
            ))}
          </select>
        </div>
        <InlineAlert tone="info">{t('mds.menu.grant.hint')}</InlineAlert>
        <div className="tree-pad" style={{ maxHeight: 320, overflowY: 'auto' }}>
          <CimTree nodes={grantTreeNodes} checkable checkedKeys={checked} onCheck={setChecked} showLine />
        </div>
      </CimModal>
    </Page>
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
    parentId: m.parentId ?? null,
    type: m.type,
    path: m.path ?? '',
    component: m.component ?? '',
    i18nCode: m.i18nCode ?? '',
    icon: m.icon ?? '',
    permCode: m.permCode ?? '',
    sortNo: m.sortNo ?? 0,
    visible: m.visible !== false,
    status: m.status ?? 'ENABLED',
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

function findNode(nodes: MenuTreeNode[], id: string): MenuTreeNode | undefined {
  for (const n of nodes) {
    if (n.menu.id === id) return n;
    const hit = findNode(n.children ?? [], id);
    if (hit) return hit;
  }
  return undefined;
}

/** 自身 + 全部后代的 id（成环排除用）。 */
function collectIds(node: MenuTreeNode, out: Set<string>): void {
  out.add(node.menu.id);
  for (const c of node.children ?? []) collectIds(c, out);
}
