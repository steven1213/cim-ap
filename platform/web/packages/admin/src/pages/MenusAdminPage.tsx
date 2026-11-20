// 菜单管理（共享）：维护控制台菜单树（父子节点）、图标与 i18n_code / perm_code 绑定。
//
// 数据面在平台 /sys/menus[/tree]（平台码 sys:menu:* 鉴权）。宿主经 AdminProvider 注入
// http 与 hasAll；有自己入口码的宿主经 perms 覆盖为 [入口码, 平台码] 双闸门。
//
// 「上级」候选要排除自身与后代：把节点挂到自己的子孙下会成环，服务端建树会死循环。
// 更新走「原节点 + 本次编辑」整体提交（保留租户/审计列），拿旧对象会把刚改的列写回去。
import { useEffect, useMemo, useRef, useState, type ComponentType, type SVGProps } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimModal,
  CimSplitPane,
  CimTag,
  CimTree,
  CimTreeNode,
  EmptyState,
  FormField,
  FormGrid,
  FormOption,
  IconPlus,
  IconRefresh,
  IconTrash,
  InlineAlert,
  Page,
  Panel,
  useConfirm,
  useToast,
} from '@cim/ui';
import { useAdminHasAll, useAdminHttp } from '../context';
import { iconKeys, resolveIcon, type IconComponent } from '../iconRegistry';
import { MENU_PERMS, type AdminPermOverrides } from '../perms';
import type { MenuNodeType, MenuTreeNode, SysMenuDto, SysRoleDto } from '../types';

/** 图标注册表接口（宿主可覆盖；缺省用包内置 registry）。 */
export interface MenuIcons {
  /** 注册表全部键（图标选择器候选）。 */
  keys(): string[];
  /** 解析图标标识；未知/为空返回回退图标。 */
  resolve(name: string | null | undefined): ComponentType<SVGProps<SVGSVGElement>>;
}

const DEFAULT_ICONS: MenuIcons = { keys: iconKeys, resolve: resolveIcon };

export interface MenusAdminPageProps {
  perms?: AdminPermOverrides;
  /** 图标注册表（缺省用包内置 registry）。 */
  icons?: MenuIcons;
  /**
   * 显示名（译文）编辑的门禁码，缺省 ['sys:i18n:save']（平台统一写端点）。
   * 显示名走平台 `GET/POST /i18n/messages`，各宿主同构，无需按 ap 覆盖。
   */
  namePerm?: string[];
}

/** 菜单管理页（六页共享）。 */
export default function MenusAdminPage({ perms, icons, namePerm = ['sys:i18n:save'] }: MenusAdminPageProps) {
  const { t } = useTranslation();
  const http = useAdminHttp();
  const toast = useToast();
  const confirm = useConfirm();
  const p = { ...MENU_PERMS, ...perms };
  const hasAll = useAdminHasAll();
  const canRead = hasAll(p.read);
  const canRemove = hasAll(p.remove);
  const canSave = hasAll(p.save);
  const canGrant = hasAll(p.grant ?? ['sys:role:list', 'sys:role:grant']);
  const canI18n = hasAll(namePerm);
  const iconReg = icons ?? DEFAULT_ICONS;

  const [expanded, setExpanded] = useState<string[]>([]);
  const [selected, setSelected] = useState<SysMenuDto | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [fieldErr, setFieldErr] = useState<{ i18nCode?: string; permCode?: string }>({});
  const [busy, setBusy] = useState(false);

  const treeRes = useResource<MenuTreeNode[]>(
    async ({ signal }) =>
      (await http.get<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }, { signal })) ?? [],
    { enabled: canRead },
  );
  const tree = treeRes.data ?? [];
  const flat = useMemo(() => flatten(tree), [tree]);

  /** 首屏展开一级节点：只在第一批数据到达时做一次，之后用户的折叠状态不再被覆盖。 */
  const seeded = useRef(false);
  useEffect(() => {
    if (seeded.current || !tree.length) return;
    seeded.current = true;
    setExpanded(tree.map((n) => n.menu.id));
  }, [tree]);

  /**
   * 保存/删除后重取回来的是新对象，`selected` 要跟着换——更新走
   * 「原节点 + 本次编辑」整体提交（保留租户/审计列）。
   */
  useEffect(() => {
    setSelected((prev) => {
      if (!prev) return prev;
      const fresh = flat.find((f) => f.menu.id === prev.id)?.menu;
      return fresh && fresh !== prev ? fresh : prev;
    });
  }, [flat]);

  /** 全部有子节点的 id（供一键展开/收起）。 */
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
      const Icon: IconComponent | null = n.menu.type === 'BUTTON' ? null : iconReg.resolve(n.menu.icon);
      const badge =
        n.menu.type === 'BUTTON' ? t('admin.type.button') : n.menu.type === 'DIR' ? t('admin.type.dir') : null;
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
                {t('admin.common.disabled')}
              </CimTag>
            )}
          </span>
        ),
        children: (n.children ?? []).map(toNode),
      };
    };
    return tree.map(toNode);
  }, [tree, t, iconReg]);

  /** 「上级」下拉：排除按钮节点、自身与全部后代（成环）。 */
  const parentOptions = useMemo<FormOption[]>(() => {
    const blocked = new Set<string>();
    const selfNode = draft?.id ? findNode(tree, draft.id) : undefined;
    if (selfNode) collectIds(selfNode, blocked);
    return flat
      .filter((f) => f.menu.type !== 'BUTTON' && !blocked.has(f.menu.id))
      .map((f) => ({
        value: f.menu.id,
        label: (f.depth ? '— '.repeat(f.depth) : '') + t(f.menu.i18nCode ?? f.menu.id),
      }));
  }, [flat, tree, draft?.id, t]);

  /** 字段随 `type` 变：目录/菜单有路由，菜单独有组件+图标+可见，按钮必填权限码。 */
  const fields = useMemo<Array<FormField<Draft>>>(() => {
    if (!draft) return [];
    const list: Array<FormField<Draft>> = [
      {
        key: 'type',
        labelKey: 'admin.field.type',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'DIR', labelKey: 'admin.type.dir' },
          { value: 'MENU', labelKey: 'admin.type.menu' },
          { value: 'BUTTON', labelKey: 'admin.type.button' },
        ],
      },
      {
        key: 'parentId',
        labelKey: 'admin.admin.menus.parent',
        kind: 'select',
        options: parentOptions,
        placeholderKey: 'admin.admin.menus.root',
      },
      {
        key: 'i18nCode',
        labelKey: 'admin.field.i18nCode',
        kind: 'text',
        mono: true,
        placeholder: 'admin.menu.menus',
        required: true,
        hint: t('admin.admin.menus.i18nCodeHint'),
      },
      { key: 'sortNo', labelKey: 'admin.field.sortNo', kind: 'number', min: 0 },
    ];
    if (isNavLike(draft.type)) {
      list.push({
        key: 'path',
        labelKey: 'admin.field.path',
        kind: 'text',
        mono: true,
        placeholder: '/menus',
      });
    }
    if (draft.type === 'MENU') {
      list.push(
        {
          key: 'component',
          labelKey: 'admin.field.component',
          kind: 'text',
          mono: true,
          placeholder: 'MenusPage',
        },
        {
          key: 'icon',
          labelKey: 'admin.field.icon',
          kind: 'select',
          options: iconReg.keys().map((k) => ({ value: k, label: k })),
          hint: t('admin.admin.menus.iconHint'),
        },
        {
          key: 'visible',
          labelKey: 'admin.field.visible',
          kind: 'switch',
          hint: t('admin.admin.menus.visibleHint'),
        },
      );
    }
    list.push(
      {
        key: 'permCode',
        labelKey: 'admin.field.permCode',
        kind: 'text',
        mono: true,
        placeholder: 'xxx:yyy:view',
        required: draft.type === 'BUTTON',
        hint: t('admin.admin.menus.permHint'),
      },
      {
        key: 'status',
        labelKey: 'admin.field.status',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'ENABLED', labelKey: 'admin.common.enabled' },
          { value: 'DISABLED', labelKey: 'admin.common.disabled' },
        ],
      },
    );
    return list;
  }, [draft, parentOptions, t, iconReg]);

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
      i18nCode: type === 'BUTTON' ? 'xxx.btn.' : '',
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
    if (!draft.i18nCode.trim()) errs.i18nCode = t('admin.common.required');
    if (draft.type === 'BUTTON' && !draft.permCode.trim()) {
      errs.permCode = t('admin.admin.menus.buttonNeedsPerm');
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
        // 以「原节点 + 本次编辑」整体提交：保留基础字段（租户/审计列等），避免局部更新丢列
        await http.put<SysMenuDto>('/sys/menus', { ...(selected ?? {}), ...edits });
      } else {
        const created = await http.post<SysMenuDto>('/sys/menus', edits);
        if (created) {
          setSelected(created);
          setDraft(toDraft(created));
        }
      }
      toast.success(t('admin.common.saved'));
      treeRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!selected) return;
    const ok = await confirm({
      title: t('admin.common.delete'),
      content: `${t('admin.common.confirmDelete')}\n${t(selected.i18nCode ?? selected.id)}`,
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await http.del<void>(`/sys/menus/${encodeURIComponent(selected.id)}`);
      toast.success(t('admin.common.deleted'));
      setSelected(null);
      setDraft(null);
      treeRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  // ---------------------------------------------------------------- 显示名（译文）
  /** 当前节点的两语言显示名；整包拉回后取 `i18nCode` 的值，保存即覆盖写回。 */
  const [names, setNames] = useState<Record<string, string>>({});
  const [namesBusy, setNamesBusy] = useState(false);
  useEffect(() => {
    if (!selected?.i18nCode) return;
    let alive = true;
    Promise.all([
      http.get<{ messages?: Record<string, string> }>('/i18n/messages', { lang: 'zh-CN' }),
      http.get<{ messages?: Record<string, string> }>('/i18n/messages', { lang: 'en-US' }),
    ])
      .then(([zh, en]) => {
        if (!alive) return;
        setNames({
          'zh-CN': zh?.messages?.[selected.i18nCode!] ?? '',
          'en-US': en?.messages?.[selected.i18nCode!] ?? '',
        });
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.id, selected?.i18nCode]);

  async function saveNames() {
    if (!selected?.i18nCode) return;
    setNamesBusy(true);
    try {
      for (const [locale, content] of Object.entries(names)) {
        await http.post('/i18n/messages', { locale, code: selected.i18nCode, content: content ?? '' });
      }
      toast.success(t('admin.menu.names.saved'));
      treeRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
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
    http
      .get<SysRoleDto[]>('/sys/roles')
      .then((rs) => {
        setRoles((rs ?? []).filter((r) => r.status !== 'DISABLED'));
        if (rs?.length) setRoleId((prev) => prev || rs[0].id);
      })
      .catch((e) => toast.error(e?.msg || t('admin.common.loadFailed')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grantOpen]);

  useEffect(() => {
    if (!grantOpen || !roleId) return;
    http
      .get<string[]>(`/sys/roles/${encodeURIComponent(roleId)}/menus`)
      .then((ids) => setChecked(ids ?? []))
      .catch(() => setChecked([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grantOpen, roleId]);

  async function saveGrant() {
    if (!roleId) return;
    setGrantBusy(true);
    try {
      await http.put<void>(`/sys/roles/${encodeURIComponent(roleId)}/menus`, checked);
      toast.success(t('admin.menu.grant.saved'));
      setGrantOpen(false);
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setGrantBusy(false);
    }
  }

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <InlineAlert tone="warn">{t('admin.common.noPlatformPerm')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('admin.admin.menus.tree')}
          sub={t('admin.admin.menus.nodeCount', { n: flat.length })}
          flush
          actions={
            <>
              <CimButton
                size="small"
                onClick={() => setExpanded(allExpanded ? [] : allParentIds)}
                disabled={!allParentIds.length}
              >
                {allExpanded ? t('admin.common.collapseAll') : t('admin.common.expandAll')}
              </CimButton>
              {canSave && (
                <CimButton type="primary" size="small" onClick={() => startCreate(null)}>
                  {t('admin.common.create')}
                </CimButton>
              )}
              {canGrant && (
                <CimButton size="small" onClick={() => setGrantOpen(true)}>
                  {t('admin.menu.grant.title')}
                </CimButton>
              )}
              <CimButton size="small" onClick={treeRes.refresh} disabled={treeRes.loading}>
                <IconRefresh width={13} height={13} />
                {treeRes.loading ? t('admin.common.loading') : t('admin.common.refresh')}
              </CimButton>
            </>
          }
        >
          {treeRes.error ? (
            <div style={{ padding: 14 }}>
              <InlineAlert tone="err">{treeRes.error.message || t('admin.common.loadFailed')}</InlineAlert>
              <CimButton type="primary" size="small" style={{ marginTop: 10 }} onClick={treeRes.refresh}>
                {t('admin.common.retry')}
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
              <EmptyState titleKey="admin.admin.menus.empty" hintKey="admin.admin.menus.emptyHint" />
            </div>
          )}
        </Panel>

        <div>
          {draft ? (
            <>
              <Panel
                title={draft.id ? t('admin.common.edit') : t('admin.common.add')}
                sub={draft.id ? t(draft.i18nCode || draft.id) : t('admin.admin.menus.newRoot')}
                actions={
                  canRemove && draft.id ? (
                    <CimButton size="small" onClick={onDelete} disabled={busy}>
                      <IconTrash width={13} height={13} />
                      {t('admin.common.delete')}
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

              <div className="sub-head" style={{ marginTop: 12 }}>
                {canSave && (
                  <CimButton type="primary" htmlType="button" onClick={onSave} disabled={busy}>
                    {busy ? t('admin.common.saving') : t('admin.common.save')}
                  </CimButton>
                )}
                {canSave && (
                  <span className="row">
                    {draft.type !== 'BUTTON' && (
                      <CimButton
                        size="small"
                        htmlType="button"
                        onClick={() => startCreate(draft.id || selected?.id || null, 'MENU')}
                      >
                        <IconPlus width={13} height={13} />
                        {t('admin.admin.menus.addChild')}
                      </CimButton>
                    )}
                    <CimButton
                      size="small"
                      htmlType="button"
                      onClick={() => startCreate(draft.id || selected?.id || null, 'BUTTON')}
                    >
                      <IconPlus width={13} height={13} />
                      {t('admin.type.button')}
                    </CimButton>
                  </span>
                )}
              </div>
            </Panel>

            {draft.id && selected?.i18nCode && (
              <Panel title={t('admin.menu.names.title')} sub={selected.i18nCode}>
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
                        {namesBusy ? t('admin.common.saving') : t('admin.menu.names.save')}
                      </CimButton>
                    </div>
                  )}
                  <InlineAlert tone="info">{t('admin.menu.names.hint')}</InlineAlert>
                </div>
              </Panel>
            )}
            </>
          ) : (
            <Panel title={t('admin.admin.menus.selectNode')}>
              <EmptyState titleKey="admin.admin.menus.selectNode" hintKey="admin.admin.menus.emptyHint" />
            </Panel>
          )}
        </div>
      </CimSplitPane>

      <CimModal
        open={grantOpen}
        title={t('admin.menu.grant.title')}
        onClose={() => setGrantOpen(false)}
        footer={
          <>
            <CimButton onClick={() => setGrantOpen(false)}>{t('admin.common.cancel')}</CimButton>
            <CimButton type="primary" onClick={saveGrant} disabled={grantBusy || !roleId}>
              {grantBusy ? t('admin.common.saving') : t('admin.common.save')}
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
        <InlineAlert tone="info">{t('admin.menu.grant.hint')}</InlineAlert>
        <div className="tree-pad" style={{ maxHeight: 320, overflowY: 'auto' }}>
          <CimTree nodes={grantTreeNodes} checkable checkedKeys={checked} onCheck={setChecked} showLine />
        </div>
      </CimModal>
    </Page>
  );
}

// ---------------------------------------------------------------- 辅助

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

function findNode(nodes: MenuTreeNode[], id: string): MenuTreeNode | undefined {
  for (const n of nodes) {
    if (n.menu.id === id) return n;
    const hit = findNode(n.children ?? [], id);
    if (hit) return hit;
  }
  return undefined;
}

/** 自身 + 全部后代的 id。 */
function collectIds(node: MenuTreeNode, out: Set<string>): void {
  out.add(node.menu.id);
  for (const c of node.children ?? []) collectIds(c, out);
}
