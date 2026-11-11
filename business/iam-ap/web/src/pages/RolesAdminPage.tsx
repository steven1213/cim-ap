import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimTag,
  CimTabs,
  DataTable,
  EmptyState,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  type TabItem,
  CimButton,
  InlineAlert,
  CimSplitPane,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { MenuTreeNode, PageResultDto, SysMenuDto, SysPermissionDto, SysRoleDto } from '@/types';
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
import { IconPlus, IconRefresh, IconSave, IconTrash } from '@/components/Icons';

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
 *
 * <p><b>授权目录与角色列表分开取</b>：只读角色可能没有 `sys:permission:list` / `sys:menu:list`，
 * 此时角色列表照常可用、只有授权区降级提示——避免一个 403 把整页拖垮。</p>
 */
export default function RolesAdminPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canRead = useHasAllPermissions([ROLE_ADMIN_LIST, SYS.ROLE_LIST]);
  const canRemove = useHasAllPermissions([ROLE_ADMIN_DELETE, SYS.ROLE_REMOVE]);

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
    ({ signal }) =>
      api.rootGet<PageResultDto<SysRoleDto>>(
        '/sys/roles/page',
        { page: 1, size: 500, sort: 'sortNo,asc' },
        { signal },
      ),
    { enabled: canRead },
  );
  const roles = useMemo(() => rolesRes.data?.records ?? [], [rolesRes.data]);

  /** 授权用目录：权限点 + 菜单树（一次取齐，避免「树画好了、码还没到」的中间态）。 */
  const catalogRes = useResource<Catalog>(
    async ({ signal }) => {
      const [permPage, tree] = await Promise.all([
        api.rootGet<PageResultDto<SysPermissionDto>>(
          '/sys/permissions/page',
          { page: 1, size: 500, sort: 'code,asc' },
          { signal },
        ),
        api.rootGet<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }, { signal }),
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
        api.rootGet<string[]>(`/sys/roles/${encodeURIComponent(selectedId)}/permissions`, undefined, { signal }),
        api.rootGet<string[]>(`/sys/roles/${encodeURIComponent(selectedId)}/menus`, undefined, { signal }),
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
    if (!draft.code.trim()) errs.code = t('iam.common.required');
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
        ? await api.rootPut<SysRoleDto>('/sys/roles', { ...(selected ?? {}), ...edits })
        : await api.rootPost<SysRoleDto>('/sys/roles', edits);
      toast.success(t('iam.common.saved'));
      rolesRes.refresh();
      if (saved) selectRole(saved);
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!draft?.id) return;
    const ok = await confirm({
      title: t('iam.common.delete'),
      content: `${t('iam.common.confirmDelete')}\n${draft.code}`,
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.rootDel<void>(`/sys/roles/${encodeURIComponent(draft.id)}`);
      toast.success(t('iam.common.deleted'));
      closeDraft();
      setGrantedPerms(new Set());
      setGrantedMenus(new Set());
      rolesRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
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
      await api.rootPut<void>(
        `/sys/roles/${encodeURIComponent(selected.id)}/permissions`,
        Array.from(grantedPerms),
      );
    } catch {
      failed.push(t('iam.admin.roles.tabPermGrant'));
    }
    try {
      await api.rootPut<void>(`/sys/roles/${encodeURIComponent(selected.id)}/menus`, Array.from(grantedMenus));
    } catch {
      failed.push(t('iam.admin.roles.tabMenuGrant'));
    }
    setBusy(false);
    if (failed.length) toast.error(`${t('iam.common.saveFailed')}：${failed.join(' / ')}`);
    else toast.success(t('iam.common.saved'));
  }

  /** 权限码按「所属页面」分组（菜单树关联）；接口级码按 module 排后；支持本地检索。 */
  const permGroups = useMemo(() => {
    const kw = permKw.trim().toLowerCase();
    const visible = kw
      ? allPerms.filter((p) => p.code.toLowerCase().includes(kw) || (p.name ?? '').toLowerCase().includes(kw))
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
        titleKey: 'iam.field.name',
        render: (r) => (
          <span className="cell-inline">
            <span className="tree-name">{r.name ?? r.code}</span>
            {r.isSuper && (
              <CimTag size="small" tone="run">
                {t('iam.admin.roles.super')}
              </CimTag>
            )}
            {r.status === 'DISABLED' && (
              <CimTag size="small" tone="err">
                {t('iam.common.disabled')}
              </CimTag>
            )}
          </span>
        ),
      },
      { key: 'code', titleKey: 'iam.field.code', mono: true, width: 140, ellipsis: true },
    ],
    [t],
  );

  /** 基本信息：5 个字段，`isSuper` 的提示按开关状态给（关掉时提示没意义）。 */
  const fields = useMemo<Array<FormField<Draft>>>(() => {
    if (!draft) return [];
    return [
      {
        key: 'code',
        labelKey: 'iam.field.code',
        kind: 'text',
        mono: true,
        placeholder: 'IAM_AUDITOR',
        required: true,
        disabled: !!draft.id,
      },
      { key: 'name', labelKey: 'iam.field.name', kind: 'text', placeholderKey: 'iam.field.name' },
      { key: 'sortNo', labelKey: 'iam.field.sortNo', kind: 'number', min: 0 },
      {
        key: 'isSuper',
        labelKey: 'iam.admin.roles.super',
        kind: 'switch',
        hint: draft.isSuper ? t('iam.admin.roles.superHint') : undefined,
      },
      {
        key: 'status',
        labelKey: 'iam.field.status',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'ENABLED', labelKey: 'iam.common.enabled' },
          { value: 'DISABLED', labelKey: 'iam.common.disabled' },
        ],
      },
    ];
  }, [draft, t]);

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
        <CimButton
          size="small"
          htmlType="button"
          style={{ padding: '1px 8px' }}
          onClick={() => toggleMenuPage(node)}
        >
          {t('iam.common.selectAll')}
        </CimButton>
      )}
      groups={ifaceGroups.map((g) => ({ key: g.key, label: g.label, iface: true, perms: g.perms }))}
      groupAction={(g) => {
        const allOn = g.perms.length > 0 && g.perms.every((p) => grantedPerms.has(p.id));
        return (
          <CimButton
            size="small"
            htmlType="button"
            style={{ padding: '1px 8px' }}
            onClick={() => toggleGroup(g)}
          >
            {allOn ? t('iam.common.clearAll') : t('iam.common.selectAll')}
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
          <InlineAlert tone="warn">{t('iam.common.noPlatformPerm')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  const tabItems: TabItem[] = draft
    ? [
        {
          key: 'basic',
          labelKey: 'iam.admin.roles.tabBasic',
          children: (
            <Panel
              title={draft.id ? t('iam.common.edit') : t('iam.admin.roles.new')}
              sub={draft.id ? draft.code : undefined}
              actions={
                canRemove && draft.id ? (
                  <CimButton size="small" onClick={onDelete} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('iam.common.delete')}
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
                <Can perm={draft.id ? [ROLE_ADMIN_UPDATE, SYS.ROLE_SAVE] : [ROLE_ADMIN_CREATE, SYS.ROLE_SAVE]}>
                  <CimButton type="primary" htmlType="button" onClick={onSave} disabled={busy}>
                    {busy ? t('iam.common.saving') : t('iam.common.save')}
                  </CimButton>
                </Can>
                <CimButton size="small" htmlType="button" onClick={closeDraft}>
                  {t('iam.common.close')}
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
      labelKey: 'iam.admin.roles.tabGrant',
      badge: grantedPerms.size,
      children: (
        <Panel
          title={t('iam.admin.roles.tabGrant')}
          sub={`${grantedPerms.size} / ${allPerms.length} · ${grantedMenus.size} / ${allMenus.length}`}
          actions={
            <Can perm={[ROLE_ADMIN_GRANT, SYS.ROLE_GRANT]}>
              <CimButton size="small" onClick={saveGrant} disabled={busy}>
                <IconSave width={13} height={13} />
                {t('iam.admin.roles.grantSave')}
              </CimButton>
            </Can>
          }
        >
          <p className="hint">{t('iam.admin.roles.grantNote')}</p>
          {(catalogErr || grantsRes.error) && (
            <InlineAlert tone="warn">{catalogErr || grantsRes.error?.message}</InlineAlert>
          )}
          {!draft.isSuper && (
            <div className="sub-head">
              <span className="hint">{t('iam.admin.roles.groupByPage')}</span>
              <span className="row" style={{ flexWrap: 'nowrap' }}>
                <CimInput
                  id="role-perm-kw"
                  mono
                  width={200}
                  placeholder={t('iam.common.search')}
                  value={permKw}
                  onChange={setPermKw}
                />
                <CimButton
                  size="small"
                  htmlType="button"
                  onClick={() => setGrantedPerms(new Set(allPerms.map((p) => p.id)))}
                >
                  {t('iam.common.selectAll')}
                </CimButton>
                <CimButton size="small" htmlType="button" onClick={() => setGrantedPerms(new Set())}>
                  {t('iam.common.clearAll')}
                </CimButton>
              </span>
            </div>
          )}
          {menuTree.length ? (
            <div style={{ paddingTop: 4 }}>{grantTree}</div>
          ) : (
            <EmptyState titleKey="iam.common.none" hint={catalogErr || undefined} />
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
          title={t('iam.admin.roles.list')}
          sub={`${roles.length}`}
          flush
          actions={
            <>
              <Can perm={[ROLE_ADMIN_CREATE, SYS.ROLE_SAVE]}>
                <CimButton bare className="icon-btn" title={t('iam.admin.roles.new')} onClick={startCreate}>
                  <IconPlus width={14} height={14} />
                </CimButton>
              </Can>
              <CimButton size="small" onClick={rolesRes.refresh} disabled={rolesRes.loading}>
                <IconRefresh width={13} height={13} />
                {rolesRes.loading ? t('iam.common.loading') : t('iam.common.refresh')}
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
            emptyTitleKey="iam.admin.roles.empty"
            emptyHintKey="iam.admin.roles.emptyHint"
          />
        </Panel>

        <div>
          {draft ? (
            <CimTabs items={tabItems} activeKey={tab} onChange={setTab} />
          ) : (
            <Panel title={t('iam.admin.roles.selectRole')}>
              <EmptyState titleKey="iam.admin.roles.selectRole" hintKey="iam.common.noPlatformPerm" />
            </Panel>
          )}
        </div>
      </CimSplitPane>
    </Page>
  );
}

// ------------------------------------------------------------------ 辅助

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
