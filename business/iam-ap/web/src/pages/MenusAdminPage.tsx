import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimTag,
  CimTree,
  EmptyState,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type CimTreeNode,
  type FormField,
  type FormOption,} from '@cim/ui';
import * as api from '@/lib/api';
import type { MenuTreeNode, SysMenuDto, MenuNodeType } from '@/types';
import { useHasAllPermissions } from '@/lib/usePermission';
import { MENU_LIST, MENU_CREATE, MENU_UPDATE, MENU_DELETE, SYS } from '@/lib/permCodes';
import { iconKeys, resolveIcon } from '@/lib/iconRegistry';
import { IconPlus, IconRefresh, IconTrash } from '@/components/Icons';

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
 *
 * <p><b>「上级」候选要排除自身与后代</b>：把节点挂到自己的子孙下会成环，服务端建树时
 * 会死循环。树里点开的就是这条链，前端比后端更容易知道该排除谁。</p>
 */
export default function MenusAdminPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canRead = useHasAllPermissions([MENU_LIST, SYS.MENU_LIST]);
  const canRemove = useHasAllPermissions([MENU_DELETE, SYS.MENU_REMOVE]);

  const [expanded, setExpanded] = useState<string[]>([]);
  const [selected, setSelected] = useState<SysMenuDto | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [fieldErr, setFieldErr] = useState<{ i18nCode?: string; permCode?: string }>({});
  const [busy, setBusy] = useState(false);

  const treeRes = useResource<MenuTreeNode[]>(
    ({ signal }) => api.rootGet<MenuTreeNode[]>('/sys/menus/tree', { includeButtons: true }, { signal }),
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
   * 保存/删除后重取回来的是新对象，`selected` 要跟着换 —— 更新走
   * 「原节点 + 本次编辑」整体提交（保留租户/审计列），拿旧对象会把刚改的
   * `updatedAt` 等列又写回去。
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
      const Icon = n.menu.type === 'BUTTON' ? null : resolveIcon(n.menu.icon);
      const badge =
        n.menu.type === 'BUTTON' ? t('iam.type.button') : n.menu.type === 'DIR' ? t('iam.type.dir') : null;
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
                {t('iam.common.disabled')}
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
        labelKey: 'iam.field.type',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'DIR', labelKey: 'iam.type.dir' },
          { value: 'MENU', labelKey: 'iam.type.menu' },
          { value: 'BUTTON', labelKey: 'iam.type.button' },
        ],
      },
      {
        key: 'parentId',
        labelKey: 'iam.admin.menus.parent',
        kind: 'select',
        options: parentOptions,
        placeholderKey: 'iam.admin.menus.root',
      },
      {
        key: 'i18nCode',
        labelKey: 'iam.field.i18nCode',
        kind: 'text',
        mono: true,
        placeholder: 'iam.menu.menus',
        required: true,
        hint: t('iam.admin.menus.i18nCodeHint'),
      },
      { key: 'sortNo', labelKey: 'iam.field.sortNo', kind: 'number', min: 0 },
    ];
    if (isNavLike(draft.type)) {
      list.push({
        key: 'path',
        labelKey: 'iam.field.path',
        kind: 'text',
        mono: true,
        placeholder: '/menus',
      });
    }
    if (draft.type === 'MENU') {
      list.push(
        {
          key: 'component',
          labelKey: 'iam.field.component',
          kind: 'text',
          mono: true,
          placeholder: 'MenusAdmin',
        },
        {
          key: 'icon',
          labelKey: 'iam.field.icon',
          kind: 'select',
          options: iconKeys().map((k) => ({ value: k, label: k })),
          hint: t('iam.admin.menus.iconHint'),
        },
        {
          key: 'visible',
          labelKey: 'iam.field.visible',
          kind: 'switch',
          hint: t('iam.admin.menus.visibleHint'),
        },
      );
    }
    list.push(
      {
        key: 'permCode',
        labelKey: 'iam.field.permCode',
        kind: 'text',
        mono: true,
        placeholder: 'iam:menu:create',
        required: draft.type === 'BUTTON',
        hint: t('iam.admin.menus.permHint'),
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
    );
    return list;
  }, [draft, parentOptions, t]);

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
      i18nCode: type === 'BUTTON' ? 'iam.btn.' : '',
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
    if (!draft.i18nCode.trim()) errs.i18nCode = t('iam.common.required');
    if (draft.type === 'BUTTON' && !draft.permCode.trim()) {
      errs.permCode = t('iam.admin.menus.buttonNeedsPerm');
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
        await api.rootPut<SysMenuDto>('/sys/menus', { ...(selected ?? {}), ...edits });
      } else {
        const created = await api.rootPost<SysMenuDto>('/sys/menus', edits);
        if (created) {
          setSelected(created);
          setDraft(toDraft(created));
        }
      }
      toast.success(t('iam.common.saved'));
      treeRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!selected) return;
    const ok = await confirm({
      title: t('iam.common.delete'),
      content: `${t('iam.common.confirmDelete')}\n${t(selected.i18nCode ?? selected.id)}`,
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.rootDel<void>(`/sys/menus/${encodeURIComponent(selected.id)}`);
      toast.success(t('iam.common.deleted'));
      setSelected(null);
      setDraft(null);
      treeRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.common.saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  if (!canRead) {
    return (
      <Page>
        <Panel>
          <div className="alert warn">
            <span>{t('iam.common.noPlatformPerm')}</span>
          </div>
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
      <div className="split">
        <Panel
          className="scroll-y"
          title={t('iam.admin.menus.tree')}
          sub={t('iam.admin.menus.nodeCount', { n: flat.length })}
          flush
          actions={
            <>
              <button
                className="btn-ghost btn-sm"
                onClick={() => setExpanded(allExpanded ? [] : allParentIds)}
                disabled={!allParentIds.length}
              >
                {allExpanded ? t('iam.common.collapseAll') : t('iam.common.expandAll')}
              </button>
              <Can perm={[MENU_CREATE, SYS.MENU_SAVE]}>
                <button
                  className="icon-btn"
                  title={t('iam.admin.menus.newRoot')}
                  onClick={() => startCreate(null)}
                >
                  <IconPlus width={14} height={14} />
                </button>
              </Can>
              <button className="btn-ghost btn-sm" onClick={treeRes.refresh} disabled={treeRes.loading}>
                <IconRefresh width={13} height={13} />
                {treeRes.loading ? t('iam.common.loading') : t('iam.common.refresh')}
              </button>
            </>
          }
        >
          {treeRes.error ? (
            <div style={{ padding: 14 }}>
              <div className="alert err">
                <span>{treeRes.error.message || t('iam.common.loadFailed')}</span>
              </div>
              <button className="btn-sm" style={{ marginTop: 10 }} onClick={treeRes.refresh}>
                {t('iam.common.retry')}
              </button>
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
              <EmptyState titleKey="iam.admin.menus.empty" hintKey="iam.admin.menus.emptyHint" />
            </div>
          )}
        </Panel>

        <div>
          {draft ? (
            <Panel
              title={draft.id ? t('iam.common.edit') : t('iam.common.add')}
              sub={draft.id ? t(draft.i18nCode || draft.id) : t('iam.admin.menus.newRoot')}
              actions={
                canRemove && draft.id ? (
                  <button className="btn-ghost btn-sm" onClick={onDelete} disabled={busy}>
                    <IconTrash width={13} height={13} />
                    {t('iam.common.delete')}
                  </button>
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
                <Can perm={draft.id ? [MENU_UPDATE, SYS.MENU_SAVE] : [MENU_CREATE, SYS.MENU_SAVE]}>
                  <button type="button" className="btn" onClick={onSave} disabled={busy}>
                    {busy ? t('iam.common.saving') : t('iam.common.save')}
                  </button>
                </Can>
                <Can perm={[MENU_CREATE, SYS.MENU_SAVE]}>
                  {/* 两个「新建」是次要动作，收成一组靠右；否则 .sub-head 的 space-between
                      会把三个按钮拉成三段散点 */}
                  <span className="row">
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
                  </span>
                </Can>
              </div>
            </Panel>
          ) : (
            <Panel title={t('iam.admin.menus.selectNode')}>
              <EmptyState titleKey="iam.admin.menus.selectNode" hintKey="iam.admin.menus.emptyHint" />
            </Panel>
          )}
        </div>
      </div>
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
