import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  FormGrid,
  IconApps,
  IconChevron,
  IconList,
  IconMonitor,
  IconTree,
  Page,
  useConfirm,
  useToast,
  type FormField,
  type FormOption,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { Area, AreaGroup, Bay, Fab, Site, Stage } from '@/types';

/**
 * 位置拓扑（Site → Fab → Area → Bay → Stage）——场地主数据的唯一维护入口。
 *
 * <p><b>为什么是一页树而不是三个平铺 CRUD 页</b>：层级数据天然是树，用户心智也是树；
 * 平铺页互相切换、凭记忆选父级的动线是「建 Area 找不到 Fab」的根源（location-design.md §8.3）。</p>
 *
 * <p><b>编辑锁动线</b>：点「编辑」先抢锁（失败提示「谁正在编辑」，不进表单）；
 * 保存成功由后端同事务清锁；取消/关闭弹窗由前端显式释放——释放按 occupied_by 条件，
 * 对已清锁的记录无害，所以关闭路径统一释放。</p>
 */

const NO_SITES: Site[] = [];
const NO_FABS: Fab[] = [];
const NO_AREAS: Area[] = [];
const NO_BAYS: Bay[] = [];
const NO_STAGES: Stage[] = [];
const NO_GROUPS: AreaGroup[] = [];

/** 树上的物理层（areaGroup/technology 只是锁/表的 type，不是位置树的层）。 */
type Level = Exclude<
  api.LocationType,
  | 'areaGroup'
  | 'technology'
  | 'product'
  | 'equipmentClass'
  | 'toolGroup'
  | 'equipment'
  | 'capability'
  | 'stockerBank'
  | 'edcSet'
  | 'edcReportTpl'
  | 'edcPlan'
  | 'carrier'
  | 'reticle'
  | 'inspectionStd'
  | 'consumable'
>;

/** 每层的直接子层（stage 是位置树叶子）。 */
const CHILD_LEVEL: Record<Level, Level | null> = {
  site: 'fab',
  fab: 'area',
  area: 'bay',
  bay: 'stage',
  stage: null,
};

/** 每层的权限码组（<Can> 显隐用）。形状取 site 组、值放宽为 string（各层码不同）。 */
type PermGroup = Record<'view' | 'create' | 'update' | 'delete', string>;
const PERM: Record<Level, PermGroup> = {
  site: MDS_PERMS.site,
  fab: MDS_PERMS.fab,
  area: MDS_PERMS.area,
  bay: MDS_PERMS.bay,
  stage: MDS_PERMS.stage,
};

/**
 * 每层元数据。i18n 键必须是<b>完整字面量</b>且属性名以 Key 结尾——
 * check-i18n 扫描器只认字面量（t()/xxxKey），动态拼接键会变成「已定义但无人引用」的假死键。
 */
const LEVEL_META: Record<
  Level,
  { Icon: typeof IconApps; oneKey: string; codeKey: string; nameKey: string; createNewKey: string }
> = {
  site: {
    Icon: IconApps,
    oneKey: 'mds.site.one',
    codeKey: 'mds.site.code',
    nameKey: 'mds.site.name',
    createNewKey: 'mds.site.createNew',
  },
  fab: {
    Icon: IconMonitor,
    oneKey: 'mds.fab.one',
    codeKey: 'mds.fab.code',
    nameKey: 'mds.fab.name',
    createNewKey: 'mds.fab.createNew',
  },
  area: {
    Icon: IconTree,
    oneKey: 'mds.area.one',
    codeKey: 'mds.area.code',
    nameKey: 'mds.area.name',
    createNewKey: 'mds.area.createNew',
  },
  bay: {
    Icon: IconApps,
    oneKey: 'mds.bay.one',
    codeKey: 'mds.bay.code',
    nameKey: 'mds.bay.name',
    createNewKey: 'mds.bay.createNew',
  },
  stage: {
    Icon: IconMonitor,
    oneKey: 'mds.stage.one',
    codeKey: 'mds.stage.code',
    nameKey: 'mds.stage.name',
    createNewKey: 'mds.stage.createNew',
  },
};

interface TopoNode {
  level: Level;
  id: string;
  code: string;
  name: string;
  /** 直接父层编码（site 为 null）；fab=siteCode、area=fabCode、bay=areaCode、stage=bayCode。 */
  parentCode: string | null;
  /** 所属 Fab 编码（fab 层为自身编码，site 为 null）——bay/stage 表单换归属时锁定 Fab 用。 */
  fabCode: string | null;
  /** 所属区域组 id（仅 area 层有值；§9.1 树内虚拟分节的归组依据）。 */
  areaGroupId?: string | null;
  /** 默认 Hold 处置部门（仅 area 层；V36 Hold Setting）。 */
  holdDept?: string | null;
  enabled: boolean;
  occupied: boolean;
  occupiedBy: string | null;
  children: TopoNode[];
}

export default function TopologyPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounced(keyword, 200);
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [editing, setEditing] = useState<TopoNode | null>(null);
  const [creating, setCreating] = useState<Level | null>(null);
  const [groupFilter, setGroupFilter] = useState('');
  const [creatingGroup, setCreatingGroup] = useState<{ fabCode: string } | null>(null);
  const [createInGroup, setCreateInGroup] = useState<{ fabNode: TopoNode; group: AreaGroup } | null>(null);
  const [editingGroup, setEditingGroup] = useState<AreaGroup | null>(null);

  const can = {
    site: useHasPermission(MDS_PERMS.site.view),
    fab: useHasPermission(MDS_PERMS.fab.view),
    area: useHasPermission(MDS_PERMS.area.view),
    bay: useHasPermission(MDS_PERMS.bay.view),
    stage: useHasPermission(MDS_PERMS.stage.view),
  };

  // 五层各自拉取：没有对应 view 权限的层不请求（避免整页 403），树里按空层展示
  const sitesRes = useResource(({ signal }) => api.listSites(undefined, signal), {
    enabled: can.site,
  });
  const fabsRes = useResource(({ signal }) => api.listFabs(undefined, undefined, signal), {
    enabled: can.fab,
  });
  const areasRes = useResource(({ signal }) => api.listAreas(undefined, undefined, signal), {
    enabled: can.area,
  });
  const baysRes = useResource(({ signal }) => api.listBays(undefined, signal), { enabled: can.bay });
  const stagesRes = useResource(({ signal }) => api.listStages(undefined, signal), {
    enabled: can.stage,
  });
  // 区域组：Area 统计口径分类（§9.1）；权限复用 area:view，无权限时不请求
  const groupsRes = useResource(({ signal }) => api.listAreaGroups(undefined, signal), {
    enabled: can.area,
  });
  // 部门选项（IAM 组织架构 DEPT；V36 起部门真源=组织架构）：属性面板展示名映射用
  const deptRes = useResource(({ signal }) => api.listDeptOptions(signal));

  const sites = sitesRes.data ?? NO_SITES;
  const fabs = fabsRes.data ?? NO_FABS;
  const areas = areasRes.data ?? NO_AREAS;
  const bays = baysRes.data ?? NO_BAYS;
  const stages = stagesRes.data ?? NO_STAGES;
  const groups = groupsRes.data ?? NO_GROUPS;

  const tree = useMemo<TopoNode[]>(() => {
    const fabsBySite = new Map<string, Fab[]>();
    for (const f of fabs) {
      const list = fabsBySite.get(f.siteCode) ?? [];
      list.push(f);
      fabsBySite.set(f.siteCode, list);
    }
    const areasByFab = new Map<string, Area[]>();
    for (const a of areas) {
      const list = areasByFab.get(a.fabCode) ?? [];
      list.push(a);
      areasByFab.set(a.fabCode, list);
    }
    const baysByArea = new Map<string, Bay[]>();
    for (const b of bays) {
      const key = `${b.fabCode}/${b.areaCode}`;
      const list = baysByArea.get(key) ?? [];
      list.push(b);
      baysByArea.set(key, list);
    }
    const stagesByBay = new Map<string, Stage[]>();
    for (const s of stages) {
      const key = `${s.fabCode}/${s.bayCode}`;
      const list = stagesByBay.get(key) ?? [];
      list.push(s);
      stagesByBay.set(key, list);
    }

    const areaNode = (a: Area): TopoNode => ({
      level: 'area',
      id: a.id,
      code: a.areaCode,
      name: a.areaName,
      parentCode: a.fabCode,
      fabCode: a.fabCode,
      areaGroupId: a.areaGroupId,
      holdDept: a.holdDept,
      enabled: a.enabled,
      occupied: a.occupied,
      occupiedBy: a.occupiedBy,
      children: (baysByArea.get(`${a.fabCode}/${a.areaCode}`) ?? []).map((b) => ({
        level: 'bay',
        id: b.id,
        code: b.bayCode,
        name: b.bayName,
        parentCode: b.areaCode,
        fabCode: b.fabCode,
        enabled: b.enabled,
        occupied: b.occupied,
        occupiedBy: b.occupiedBy,
        children: (stagesByBay.get(`${b.fabCode}/${b.bayCode}`) ?? []).map((s) => ({
          level: 'stage',
          id: s.id,
          code: s.stageCode,
          name: s.stageName,
          parentCode: s.bayCode,
          fabCode: s.fabCode,
          enabled: s.enabled,
          occupied: s.occupied,
          occupiedBy: s.occupiedBy,
          children: [],
        })),
      })),
    });

    const fabNode = (f: Fab): TopoNode => ({
      level: 'fab',
      id: f.id,
      code: f.fabCode,
      name: f.fabName,
      parentCode: f.siteCode,
      fabCode: f.fabCode,
      enabled: f.enabled,
      occupied: f.occupied,
      occupiedBy: f.occupiedBy,
      children: (areasByFab.get(f.fabCode) ?? []).map(areaNode),
    });

    return sites.map((s) => ({
      level: 'site' as const,
      id: s.id,
      code: s.siteCode,
      name: s.siteName,
      parentCode: null,
      fabCode: null,
      enabled: s.enabled,
      occupied: s.occupied,
      occupiedBy: s.occupiedBy,
      children: (fabsBySite.get(s.siteCode) ?? []).map(fabNode),
    }));
  }, [sites, fabs, areas, bays, stages]);

  const filteredTree = useMemo(() => {
    const base = groupFilter ? filterByGroup(tree, groupFilter) : tree;
    return filterTree(base, debouncedKeyword);
  }, [tree, debouncedKeyword, groupFilter]);
  const selected = useMemo(() => findNode(tree, selectedKey), [tree, selectedKey]);
  // 组选中 key = group:<id>（与树节点 key 空间不冲突）
  const selectedGroup = useMemo(
    () =>
      selectedKey?.startsWith('group:')
        ? (groups.find((g) => `group:${g.id}` === selectedKey) ?? null)
        : null,
    [groups, selectedKey],
  );

  const toggle = useCallback((key: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  const refresh = useCallback(() => {
    sitesRes.refresh();
    fabsRes.refresh();
    areasRes.refresh();
    baysRes.refresh();
    stagesRes.refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 编辑先抢锁：抢不到提示「谁正在编辑」，不进表单。 */
  const openEdit = useCallback(
    async (node: TopoNode) => {
      try {
        await api.claimLock(node.level, node.id);
      } catch (e) {
        toast.error(e instanceof Error ? e.message : String(e));
        return;
      }
      setSelectedKey(`${node.level}:${node.id}`);
      setEditing(node);
    },
    [toast],
  );

  /** 关闭编辑弹窗：显式释放（服务端按 occupied_by 条件清，对已清锁的记录无害）。 */
  const closeEdit = useCallback(() => {
    if (editing) {
      void api.releaseLock(editing.level, editing.id).catch(() => {});
    }
    setEditing(null);
  }, [editing]);

  const doDelete = useCallback(
    async (node: TopoNode) => {
      const ok = await confirm({
        title: t('mds.common.delete'),
        contentKey: 'mds.common.confirmDelete',
        danger: true,
        okKey: 'mds.common.delete',
      });
      if (!ok) return;
      const deleter: Record<Level, (id: string) => Promise<void>> = {
        site: api.deleteSite,
        fab: api.deleteFab,
        area: api.deleteArea,
        bay: api.deleteBay,
        stage: api.deleteStage,
      };
      try {
        await deleter[node.level](node.id);
        toast.success(t('mds.common.deleted'));
        setSelectedKey(null);
      } catch (e) {
        toast.error(e instanceof Error ? e.message : String(e));
        return;
      }
      refresh();
    },
    [t, confirm, toast, refresh],
  );

  /** 组编辑先抢锁（与节点同动线）；取消显式释放——释放按 occupied_by 条件，对已清锁无害。 */
  const openEditGroup = useCallback(
    async (group: AreaGroup) => {
      try {
        await api.claimLock('areaGroup', group.id);
      } catch (e) {
        toast.error(e instanceof Error ? e.message : String(e));
        return;
      }
      setEditingGroup(group);
    },
    [toast],
  );

  const closeEditGroup = useCallback(() => {
    if (editingGroup) {
      void api.releaseLock('areaGroup', editingGroup.id).catch(() => {});
    }
    setEditingGroup(null);
  }, [editingGroup]);

  const doDeleteGroup = useCallback(
    async (group: AreaGroup) => {
      const ok = await confirm({
        title: t('mds.common.delete'),
        contentKey: 'mds.group.confirmDelete',
        danger: true,
        okKey: 'mds.common.delete',
      });
      if (!ok) return;
      try {
        await api.deleteAreaGroup(group.id);
        toast.success(t('mds.common.deleted'));
      } catch (e) {
        toast.error(e instanceof Error ? e.message : String(e));
        return;
      }
      refresh();
    },
    [t, confirm, toast, refresh],
  );

  return (
    <Page>
      <div className="topo">
        <aside className="topo-tree">
          <div className="topo-toolbar">
            <input
              className="topo-search"
              placeholder={t('mds.common.search')}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            {groups.length > 0 && (
              <select
                className="topo-group-filter"
                value={groupFilter}
                onChange={(e) => setGroupFilter(e.target.value)}
              >
                <option value="">{t('mds.group.filterAll')}</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.groupName} · {g.fabCode}
                  </option>
                ))}
              </select>
            )}
            <Can perm={MDS_PERMS.site.create}>
              <CimButton type="primary" size="small" onClick={() => setCreating('site')}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          </div>
          <div className="topo-nodes">
            {filteredTree.length === 0 && !sitesRes.loading ? (
              <p className="dim topo-empty">{t('mds.topo.empty')}</p>
            ) : (
              filteredTree.map((n) => (
                <TopoBranch
                  key={`${n.level}:${n.id}`}
                  node={n}
                  depth={0}
                  collapsed={collapsed}
                  onToggle={toggle}
                  onSelect={setSelectedKey}
                  selectedKey={selectedKey}
                  groups={groups}
                />
              ))
            )}
          </div>
        </aside>

        <section className="topo-detail">
          {selectedGroup ? (
            <GroupDetail
              group={selectedGroup}
              memberCount={areas.filter((a) => a.areaGroupId === selectedGroup.id).length}
              onEdit={() => void openEditGroup(selectedGroup)}
              onDelete={() => void doDeleteGroup(selectedGroup)}
              onCreateArea={() => {
                const fabNode = findFabByCode(tree, selectedGroup.fabCode);
                if (fabNode) setCreateInGroup({ fabNode, group: selectedGroup });
              }}
            />
          ) : selected ? (
            <NodeDetail
              node={selected}
              groups={groups}
              depts={deptRes.data ?? []}
              onEdit={() => void openEdit(selected)}
              onDelete={() => void doDelete(selected)}
              onCreateChild={() => setCreating(CHILD_LEVEL[selected.level])}
              onCreateGroup={() => setCreatingGroup({ fabCode: selected.code })}
            />
          ) : (
            <p className="dim topo-empty">{t('mds.topo.selectHint')}</p>
          )}
        </section>
      </div>

      {creating && (
        <NodeFormModal
          level={creating}
          initial={null}
          parentHint={creating && selected && CHILD_LEVEL[selected.level] === creating ? selected : null}
          sites={sites}
          fabs={fabs}
          areas={areas}
          bays={bays}
          stages={stages}
          groups={groups}
          onClose={() => setCreating(null)}
          onDone={() => {
            setCreating(null);
            toast.success(t('mds.common.created'));
            refresh();
          }}
        />
      )}
      {editing && (
        <NodeFormModal
          level={editing.level}
          initial={editing}
          parentHint={null}
          sites={sites}
          fabs={fabs}
          areas={areas}
          bays={bays}
          stages={stages}
          groups={groups}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            refresh();
          }}
        />
      )}
      {creatingGroup && (
        <GroupFormModal
          initial={null}
          fabCode={creatingGroup.fabCode}
          onClose={() => setCreatingGroup(null)}
          onDone={() => {
            setCreatingGroup(null);
            toast.success(t('mds.common.created'));
            refresh();
          }}
        />
      )}
      {editingGroup && (
        <GroupFormModal
          initial={editingGroup}
          fabCode={editingGroup.fabCode}
          onClose={closeEditGroup}
          onDone={() => {
            setEditingGroup(null);
            toast.success(t('mds.common.updated'));
            refresh();
          }}
        />
      )}
      {createInGroup && (
        <NodeFormModal
          level="area"
          initial={null}
          parentHint={createInGroup.fabNode}
          initialGroupId={createInGroup.group.id}
          sites={sites}
          fabs={fabs}
          areas={areas}
          bays={bays}
          stages={stages}
          groups={groups}
          onClose={() => setCreateInGroup(null)}
          onDone={() => {
            setCreateInGroup(null);
            toast.success(t('mds.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 弹窗标题键（属性名以 Key 结尾的对象字面量，供 check-i18n 扫描；三元里写裸字面量会被判「无人引用」）。 */
const GROUP_TITLE = {
  editKey: 'mds.group.rename',
  createKey: 'mds.group.createNew',
} as const;

/** 分组新建/编辑弹窗（§9.1：组是 Area 的管理切面，无独立页面；编辑锁由页面层抢/放）。 */
function GroupFormModal({
  initial,
  fabCode,
  onClose,
  onDone,
}: {
  initial: AreaGroup | null;
  fabCode: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.groupCode ?? '',
    name: initial?.groupName ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'code' as const, labelKey: 'mds.group.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.group.name', required: true },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark', span: 2 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    [initial],
  );

  async function submit() {
    if (!form.name.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateAreaGroup(initial.id, {
          groupName: form.name.trim(),
          remark: form.remark || null,
          enabled: form.enabled,
        });
      } else {
        await api.createAreaGroup({
          groupCode: form.code.trim(),
          fabCode,
          groupName: form.name.trim(),
          remark: form.remark || null,
          enabled: form.enabled,
        });
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? GROUP_TITLE.editKey : GROUP_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <p className="dim topo-fab-hint">
        {t('mds.fab.one')}：{fabCode}
      </p>
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

function filterTree(nodes: TopoNode[], kw: string): TopoNode[] {
  if (!kw) return nodes;
  const q = kw.toLowerCase();
  const walk = (n: TopoNode): TopoNode | null => {
    const children = n.children.map(walk).filter((x): x is TopoNode => x !== null);
    const self =
      n.code.toLowerCase().includes(q) || n.name.toLowerCase().includes(q) || children.length > 0;
    return self ? { ...n, children } : null;
  };
  return nodes.map(walk).filter((x): x is TopoNode => x !== null);
}

/** 按区域组过滤（§9.1 触点③）：保留命中 Area 及其祖先链。 */
function filterByGroup(nodes: TopoNode[], groupId: string): TopoNode[] {
  const walk = (n: TopoNode): TopoNode | null => {
    const children = n.children.map(walk).filter((x): x is TopoNode => x !== null);
    const self = n.level === 'area' && n.areaGroupId === groupId;
    return self || children.length > 0 ? { ...n, children } : null;
  };
  return nodes.map(walk).filter((x): x is TopoNode => x !== null);
}

function findNode(nodes: TopoNode[], key: string | null): TopoNode | null {
  if (!key) return null;
  for (const n of nodes) {
    if (`${n.level}:${n.id}` === key) return n;
    const hit = findNode(n.children, key);
    if (hit) return hit;
  }
  return null;
}

/** 按编码找 Fab 节点（组详情「新增区域」要拿 Fab 作为表单 parent 提示）。 */
function findFabByCode(nodes: TopoNode[], code: string): TopoNode | null {
  for (const n of nodes) {
    if (n.level === 'fab' && n.code === code) return n;
    const hit = findFabByCode(n.children, code);
    if (hit) return hit;
  }
  return null;
}

function TopoBranch({
  node,
  depth,
  collapsed,
  onToggle,
  onSelect,
  selectedKey,
  groups,
}: {
  node: TopoNode;
  depth: number;
  collapsed: Set<string>;
  onToggle: (key: string) => void;
  onSelect: (key: string) => void;
  selectedKey: string | null;
  groups: AreaGroup[];
}) {
  const { t } = useTranslation();
  const key = `${node.level}:${node.id}`;
  const isCollapsed = collapsed.has(key);
  // Fab 层一律走分节渲染：有组分节展示，无组也有尾部「+ 分组」入口（否则第一个分组永远无处创建）
  const showSections = node.level === 'fab';
  const hasChildren = node.children.length > 0 || showSections;
  const { Icon } = LEVEL_META[node.level];
  const selected = selectedKey === key;

  return (
    <div className="topo-branch">
      <div
        className={`topo-row${selected ? ' selected' : ''}${node.enabled ? '' : ' off'}`}
        style={{ paddingLeft: 8 + depth * 18 }}
        onClick={() => onSelect(key)}
      >
        <span
          className={`topo-caret${isCollapsed ? '' : ' open'}`}
          onClick={(e) => {
            e.stopPropagation();
            if (hasChildren) onToggle(key);
          }}
        >
          {hasChildren ? <IconChevron width={12} height={12} /> : <span className="topo-dot" />}
        </span>
        <Icon width={14} height={14} />
        <span className="topo-code">{node.code}</span>
        <span className="topo-name">{node.name}</span>
        {!node.enabled && <span className="topo-badge dim-badge">{t('mds.topo.disabled')}</span>}
        {node.occupied && <span className="topo-badge warn-badge">{t('mds.topo.lockedShort')}</span>}
      </div>
      {hasChildren && !isCollapsed && (
        <div className="topo-children">
          {showSections ? (
            renderFabChildren(node, depth, groups, collapsed, onToggle, onSelect, selectedKey, t)
          ) : (
            node.children.map((c) => (
              <TopoBranch
                key={`${c.level}:${c.id}`}
                node={c}
                depth={depth + 1}
                collapsed={collapsed}
                onToggle={onToggle}
                onSelect={onSelect}
                selectedKey={selectedKey}
                groups={groups}
              />
            ))
          )}
        </div>
      )}
    </div>
  );
}

/** Fab 下 Area 按区域组分节（§9.1）：组头可折叠、可选中进右侧详情；操作统一在详情面板，树内零按钮。 */
function renderFabChildren(
  fab: TopoNode,
  depth: number,
  groups: AreaGroup[],
  collapsed: Set<string>,
  onToggle: (key: string) => void,
  onSelect: (key: string) => void,
  selectedKey: string | null,
  t: (key: string) => string,
): ReactNode[] {
  const childDepth = depth + 1;
  const parts: ReactNode[] = [];
  const fabGroups = groups.filter((g) => g.fabCode === fab.code);
  const byGroup = new Map<string, TopoNode[]>();
  const ungrouped: TopoNode[] = [];
  for (const c of fab.children) {
    if (c.areaGroupId) {
      const list = byGroup.get(c.areaGroupId) ?? [];
      list.push(c);
      byGroup.set(c.areaGroupId, list);
    } else {
      ungrouped.push(c);
    }
  }

  const branchOf = (c: TopoNode, d: number) => (
    <TopoBranch
      key={`${c.level}:${c.id}`}
      node={c}
      depth={d}
      collapsed={collapsed}
      onToggle={onToggle}
      onSelect={onSelect}
      selectedKey={selectedKey}
      groups={groups}
    />
  );

  for (const g of fabGroups) {
    const members = byGroup.get(g.id) ?? [];
    const key = `grp:${fab.id}:${g.id}`;
    const isCollapsed = collapsed.has(key);
    const groupKey = `group:${g.id}`;
    const isGroupSelected = selectedKey === groupKey;
    parts.push(
      <div className="topo-branch" key={key}>
        <div
          className={`topo-group-row${isGroupSelected ? ' selected' : ''}`}
          style={{ paddingLeft: 8 + childDepth * 18 }}
          onClick={() => onSelect(groupKey)}
          title={t('mds.group.one')}
        >
          <span
            className={`topo-caret${isCollapsed ? '' : ' open'}`}
            onClick={(e) => {
              e.stopPropagation();
              onToggle(key);
            }}
          >
            <IconChevron width={12} height={12} />
          </span>
          <IconList width={13} height={13} />
          <span className="topo-name topo-group-name">{g.groupName}</span>
          {!g.enabled && <span className="topo-badge dim-badge">{t('mds.topo.disabled')}</span>}
          <span className="topo-badge dim-badge">{members.length}</span>
        </div>
        {!isCollapsed && members.map((c) => branchOf(c, childDepth + 1))}
      </div>,
    );
  }

  if (ungrouped.length > 0 && fabGroups.length > 0) {
    parts.push(
      <div
        key="topo-ungrouped"
        className="topo-group-row static"
        style={{ paddingLeft: 8 + childDepth * 18 }}
      >
        <span className="topo-name topo-group-name">{t('mds.topo.ungrouped')}</span>
        <span className="topo-badge dim-badge">{ungrouped.length}</span>
      </div>,
    );
  }
  for (const c of ungrouped) {
    parts.push(branchOf(c, childDepth));
  }
  return parts;
}

function NodeDetail({
  node,
  groups,
  depts,
  onEdit,
  onDelete,
  onCreateChild,
  onCreateGroup,
}: {
  node: TopoNode;
  groups: AreaGroup[];
  depts: api.DeptOption[];
  onEdit: () => void;
  onDelete: () => void;
  onCreateChild: () => void;
  onCreateGroup: () => void;
}) {
  const { t } = useTranslation();
  const meta = LEVEL_META[node.level];
  const { Icon } = meta;
  const child = CHILD_LEVEL[node.level];
  return (
    <div className="topo-info">
      <header className="topo-info-head">
        <Icon width={18} height={18} />
        <strong>{node.code}</strong>
        <span className="topo-info-name">{node.name}</span>
        {!node.enabled && <span className="topo-badge dim-badge">{t('mds.topo.disabled')}</span>}
      </header>
      <dl className="topo-meta">
        <dt>{t('mds.topo.level')}</dt>
        <dd>{t(meta.oneKey)}</dd>
        <dt>{t('mds.topo.status')}</dt>
        <dd>{node.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}</dd>
        <dt>{t('mds.topo.editLock')}</dt>
        <dd>
          {node.occupied
            ? t('mds.topo.lockedBy', { user: node.occupiedBy ?? '' })
            : t('mds.topo.lockFree')}
        </dd>
        {node.level === 'area' && (
          <>
            <dt>{t('mds.area.group')}</dt>
            <dd>{groups.find((g) => g.id === node.areaGroupId)?.groupName ?? '—'}</dd>
            <dt>{t('mds.area.holdDept')}</dt>
            <dd>{depts.find((d) => d.code === node.holdDept)?.name ?? node.holdDept ?? '—'}</dd>
          </>
        )}
      </dl>
      <div className="topo-actions">
        <Can perm={PERM[node.level].update}>
          <CimButton size="small" onClick={onEdit}>
            {t('mds.common.edit')}
          </CimButton>
        </Can>
        {child && (
          <Can perm={PERM[child].create}>
            <CimButton size="small" type="primary" onClick={onCreateChild}>
              {t('mds.topo.addChild', { child: t(LEVEL_META[child].oneKey) })}
            </CimButton>
          </Can>
        )}
        {node.level === 'fab' && (
          <Can perm={MDS_PERMS.area.create}>
            <CimButton size="small" onClick={onCreateGroup}>
              {t('mds.common.create')}
            </CimButton>
          </Can>
        )}
        <Can perm={PERM[node.level].delete}>
          <CimButton size="small" danger onClick={onDelete}>
            {t('mds.common.delete')}
          </CimButton>
        </Can>
      </div>
    </div>
  );
}

/** 区域分组详情（与 NodeDetail 同构）：操作统一在右侧面板，树内零按钮。 */
function GroupDetail({
  group,
  memberCount,
  onEdit,
  onDelete,
  onCreateArea,
}: {
  group: AreaGroup;
  memberCount: number;
  onEdit: () => void;
  onDelete: () => void;
  onCreateArea: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="topo-info">
      <header className="topo-info-head">
        <IconList width={18} height={18} />
        <strong>{group.groupCode}</strong>
        <span className="topo-info-name">{group.groupName}</span>
        {!group.enabled && <span className="topo-badge dim-badge">{t('mds.topo.disabled')}</span>}
      </header>
      <dl className="topo-meta">
        <dt>{t('mds.topo.level')}</dt>
        <dd>{t('mds.group.one')}</dd>
        <dt>{t('mds.topo.status')}</dt>
        <dd>{group.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}</dd>
        <dt>{t('mds.fab.one')}</dt>
        <dd>{group.fabCode}</dd>
        <dt>{t('mds.group.memberCount')}</dt>
        <dd>{memberCount}</dd>
      </dl>
      <div className="topo-actions">
        <Can perm={MDS_PERMS.area.create}>
          <CimButton size="small" type="primary" onClick={onCreateArea}>
            {/* 与 NodeDetail 子层按钮同文案（「新增区域」），不用的 createNew（「新建区域」）会造成两套叫法 */}
            {t('mds.topo.addChild', { child: t(LEVEL_META.area.oneKey) })}
          </CimButton>
        </Can>
        <Can perm={MDS_PERMS.area.update}>
          <CimButton size="small" onClick={onEdit}>
            {t('mds.common.edit')}
          </CimButton>
        </Can>
        <Can perm={MDS_PERMS.area.delete}>
          <CimButton size="small" danger onClick={onDelete}>
            {t('mds.common.delete')}
          </CimButton>
        </Can>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- 表单弹窗

interface NodeForm {
  code: string;
  parentCode: string;
  name: string;
  extra: string;
  extra2: string;
  extra3: string;
  /** 所属区域组 id（仅 area；可空，§9.1）。 */
  groupId: string;
  /** 默认 Hold 处置部门（仅 area；V36 Hold Setting，挂 mds.owning_dept 字典）。 */
  holdDept: string;
  remark: string;
  enabled: boolean;
}

/** 编辑态回填用原始行（树节点只带编码/名称/状态，属性值得从列表行取）。 */
function extrasOf(
  level: Level,
  raw: Site | Fab | Area | Bay | Stage | null,
): { extra: string; extra2: string; extra3: string; groupId: string; holdDept: string; remark: string } {
  if (!raw) return { extra: '', extra2: '', extra3: '', groupId: '', holdDept: '', remark: '' };
  switch (level) {
    case 'site': {
      const r = raw as Site;
      return { extra: r.address ?? '', extra2: r.contactPerson ?? '', extra3: r.contactPhone ?? '', groupId: '', holdDept: '', remark: r.remark ?? '' };
    }
    case 'fab': {
      const r = raw as Fab;
      return { extra: r.fabType ?? '', extra2: r.amhsType ?? '', extra3: r.cleanGrade ?? '', groupId: '', holdDept: '', remark: r.remark ?? '' };
    }
    case 'area': {
      const r = raw as Area;
      return { extra: r.areaType ?? '', extra2: '', extra3: '', groupId: r.areaGroupId ?? '', holdDept: r.holdDept ?? '', remark: r.remark ?? '' };
    }
    default: {
      const r = raw as Bay | Stage;
      return { extra: '', extra2: '', extra3: '', groupId: '', holdDept: '', remark: r.remark ?? '' };
    }
  }
}

/** 每层父级选择器的 i18n 键与候选项来源。属性名以 Key 结尾供扫描器识别。 */
const PARENT_META: Record<
  Exclude<Level, 'site'>,
  { labelKey: string; placeholderKey: string }
> = {
  fab: { labelKey: 'mds.fab.site', placeholderKey: 'mds.fab.sitePlaceholder' },
  area: { labelKey: 'mds.area.fab', placeholderKey: 'mds.area.fabPlaceholder' },
  bay: { labelKey: 'mds.bay.area', placeholderKey: 'mds.bay.areaPlaceholder' },
  stage: { labelKey: 'mds.stage.bay', placeholderKey: 'mds.stage.bayPlaceholder' },
};

/**
 * 五层共用的表单弹窗。`parentHint` 非 null 即「从树上发起的新建」：
 * 父级自动带入并锁定（动线 §8.3）；编辑态父级可选（换归属，服务端守门）。
 * bay/stage 的 fabCode 不可改（后端口径），由 initial/parentHint 的 fabCode 带出。
 */
function NodeFormModal({
  level,
  initial,
  parentHint,
  initialGroupId,
  sites,
  fabs,
  areas,
  bays,
  stages,
  groups,
  onClose,
  onDone,
}: {
  level: Level;
  initial: TopoNode | null;
  parentHint: TopoNode | null;
  /** 从组头「+」发起：所属组预选并锁定（§9.1 动线）。 */
  initialGroupId?: string | null;
  sites: Site[];
  fabs: Fab[];
  areas: Area[];
  bays: Bay[];
  stages: Stage[];
  groups: AreaGroup[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof NodeForm, string>>>({});

  // 部门选项（IAM 组织架构 DEPT；V36 起部门真源=组织架构）：Hold 处置部门下拉用
  const deptRes = useResource(({ signal }) => api.listDeptOptions(signal));
  const deptOptions = useMemo<FormOption[]>(
    () => (deptRes.data ?? []).map((d) => ({ value: d.code, label: d.name, hint: d.code })),
    [deptRes.data],
  );

  const meta = LEVEL_META[level];
  // Fab 语境：bay/stage 的归属候选项按它过滤（fabCode 后端不可改）
  const fabContext = parentHint?.fabCode ?? initial?.fabCode ?? null;

  // 编辑态：从列表行回填属性值（树节点不带 address/fabType 等扩展属性）
  const raw = useMemo<Site | Fab | Area | Bay | Stage | null>(() => {
    if (!initial) return null;
    switch (initial.level) {
      case 'site':
        return sites.find((s) => s.id === initial.id) ?? null;
      case 'fab':
        return fabs.find((f) => f.id === initial.id) ?? null;
      case 'area':
        return areas.find((a) => a.id === initial.id) ?? null;
      case 'bay':
        return bays.find((b) => b.id === initial.id) ?? null;
      case 'stage':
        return stages.find((s) => s.id === initial.id) ?? null;
    }
  }, [initial, sites, fabs, areas, bays, stages]);

  const [form, setForm] = useState<NodeForm>(() => {
    const base: NodeForm = {
      code: initial?.code ?? '',
      parentCode: initial?.parentCode ?? parentHint?.code ?? '',
      name: initial?.name ?? '',
      ...extrasOf(level, raw),
      enabled: initial?.enabled ?? true,
    };
    if (initialGroupId) base.groupId = initialGroupId;
    return base;
  });

  const siteOptions = useMemo<FormOption[]>(
    () => sites.map((s) => ({ value: s.siteCode, label: s.siteName, hint: s.siteCode })),
    [sites],
  );
  const fabOptions = useMemo<FormOption[]>(
    () => fabs.map((f) => ({ value: f.fabCode, label: f.fabName, hint: f.fabCode })),
    [fabs],
  );
  const areaOptions = useMemo<FormOption[]>(
    () =>
      areas
        .filter((a) => !fabContext || a.fabCode === fabContext)
        .map((a) => ({ value: a.areaCode, label: a.areaName, hint: `${a.fabCode}/${a.areaCode}` })),
    [areas, fabContext],
  );
  const bayOptions = useMemo<FormOption[]>(
    () =>
      bays
        .filter((b) => !fabContext || b.fabCode === fabContext)
        .map((b) => ({ value: b.bayCode, label: b.bayName, hint: `${b.fabCode}/${b.bayCode}` })),
    [bays, fabContext],
  );
  const groupOptions = useMemo<FormOption[]>(
    () =>
      groups
        .filter((g) => !form.parentCode || g.fabCode === form.parentCode)
        .map((g) => ({ value: g.id, label: g.groupName, hint: g.groupCode })),
    [groups, form.parentCode],
  );

  const fields = useMemo<Array<FormField<NodeForm>>>(() => {
    const base: Array<FormField<NodeForm>> = [];
    if (!initial) {
      base.push({ kind: 'text', key: 'code', labelKey: meta.codeKey, required: true, mono: true });
    }
    if (level !== 'site') {
      const pm = PARENT_META[level];
      base.push({
        kind: 'select',
        key: 'parentCode',
        labelKey: pm.labelKey,
        required: true,
        options:
          level === 'fab'
            ? siteOptions
            : level === 'area'
              ? fabOptions
              : level === 'bay'
                ? areaOptions
                : bayOptions,
        showSearch: true,
        placeholderKey: pm.placeholderKey,
        disabled: !!parentHint, // 从树上发起：父级已锁定
      });
    }
    base.push({ kind: 'text', key: 'name', labelKey: meta.nameKey, required: true });
    if (level === 'site') {
      base.push({ kind: 'text', key: 'extra', labelKey: 'mds.site.address', span: 2 });
      base.push({ kind: 'text', key: 'extra2', labelKey: 'mds.site.contactPerson' });
      base.push({ kind: 'text', key: 'extra3', labelKey: 'mds.site.contactPhone' });
    }
    if (level === 'fab') {
      base.push({ kind: 'text', key: 'extra', labelKey: 'mds.fab.type', placeholder: '6寸' });
      base.push({
        kind: 'select',
        key: 'extra2',
        labelKey: 'mds.fab.amhsType',
        options: [
          { value: 'OHT', label: 'OHT' },
          { value: 'RGV', label: 'RGV' },
          { value: 'NONE', label: 'NONE' },
        ],
        allowClear: true,
      });
      base.push({ kind: 'text', key: 'extra3', labelKey: 'mds.fab.cleanGrade', placeholder: 'Class 100' });
    }
    if (level === 'area') {
      base.push({ kind: 'text', key: 'extra', labelKey: 'mds.area.type', placeholder: 'PHOTO' });
      base.push({
        kind: 'select',
        key: 'groupId',
        labelKey: 'mds.area.group',
        options: groupOptions,
        allowClear: true,
        placeholderKey: 'mds.area.groupPlaceholder',
        disabled: !!initialGroupId,
      });
      // V36 Hold Setting：该区 WIP 被 Hold 时 MES 带出的默认处置部门（空 = 人工指定）
      base.push({
        kind: 'select',
        key: 'holdDept',
        labelKey: 'mds.area.holdDept',
        options: deptOptions,
        allowClear: true,
      });
    }
    base.push({ kind: 'text', key: 'remark', labelKey: 'mds.common.remark', span: 2 });
    base.push({ kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' });
    return base;
  }, [level, initial, parentHint, meta, initialGroupId, siteOptions, fabOptions, areaOptions, bayOptions, groupOptions, deptOptions]);

  async function submit() {
    const next: Partial<Record<keyof NodeForm, string>> = {};
    if (!initial && !form.code.trim()) next.code = t('mds.common.required');
    if (level !== 'site' && !form.parentCode.trim()) next.parentCode = t('mds.common.required');
    if (!form.name.trim()) next.name = t('mds.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    const enabled = form.enabled;
    try {
      switch (level) {
        case 'site': {
          const payload = {
            siteCode: form.code.trim(),
            siteName: form.name.trim(),
            address: form.extra || null,
            contactPerson: form.extra2 || null,
            contactPhone: form.extra3 || null,
            remark: form.remark || null,
            enabled,
          };
          if (initial) await api.updateSite(initial.id, payload);
          else await api.createSite(payload);
          break;
        }
        case 'fab': {
          const payload = {
            fabCode: form.code.trim(),
            siteCode: form.parentCode.trim(),
            fabName: form.name.trim(),
            fabType: form.extra || null,
            amhsType: form.extra2 || null,
            cleanGrade: form.extra3 || null,
            remark: form.remark || null,
            enabled,
          };
          if (initial) await api.updateFab(initial.id, payload);
          else await api.createFab(payload);
          break;
        }
        case 'area': {
          const payload = {
            areaCode: form.code.trim(),
            fabCode: form.parentCode.trim(),
            areaName: form.name.trim(),
            areaType: form.extra || null,
            areaGroupId: form.groupId || null,
            holdDept: form.holdDept || null,
            remark: form.remark || null,
            enabled,
          };
          if (initial) await api.updateArea(initial.id, payload);
          else await api.createArea(payload);
          break;
        }
        case 'bay': {
          const payload = {
            bayCode: form.code.trim(),
            // fabCode 后端不可改：编辑取既有归属，树上新建取父 Area 的 fab
            fabCode: initial?.fabCode ?? parentHint?.fabCode ?? '',
            areaCode: form.parentCode.trim(),
            bayName: form.name.trim(),
            remark: form.remark || null,
            enabled,
          };
          if (initial) await api.updateBay(initial.id, payload);
          else await api.createBay(payload);
          break;
        }
        case 'stage': {
          const payload = {
            stageCode: form.code.trim(),
            fabCode: initial?.fabCode ?? parentHint?.fabCode ?? '',
            bayCode: form.parentCode.trim(),
            stageName: form.name.trim(),
            remark: form.remark || null,
            enabled,
          };
          if (initial) await api.updateStage(initial.id, payload);
          else await api.createStage(payload);
          break;
        }
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? 'mds.common.edit' : meta.createNewKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid<NodeForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
