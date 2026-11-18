// 权限管理（共享）：权限点 CRUD（module:res:action）。
//
// 数据面复用平台 /sys/permissions（平台码 sys:permission:* 鉴权）；前端只填 code + name，
// 平台 SysPermissionService 保存时 applyCode(code) 拆三段落库，保证三段与 code 永不漂移。
//
// 左列是与菜单树同构的权限点树（DIR → MENU 入口码 → BUTTON 动作码，共用 PermTree）；
// 未挂页面的接口级码（平台 sys:* 闸门等）按 module 归组排后并打标。菜单树加载失败
// （如无 sys:menu:list）时静默回退为纯 module 分组，不影响权限 CRUD 主功能。
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimField,
  CimInput,
  CimSelect,
  CimSplitPane,
  EmptyState,
  IconPlus,
  IconTrash,
  InlineAlert,
  Page,
  Panel,
  useConfirm,
  useToast,
} from '@cim/ui';
import { useAdminHasAll, useAdminHttp } from '../context';
import PermTree from '../PermTree';
import { PERM_PERMS, type AdminPermOverrides } from '../perms';
import { buildPermPageMap, groupPermsByPage } from '../permPageMap';
import type { MenuTreeNode, PageResultDto, SysPermissionDto } from '../types';

export interface PermissionsAdminPageProps {
  perms?: AdminPermOverrides;
}

/** 权限管理页（六页共享）。 */
export default function PermissionsAdminPage({ perms }: PermissionsAdminPageProps) {
  const { t } = useTranslation();
  const http = useAdminHttp();
  const toast = useToast();
  const confirm = useConfirm();
  const p = { ...PERM_PERMS, ...perms };
  const hasAll = useAdminHasAll();
  const canRead = hasAll(p.read);
  const canRemove = hasAll(p.remove);
  const canSave = hasAll(p.save);

  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState<Draft | null>(null);

  const listRes = useResource<SysPermissionDto[]>(
    async ({ signal }) => {
      const page = await http.get<PageResultDto<SysPermissionDto>>(
        '/sys/permissions/page',
        { page: 1, size: 500, sort: 'code,asc' },
        { signal },
      );
      return page?.records ?? [];
    },
    { enabled: canRead },
  );

  // 菜单树只用于「页面归属目录」：取不到（无 sys:menu:list）就回退 module 分组，故吞掉 error
  const treeRes = useResource<MenuTreeNode[]>(
    async ({ signal }) =>
      (await http.get<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }, { signal })) ?? [],
    { enabled: canRead },
  );

  const rows = listRes.data ?? [];
  const menuTree = treeRes.error ? [] : treeRes.data ?? [];

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

  async function onSubmit() {
    if (!draft) return;
    const code = draft.code.trim();
    if (code.split(':').length !== 3) {
      toast.error(t('admin.admin.perms.invalidCode'));
      return;
    }
    setBusy(true);
    try {
      const edits = { code, name: draft.name.trim() || null, status: draft.status };
      if (draft.id) {
        await http.put<SysPermissionDto>('/sys/permissions', { ...(draft.origin ?? {}), ...edits });
      } else {
        await http.post<SysPermissionDto>('/sys/permissions', edits);
      }
      toast.success(t('admin.common.saved'));
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!draft?.id) return;
    const ok = await confirm({
      title: t('admin.common.delete'),
      content: draft.code,
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await http.del<void>(`/sys/permissions/${encodeURIComponent(draft.id)}`);
      toast.success(t('admin.common.deleted'));
      setDraft(null);
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.common.loadFailed'));
    } finally {
      setBusy(false);
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
          title={t('admin.admin.perms.count')}
          sub={`${filtered.length} / ${rows.length}`}
          flush
          actions={
            <>
              {canSave && (
                <CimButton
                  size="small"
                  iconOnly
                  title={t('admin.admin.perms.new')}
                  onClick={() => setDraft({ id: '', code: 'xxx:', name: '', status: 'ENABLED', origin: null })}
                icon={<IconPlus width={14} height={14} />}
                />
              )}
              <CimButton size="small" onClick={listRes.refresh} disabled={listRes.loading}>
                {listRes.loading ? t('admin.common.loading') : t('admin.common.refresh')}
              </CimButton>
            </>
          }
        >
          <div style={{ padding: '10px 12px 0' }}>
            <CimInput
              id="perm-kw"
              mono
              value={keyword}
              onChange={setKeyword}
              placeholderKey="admin.common.search"
              allowClear
            />
          </div>

          {listRes.error ? (
            <div style={{ padding: 14 }}>
              <InlineAlert tone="err">{listRes.error.message || t('admin.common.loadFailed')}</InlineAlert>
              <CimButton type="primary" size="small" style={{ marginTop: 10 }} onClick={listRes.refresh}>
                {t('admin.common.refresh')}
              </CimButton>
            </div>
          ) : filtered.length || moduleGroups.length ? (
            <PermTree
              nodes={filteredTree}
              permIdByCode={permIdByCode}
              permById={permById}
              activePermId={draft?.id}
              onPermClick={(perm) =>
                setDraft({ id: perm.id, code: perm.code, name: perm.name ?? '', status: perm.status, origin: perm })
              }
              groups={moduleGroups.map((g) => ({
                key: g.key,
                label: g.label,
                iface: true,
                perms: g.perms,
              }))}
              kw={keyword}
            />
          ) : (
            <EmptyState titleKey="admin.common.none" />
          )}
        </Panel>

        <div>
          {draft ? (
            <Panel
              title={draft.id ? t('admin.common.edit') : t('admin.admin.perms.new')}
              sub={draft.id ?? t('admin.admin.perms.new')}
              actions={
                canRemove && draft.id ? (
                  <CimButton size="small" onClick={() => void onDelete()} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('admin.common.delete')}
                  </CimButton>
                ) : null
              }
            >
              <div className="form-grid">
                <CimField
                  label={
                    <>
                      {t('admin.field.code')} <span className="opt">{t('admin.common.required')}</span>
                    </>
                  }
                  htmlFor="p-code"
                >
                  <CimInput
                    id="p-code"
                    mono
                    placeholder="xxx:yyy:create"
                    value={draft.code}
                    onChange={(v) => setDraft({ ...draft, code: v })}
                  />
                </CimField>
                <CimField label={t('admin.field.name')} htmlFor="p-name">
                  <CimInput
                    id="p-name"
                    value={draft.name}
                    onChange={(v) => setDraft({ ...draft, name: v })}
                  />
                </CimField>
                <CimField
                  label={
                    <>
                      {t('admin.common.enabled')} / {t('admin.common.disabled')}
                    </>
                  }
                  htmlFor="p-status"
                >
                  <CimSelect
                    value={draft.status}
                    onChange={(v) =>
                      setDraft({ ...draft, status: (typeof v === 'string' ? v : 'ENABLED') as 'ENABLED' | 'DISABLED' })
                    }
                    options={[
                      { value: 'ENABLED', labelKey: 'admin.common.enabled' },
                      { value: 'DISABLED', labelKey: 'admin.common.disabled' },
                    ]}
                    allowClear={false}
                  />
                </CimField>
              </div>

              <p className="hint">{t('admin.admin.perms.codeHint')}</p>
              <p className="hint">{t('admin.admin.perms.moduleOwner')}</p>

              <div className="sub-head" style={{ marginTop: 12 }}>
                {canSave && (
                  <CimButton type="primary" htmlType="button" disabled={busy} onClick={() => void onSubmit()}>
                    {busy ? t('admin.common.saving') : t('admin.common.save')}
                  </CimButton>
                )}
                <CimButton size="small" htmlType="button" onClick={() => setDraft(null)}>
                  {t('admin.common.close')}
                </CimButton>
              </div>
            </Panel>
          ) : (
            <Panel title={t('admin.admin.perms.count')}>
              <EmptyState titleKey="admin.admin.perms.new" />
            </Panel>
          )}
        </div>
      </CimSplitPane>
    </Page>
  );
}

interface Draft {
  id: string;
  code: string;
  name: string;
  status: 'ENABLED' | 'DISABLED';
  /** 编辑态保留原实体，提交时整体覆盖，避免丢列。 */
  origin: SysPermissionDto | null;
}
