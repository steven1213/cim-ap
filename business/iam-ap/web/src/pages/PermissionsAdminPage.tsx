import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimSelect,
  EmptyState,
  Page,
  Panel,
  useConfirm,
  useToast,
  CimButton,
  InlineAlert,
  CimField,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { MenuTreeNode, PageResultDto, SysPermissionDto } from '@/types';
import PermTree from '@/components/PermTree';
import { useHasAllPermissions } from '@/lib/usePermission';
import { buildPermPageMap, groupPermsByPage } from '@/lib/permPageMap';
import { PERM_LIST, PERM_CREATE, PERM_UPDATE, PERM_DELETE, SYS } from '@/lib/permCodes';
import { IconPlus, IconTrash } from '@/components/Icons';

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
  const toast = useToast();
  const confirm = useConfirm();
  const canRead = useHasAllPermissions([PERM_LIST, SYS.PERM_LIST]);
  const canRemove = useHasAllPermissions([PERM_DELETE, SYS.PERM_REMOVE]);

  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState<Draft | null>(null);

  const saveCodes = draft?.id ? [PERM_UPDATE, SYS.PERM_SAVE] : [PERM_CREATE, SYS.PERM_SAVE];

  const listRes = useResource<SysPermissionDto[]>(
    async ({ signal }) => {
      const p = await api.rootGet<PageResultDto<SysPermissionDto>>(
        '/sys/permissions/page',
        { page: 1, size: 500, sort: 'code,asc' },
        { signal },
      );
      return p?.records ?? [];
    },
    { enabled: canRead },
  );

  // 菜单树只用于「页面归属目录」：取不到（无 sys:menu:list）就回退 module 分组，故吞掉 error
  const treeRes = useResource<MenuTreeNode[]>(
    ({ signal }) => api.rootGet<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }, { signal }),
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
      toast.error(t('iam.admin.perms.invalidCode'));
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
      toast.success(t('iam.common.saved'));
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!draft?.id) return;
    const ok = await confirm({
      title: t('iam.common.delete'),
      content: draft.code,
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.rootDel<void>(`/sys/permissions/${encodeURIComponent(draft.id)}`);
      toast.success(t('iam.common.deleted'));
      setDraft(null);
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <InlineAlert tone="warn">{t('iam.common.noPlatformPerm')}</InlineAlert>
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
      <div className="split">
        <Panel
          className="scroll-y"
          title={t('iam.admin.perms.count')}
          sub={`${filtered.length} / ${rows.length}`}
          flush
          actions={
            <>
              <Can perm={[PERM_CREATE, SYS.PERM_SAVE]}>
                <CimButton
                  bare
                  className="icon-btn"
                  title={t('iam.admin.perms.new')}
                  onClick={() => setDraft({ id: '', code: 'iam:', name: '', status: 'ENABLED', origin: null })}
                >
                  <IconPlus width={14} height={14} />
                </CimButton>
              </Can>
              <CimButton size="small" onClick={listRes.refresh} disabled={listRes.loading}>
                {listRes.loading ? t('iam.common.loading') : t('iam.common.refresh')}
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
              placeholderKey="iam.common.search"
              allowClear
            />
          </div>

          {listRes.error ? (
            <div style={{ padding: 14 }}>
              <InlineAlert tone="err">{listRes.error.message || t('iam.common.loadFailed')}</InlineAlert>
              <CimButton type="primary" size="small" style={{ marginTop: 10 }} onClick={listRes.refresh}>
                {t('iam.common.refresh')}
              </CimButton>
            </div>
          ) : filtered.length || moduleGroups.length ? (
            <PermTree
              nodes={filteredTree}
              permIdByCode={permIdByCode}
              permById={permById}
              activePermId={draft?.id}
              onPermClick={(p) =>
                setDraft({ id: p.id, code: p.code, name: p.name ?? '', status: p.status, origin: p })
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
            <EmptyState titleKey="iam.common.none" />
          )}
        </Panel>

        <div>
          {draft ? (
            <Panel
              title={draft.id ? t('iam.common.edit') : t('iam.admin.perms.new')}
              sub={draft.id ?? t('iam.admin.perms.new')}
              actions={
                canRemove && draft.id ? (
                  <CimButton size="small" onClick={() => void onDelete()} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('iam.common.delete')}
                  </CimButton>
                ) : null
              }
            >
              <div className="form-grid">
                <CimField
                  label={
                    <>
                      {t('iam.field.code')} <span className="opt">{t('iam.common.required')}</span>
                    </>
                  }
                  htmlFor="p-code"
                >
                  <CimInput
                    id="p-code"
                    mono
                    placeholder="iam:user:create"
                    value={draft.code}
                    onChange={(v) => setDraft({ ...draft, code: v })}
                  />
                </CimField>
                <CimField label={t('iam.field.name')} htmlFor="p-name">
                  <CimInput
                    id="p-name"
                    value={draft.name}
                    onChange={(v) => setDraft({ ...draft, name: v })}
                  />
                </CimField>
                <CimField
                  label={
                    <>
                      {t('iam.common.enabled')} / {t('iam.common.disabled')}
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
                      { value: 'ENABLED', labelKey: 'iam.common.enabled' },
                      { value: 'DISABLED', labelKey: 'iam.common.disabled' },
                    ]}
                    allowClear={false}
                  />
                </CimField>
              </div>

              <p className="hint">{t('iam.admin.perms.codeHint')}</p>
              <p className="hint">{t('iam.admin.perms.moduleOwner')}</p>

              <div className="sub-head" style={{ marginTop: 12 }}>
                <Can perm={saveCodes}>
                  <CimButton type="primary" htmlType="button" disabled={busy} onClick={() => void onSubmit()}>
                    {busy ? t('iam.common.saving') : t('iam.common.save')}
                  </CimButton>
                </Can>
                <CimButton size="small" htmlType="button" onClick={() => setDraft(null)}>
                  {t('iam.common.close')}
                </CimButton>
              </div>
            </Panel>
          ) : (
            <Panel title={t('iam.admin.perms.count')}>
              <EmptyState titleKey="iam.admin.perms.new" />
            </Panel>
          )}
        </div>
      </div>
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
