// 角色管理（共享）：角色 CRUD + 授权。
//
// 授权交互为单棵菜单树（一次保存），存储仍是两张独立授权表：sys_role_perm（接口权限）
// 与 sys_role_menu（侧栏可见）。树上勾选菜单＝写菜单表，勾选入口码/按钮码＝写权限表。
// 「能调用接口」与「看得见菜单」解耦语义完整保留（可只勾其一）。
//
// 授权目录与角色列表分开取：只读角色可能没有 sys:permission:list / sys:menu:list，
// 此时角色列表照常可用、只有授权区降级提示——避免一个 403 把整页拖垮。
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimInput,
  CimSplitPane,
  CimTabs,
  CimTag,
  DataTable,
  DataTableColumn,
  EmptyState,
  FormField,
  FormGrid,
  IconRefresh,
  IconSave,
  IconTrash,
  InlineAlert,
  Page,
  Panel,
  TabItem,
  useConfirm,
  useToast,
} from '@cim/ui';
import { useAdminHasAll, useAdminHttp } from '../context';
import PermTree from '../PermTree';
import { ROLE_PERMS, type AdminPermOverrides } from '../perms';
import { buildPermPageMap, groupPermsByPage } from '../permPageMap';
import type { MenuTreeNode, PageResultDto, SysMenuDto, SysPermissionDto, SysRoleDto } from '../types';

export interface RolesAdminPageProps {
  perms?: AdminPermOverrides;
}

/** 角色管理页（六页共享）。 */
export default function RolesAdminPage({ perms }: RolesAdminPageProps) {
  const { t } = useTranslation();
  const http = useAdminHttp();
  const toast = useToast();
  const confirm = useConfirm();
  const p = { ...ROLE_PERMS, ...perms };
  const hasAll = useAdminHasAll();
  const canRead = hasAll(p.read);
  const canRemove = hasAll(p.remove);
  const canSave = hasAll(p.save);
  const canGrant = hasAll(p.grant);

  const [selected, setSelected] = useState<SysRoleDto | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [tab, setTab] = useState('basic');
  const [busy, setBusy] = useState(false);
  const [fieldErr, setFieldErr] = useState<{ code?: string }>({});
  /** 授权区：码/名检索（本地过滤）。 */
  const [permKw, setPermKw] = useState('');

  const [grantedPerms, setGrantedPerms] = useState<Set<string>>(new Set());
  const [grantedMenus, setGrantedMenus] = useState<Set<string>>(new Set());

  const selectedId = selected?.id ?? '';

  const rolesRes = useResource<PageResultDto<SysRoleDto>>(
    async ({ signal }) =>
      (await http.get<PageResultDto<SysRoleDto>>(
        '/sys/roles/page',
        { page: 1, size: 500, sort: 'sortNo,asc' },
        { signal },
      )) ?? { records: [], total: 0, page: 0, size: 0 },
    { enabled: canRead },
  );
  const roles = useMemo(() => rolesRes.data?.records ?? [], [rolesRes.data]);

  /** 授权用目录：权限点 + 菜单树（一次取齐，避免「树画好了、码还没到」的中间态）。 */
  const catalogRes = useResource<Catalog>(
    async ({ signal }) => {
      const [permPage, tree] = await Promise.all([
        http.get<PageResultDto<SysPermissionDto>>(
          '/sys/permissions/page',
          { page: 1, size: 500, sort: 'code,asc' },
          { signal },
        ),
        http.get<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }, { signal }),
      ]);
      const nodes = tree ?? [];
      return { perms: permPage?.records ?? [], tree: nodes, flat: flatten(nodes) };
    },
    { enabled: canRead },
  );
  const allPerms = useMemo(() => catalogRes.data?.perms ?? [], [catalogRes.data]);
  const menuTree = useMemo(() => catalogRes.data?.tree ?? [], [catalogRes.data]);
  const allMenus = useMemo(() => catalogRes.data?.flat ?? [], [catalogRes.data]);
  /** 目录拉不到时只降级授权区，角色列表与基本信息仍可用。 */
  const catalogErr = catalogRes.error?.message ?? '';

  const grantsRes = useResource<{ perms: string[]; menus: string[] }>(
    async ({ signal }) => {
      const [perms, menus] = await Promise.all([
        http.get<string[]>(`/sys/roles/${encodeURIComponent(selectedId)}/permissions`, undefined, { signal }),
        http.get<string[]>(`/sys/roles/${encodeURIComponent(selectedId)}/menus`, undefined, { signal }),
      ]);
      return { perms: perms ?? [], menus: menus ?? [] };
    },
    { deps: [selectedId], enabled: !!selectedId },
  );

  /** 回包 → 可编辑副本（勾选态由用户改，不能在 render 里从回包派生）。 */
  useEffect(() => {
    if (!selectedId || !grantsRes.data) {
      setGrantedPerms(new Set());
      setGrantedMenus(new Set());
      return;
    }
    setGrantedPerms(new Set(grantsRes.data.perms));
    setGrantedMenus(new Set(grantsRes.data.menus));
  }, [grantsRes.data, selectedId]);

  /** 保存后列表重取回来的是新对象，跟着换：更新走「原节点 + 本次编辑」整体提交。 */
  useEffect(() => {
    setSelected((prev) => {
      if (!prev) return prev;
      const fresh = roles.find((r) => r.id === prev.id);
      return fresh && fresh !== prev ? fresh : prev;
    });
  }, [roles]);

  function selectRole(role: SysRoleDto) {
    setSelected(role);
    setDraft({ id: role.id, code: role.code, name: role.name ?? '', isSuper: role.isSuper, sortNo: role.sortNo ?? 0, status: role.status });
    setFieldErr({});
    setTab('basic');
  }

  function startCreate() {
    setSelected(null);
    setFieldErr({});
    setTab('basic');
    setGrantedPerms(new Set());
    setGrantedMenus(new Set());
    setDraft({ id: '', code: '', name: '', isSuper: false, sortNo: 10, status: 'ENABLED' });
  }

  function closeDraft() {
    setDraft(null);
    setSelected(null);
    setFieldErr({});
  }

  async function onSave() {
    if (!draft) return;
    const errs: typeof fieldErr = {};
    if (!draft.code.trim()) errs.code = t('admin.common.required');
    setFieldErr(errs);
    if (Object.keys(errs).length) return;

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
        ? await http.put<SysRoleDto>('/sys/roles', { ...(selected ?? {}), ...edits })
        : await http.post<SysRoleDto>('/sys/roles', edits);
      toast.success(t('admin.common.saved'));
      rolesRes.refresh();
      if (saved) selectRole(saved);
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!draft?.id) return;
    const ok = await confirm({
      title: t('admin.common.delete'),
      content: `${t('admin.common.confirmDelete')}\n${draft.code}`,
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await http.del<void>(`/sys/roles/${encodeURIComponent(draft.id)}`);
      toast.success(t('admin.common.deleted'));
      closeDraft();
      setGrantedPerms(new Set());
      setGrantedMenus(new Set());
      rolesRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  /**
   * 一次保存两张授权表（顺序执行、各自幂等覆盖写）。
   *
   * <p>两张表分属两个平台端点、无跨表事务——任一失败时把失败的那张表名列进提示，
   * 成功的那张不回滚（覆盖写可重试，重按一次保存即可补齐）。</p>
   */
  async function saveGrant() {
    if (!selected) return;
    setBusy(true);
    const failed: string[] = [];
    try {
      await http.put<void>(
        `/sys/roles/${encodeURIComponent(selected.id)}/permissions`,
        Array.from(grantedPerms),
      );
    } catch {
      failed.push(t('admin.admin.roles.tabPermGrant'));
    }
    try {
      await http.put<void>(`/sys/roles/${encodeURIComponent(selected.id)}/menus`, Array.from(grantedMenus));
    } catch {
      failed.push(t('admin.admin.roles.tabMenuGrant'));
    }
    setBusy(false);
    if (failed.length) toast.error(`${t('admin.common.saveFailed')}：${failed.join(' / ')}`);
    else toast.success(t('admin.common.saved'));
  }

  /** 权限码按「所属页面」分组（菜单树关联）；接口级码按 module 排后；支持本地检索。 */
  const permGroups = useMemo(() => {
    const kw = permKw.trim().toLowerCase();
    const visible = kw
      ? allPerms.filter((perm) => perm.code.toLowerCase().includes(kw) || (perm.name ?? '').toLowerCase().includes(kw))
      : allPerms;
    return groupPermsByPage(visible, buildPermPageMap(menuTree));
  }, [allPerms, menuTree, permKw]);

  /** 组级全选/清空：组内已全选则清空，否则全选。 */
  function toggleGroup(g: { perms: SysPermissionDto[] }) {
    const next = new Set(grantedPerms);
    const allOn = g.perms.length > 0 && g.perms.every((perm) => next.has(perm.id));
    for (const perm of g.perms) {
      if (allOn) next.delete(perm.id);
      else next.add(perm.id);
    }
    setGrantedPerms(next);
  }

  /** 授权树用：permCode → 权限行 ID（授权接口按权限 ID 读写；树上只有码）。 */
  const permIdByCode = useMemo(() => {
    const m = new Map<string, string>();
    for (const perm of allPerms) m.set(perm.code, perm.id);
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
  }, [menuTree, permKw, t]);

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
  const ifaceGroups = useMemo(() => permGroups.filter((g) => g.kind === 'module'), [permGroups]);

  const roleColumns = useMemo<Array<DataTableColumn<SysRoleDto>>>(
    () => [
      {
        key: 'name',
        titleKey: 'admin.field.name',
        render: (r) => (
          <span className="cell-inline">
            <span className="tree-name">{r.name ?? r.code}</span>
            {r.isSuper && (
              <CimTag size="small" tone="run">
                {t('admin.admin.roles.super')}
              </CimTag>
            )}
            {r.status === 'DISABLED' && (
              <CimTag size="small" tone="err">
                {t('admin.common.disabled')}
              </CimTag>
            )}
          </span>
        ),
      },
      { key: 'code', titleKey: 'admin.field.code', mono: true, width: 140, ellipsis: true },
    ],
    [t],
  );

  /** 基本信息：5 个字段，`isSuper` 的提示按开关状态给（关掉时提示没意义）。 */
  const fields = useMemo<Array<FormField<Draft>>>(() => {
    if (!draft) return [];
    return [
      {
        key: 'code',
        labelKey: 'admin.field.code',
        kind: 'text',
        mono: true,
        placeholder: 'AUDITOR',
        required: true,
        disabled: !!draft.id,
      },
      { key: 'name', labelKey: 'admin.field.name', kind: 'text', placeholderKey: 'admin.field.name' },
      { key: 'sortNo', labelKey: 'admin.field.sortNo', kind: 'number', min: 0 },
      {
        key: 'isSuper',
        labelKey: 'admin.admin.roles.super',
        kind: 'switch',
        hint: draft.isSuper ? t('admin.admin.roles.superHint') : undefined,
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
    ];
  }, [draft, t]);

  /**
   * 授权树（共用组件 PermTree）：DIR 折叠 → MENU 行（菜单可见 + 入口码 + 页面全选）
   * → 按钮码嵌套其下。按钮码以菜单树 BUTTON 节点的 permCode 为准，经 permIdByCode
   * 换算成权限 ID 写入 sys_role_perm；码未在权限表注册时行灰显（显示码但勾不了）。
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
        <CimButton
          size="small"
          htmlType="button"
          style={{ padding: '1px 8px' }}
          onClick={() => toggleMenuPage(node)}
        >
          {t('admin.common.selectAll')}
        </CimButton>
      )}
      groups={ifaceGroups.map((g) => ({ key: g.key, label: g.label, iface: true, perms: g.perms }))}
      groupAction={(g) => {
        const allOn = g.perms.length > 0 && g.perms.every((perm) => grantedPerms.has(perm.id));
        return (
          <CimButton
            size="small"
            htmlType="button"
            style={{ padding: '1px 8px' }}
            onClick={() => toggleGroup(g)}
          >
            {allOn ? t('admin.common.clearAll') : t('admin.common.selectAll')}
          </CimButton>
        );
      }}
      kw={permKw}
    />
  );

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <InlineAlert tone="warn">{t('admin.common.noPlatformPerm')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  const tabItems: TabItem[] = draft
    ? [
        {
          key: 'basic',
          labelKey: 'admin.admin.roles.tabBasic',
          children: (
            <Panel
              title={draft.id ? t('admin.common.edit') : t('admin.admin.roles.new')}
              sub={draft.id ? draft.code : undefined}
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
                <CimButton size="small" htmlType="button" onClick={closeDraft}>
                  {t('admin.common.close')}
                </CimButton>
              </div>
            </Panel>
          ),
        },
      ]
    : [];

  if (draft?.id) {
    tabItems.push({
      key: 'grant',
      labelKey: 'admin.admin.roles.tabGrant',
      badge: grantedPerms.size,
      children: (
        <Panel
          title={t('admin.admin.roles.tabGrant')}
          sub={`${grantedPerms.size} / ${allPerms.length} · ${grantedMenus.size} / ${allMenus.length}`}
          actions={
            canGrant ? (
              <CimButton size="small" onClick={saveGrant} disabled={busy}>
                <IconSave width={13} height={13} />
                {t('admin.admin.roles.grantSave')}
              </CimButton>
            ) : undefined
          }
        >
          <p className="hint">{t('admin.admin.roles.grantNote')}</p>
          {(catalogErr || grantsRes.error) && (
            <InlineAlert tone="warn">{catalogErr || grantsRes.error?.message}</InlineAlert>
          )}
          {!draft.isSuper && (
            <div className="sub-head">
              <span className="hint">{t('admin.admin.roles.groupByPage')}</span>
              <span className="row" style={{ flexWrap: 'nowrap' }}>
                <CimInput
                  id="role-perm-kw"
                  mono
                  width={200}
                  placeholder={t('admin.common.search')}
                  value={permKw}
                  onChange={setPermKw}
                />
                <CimButton
                  size="small"
                  htmlType="button"
                  onClick={() => setGrantedPerms(new Set(allPerms.map((perm) => perm.id)))}
                >
                  {t('admin.common.selectAll')}
                </CimButton>
                <CimButton size="small" htmlType="button" onClick={() => setGrantedPerms(new Set())}>
                  {t('admin.common.clearAll')}
                </CimButton>
              </span>
            </div>
          )}
          {menuTree.length ? (
            <div style={{ paddingTop: 4 }}>{grantTree}</div>
          ) : (
            <EmptyState titleKey="admin.common.none" hint={catalogErr || undefined} />
          )}
        </Panel>
      ),
    });
  }

  return (
    <Page>
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('admin.admin.roles.list')}
          sub={`${roles.length}`}
          flush
          actions={
            <>
              {canSave && (
                <CimButton type="primary" size="small" onClick={startCreate}>
                  {t('admin.common.create')}
                </CimButton>
              )}
              <CimButton size="small" onClick={rolesRes.refresh} disabled={rolesRes.loading}>
                <IconRefresh width={13} height={13} />
                {rolesRes.loading ? t('admin.common.loading') : t('admin.common.refresh')}
              </CimButton>
            </>
          }
        >
          <DataTable
            flat
            size="small"
            rows={roles}
            columns={roleColumns}
            rowKey={(r) => r.id}
            loading={rolesRes.loading}
            error={rolesRes.error?.message}
            onRetry={rolesRes.refresh}
            onRowClick={selectRole}
            rowClassName={(r) => (r.id === selected?.id ? 'ant-table-row-selected' : '')}
            emptyTitleKey="admin.admin.roles.empty"
            emptyHintKey="admin.admin.roles.emptyHint"
          />
        </Panel>

        <div>
          {draft ? (
            <CimTabs items={tabItems} activeKey={tab} onChange={setTab} />
          ) : (
            <Panel title={t('admin.admin.roles.selectRole')}>
              <EmptyState titleKey="admin.admin.roles.selectRole" hintKey="admin.common.noPlatformPerm" />
            </Panel>
          )}
        </div>
      </CimSplitPane>
    </Page>
  );
}

// ---------------------------------------------------------------- 辅助

interface Draft {
  id: string;
  code: string;
  name: string;
  isSuper: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
}

interface Catalog {
  perms: SysPermissionDto[];
  tree: MenuTreeNode[];
  flat: { menu: SysMenuDto; depth: number }[];
}

function flatten(nodes: MenuTreeNode[], depth = 0): { menu: SysMenuDto; depth: number }[] {
  const out: { menu: SysMenuDto; depth: number }[] = [];
  for (const n of nodes) {
    out.push({ menu: n.menu, depth });
    out.push(...flatten(n.children ?? [], depth + 1));
  }
  return out;
}
