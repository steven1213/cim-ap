import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  FormGrid,
  IconApps,
  IconChevron,
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
import type { Area, Bay, Fab, Site, Stage } from '@/types';

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

type Level = api.LocationType;

/** 每层的直接子层（stage 是位置树叶子）。 */
const CHILD_LEVEL: Record<Level, Level | null> = {
  site: 'fab',
  fab: 'area',
  area: 'bay',
  bay: 'stage',
  stage: null,
};

/** 每层的权限码组（<Can> 显隐用）。 */
const PERM: Record<Level, (typeof MDS_PERMS)[keyof typeof MDS_PERMS]> = {
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

  const sites = sitesRes.data ?? NO_SITES;
  const fabs = fabsRes.data ?? NO_FABS;
  const areas = areasRes.data ?? NO_AREAS;
  const bays = baysRes.data ?? NO_BAYS;
  const stages = stagesRes.data ?? NO_STAGES;

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

  const filteredTree = useMemo(() => filterTree(tree, debouncedKeyword), [tree, debouncedKeyword]);
  const selected = useMemo(() => findNode(tree, selectedKey), [tree, selectedKey]);

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
            <Can perm={MDS_PERMS.site.create}>
              <CimButton type="primary" size="small" onClick={() => setCreating('site')}>
                {t('mds.site.createNew')}
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
                />
              ))
            )}
          </div>
        </aside>

        <section className="topo-detail">
          {selected ? (
            <NodeDetail
              node={selected}
              onEdit={() => void openEdit(selected)}
              onDelete={() => void doDelete(selected)}
              onCreateChild={() => setCreating(CHILD_LEVEL[selected.level])}
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
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            refresh();
          }}
        />
      )}
    </Page>
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

function findNode(nodes: TopoNode[], key: string | null): TopoNode | null {
  if (!key) return null;
  for (const n of nodes) {
    if (`${n.level}:${n.id}` === key) return n;
    const hit = findNode(n.children, key);
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
}: {
  node: TopoNode;
  depth: number;
  collapsed: Set<string>;
  onToggle: (key: string) => void;
  onSelect: (key: string) => void;
  selectedKey: string | null;
}) {
  const { t } = useTranslation();
  const key = `${node.level}:${node.id}`;
  const isCollapsed = collapsed.has(key);
  const hasChildren = node.children.length > 0;
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
          {node.children.map((c) => (
            <TopoBranch
              key={`${c.level}:${c.id}`}
              node={c}
              depth={depth + 1}
              collapsed={collapsed}
              onToggle={onToggle}
              onSelect={onSelect}
              selectedKey={selectedKey}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function NodeDetail({
  node,
  onEdit,
  onDelete,
  onCreateChild,
}: {
  node: TopoNode;
  onEdit: () => void;
  onDelete: () => void;
  onCreateChild: () => void;
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
        <Can perm={PERM[node.level].delete}>
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
  remark: string;
  enabled: boolean;
}

/** 编辑态回填用原始行（树节点只带编码/名称/状态，属性值得从列表行取）。 */
function extrasOf(
  level: Level,
  raw: Site | Fab | Area | Bay | Stage | null,
): { extra: string; extra2: string; extra3: string; remark: string } {
  if (!raw) return { extra: '', extra2: '', extra3: '', remark: '' };
  switch (level) {
    case 'site': {
      const r = raw as Site;
      return { extra: r.address ?? '', extra2: r.contactPerson ?? '', extra3: r.contactPhone ?? '', remark: r.remark ?? '' };
    }
    case 'fab': {
      const r = raw as Fab;
      return { extra: r.fabType ?? '', extra2: r.amhsType ?? '', extra3: r.cleanGrade ?? '', remark: r.remark ?? '' };
    }
    case 'area': {
      const r = raw as Area;
      return { extra: r.areaType ?? '', extra2: '', extra3: '', remark: r.remark ?? '' };
    }
    default: {
      const r = raw as Bay | Stage;
      return { extra: '', extra2: '', extra3: '', remark: r.remark ?? '' };
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
  sites,
  fabs,
  areas,
  bays,
  stages,
  onClose,
  onDone,
}: {
  level: Level;
  initial: TopoNode | null;
  parentHint: TopoNode | null;
  sites: Site[];
  fabs: Fab[];
  areas: Area[];
  bays: Bay[];
  stages: Stage[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof NodeForm, string>>>({});

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

  const [form, setForm] = useState<NodeForm>(() => ({
    code: initial?.code ?? '',
    parentCode: initial?.parentCode ?? parentHint?.code ?? '',
    name: initial?.name ?? '',
    ...extrasOf(level, raw),
    enabled: initial?.enabled ?? true,
  }));

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
    }
    base.push({ kind: 'text', key: 'remark', labelKey: 'mds.common.remark', span: 2 });
    base.push({ kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' });
    return base;
  }, [level, initial, parentHint, meta, siteOptions, fabOptions, areaOptions, bayOptions]);

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
