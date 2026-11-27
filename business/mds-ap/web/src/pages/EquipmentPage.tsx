import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  StatusTag,
  useConfirm,
  useDict,
  useToast,
  type DataTableColumn,
  type FormField,
  type FormOption,
} from '@cim/ui';
import { useHasPermission } from '@/lib/usePermission';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import LocationTree, {
  buildLocTree,
  collectSubtreeIds,
  filterLocTree,
} from '@/components/LocationTree';
import type { Capability, Equipment, EquipmentCapabilityItem, EquipmentClass, EquipmentUnit } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 机台台账（EquipmentPage，v2 三页合一）——加工设备与搬运设备共用一张表，
 * 按机型大类（kind）筛选（行业 Process/Non-Process 两分法收敛为一个筛选器）。
 *
 * <p>动线：左位置树选节点 → 子树过滤；机型下拉 + Owner 精确过滤（责任人动线）；
 * 编辑弹窗分四块：基本 / 资产与 Owner / 运行控制（SEMI E30 控制模式 + 生产模式）/
 * 工艺约束（片径/载具/batch/产能，新建时从机型模板默认值派生回填）。
 * 「机型模板 → 派生实例」动线：模板页「派生机台」跳转带 ?derive=&lt;classCode&gt;，
 * 本页自动打开新建弹窗并预选机型。</p>
 */

const NO_EQP: Equipment[] = [];
const NO_CLASSES: EquipmentClass[] = [];
const NO_CAPS: Capability[] = [];
const NO_UNITS: EquipmentUnit[] = [];

/** 位置层显示文案键（`key:` 形态，check-i18n 认；模板字面量 `mds.loc.${lt}` 会判死）。 */
const LOC_LABEL = {
  FAB: { labelKey: 'mds.loc.FAB' },
  AREA: { labelKey: 'mds.loc.AREA' },
  BAY: { labelKey: 'mds.loc.BAY' },
  STAGE: { labelKey: 'mds.loc.STAGE' },
} as const;

/** 子单元形态选项（E120/E164 判据：受 recipe 控制处理晶圆=MODULE，否则 SUBSYSTEM）。 */
const UNIT_TYPE_OPTIONS = [
  'LOAD_PORT',
  'PROCESS_CHAMBER',
  'ALIGNER',
  'ROBOT',
  'LOAD_LOCK',
  'BUFFER',
  'OTHER',
] as const;

/** 形态 → E120 节点类（建表口径，前端联动默认值）。 */
const UNIT_CLASS_BY_TYPE: Record<string, 'MODULE' | 'SUBSYSTEM'> = {
  LOAD_PORT: 'SUBSYSTEM',
  PROCESS_CHAMBER: 'MODULE',
  ALIGNER: 'SUBSYSTEM',
  ROBOT: 'SUBSYSTEM',
  LOAD_LOCK: 'SUBSYSTEM',
  BUFFER: 'SUBSYSTEM',
  OTHER: 'SUBSYSTEM',
};

/** 子单元形态显示文案键（labelKey 形态，check-i18n 认）。 */
const UNIT_TYPE_LABEL = {
  LOAD_PORT: { labelKey: 'mds.unit.type.LOAD_PORT' },
  PROCESS_CHAMBER: { labelKey: 'mds.unit.type.PROCESS_CHAMBER' },
  ALIGNER: { labelKey: 'mds.unit.type.ALIGNER' },
  ROBOT: { labelKey: 'mds.unit.type.ROBOT' },
  LOAD_LOCK: { labelKey: 'mds.unit.type.LOAD_LOCK' },
  BUFFER: { labelKey: 'mds.unit.type.BUFFER' },
  OTHER: { labelKey: 'mds.unit.type.OTHER' },
} as const;

/** 设备大类筛选顺序（8 类行业定稿；全部=不过滤）。 */
const KIND_FILTERS = ['PROCESS', 'METRO', 'TEST', 'ASSY', 'MATERIAL', 'FACILITY', 'TOOL', 'OTHER'] as const;

export default function EquipmentPage({ initialKind }: { initialKind?: string } = {}) {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const stateDict = useDict('mds.equip_state');
  const kindDict = useDict('mds.equip_kind');
  // 部门选项（IAM 组织架构 DEPT；V36 起部门真源=组织架构，静态字典废弃）
  const deptRes = useResource(({ signal }) => api.listDeptOptions(signal));

  const [keyword, setKeyword] = useState('');
  const [classFilter, setClassFilter] = useState('');
  const [kindFilter, setKindFilter] = useState(initialKind ?? '');
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [createPrefillClass, setCreatePrefillClass] = useState('');
  const [editing, setEditing] = useState<Equipment | null>(null);
  const [capping, setCapping] = useState<Equipment | null>(null);
  const [uniting, setUniting] = useState<Equipment | null>(null);

  // 「机型模板 → 派生机台」动线：?derive=<classCode> 自动打开新建弹窗并预选机型
  useEffect(() => {
    const derive = searchParams.get('derive');
    if (derive) {
      setCreatePrefillClass(derive);
      setCreating(true);
      setSearchParams({}, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 位置树按各层 view 权限拉取（无权限的层不请求，树中该层为空——与拓扑页同口径）
  const canSite = useHasPermission(MDS_PERMS.site.view);
  const canFab = useHasPermission(MDS_PERMS.fab.view);
  const canArea = useHasPermission(MDS_PERMS.area.view);
  const canBay = useHasPermission(MDS_PERMS.bay.view);
  const canStage = useHasPermission(MDS_PERMS.stage.view);

  const sitesRes = useResource(({ signal }) => api.listSites(undefined, signal), { enabled: canSite });
  const fabsRes = useResource(({ signal }) => api.listFabs(undefined, undefined, signal), {
    enabled: canFab,
  });
  const areasRes = useResource(({ signal }) => api.listAreas(undefined, undefined, signal), {
    enabled: canArea,
  });
  const baysRes = useResource(({ signal }) => api.listBays(undefined, signal), { enabled: canBay });
  const stagesRes = useResource(({ signal }) => api.listStages(undefined, signal), {
    enabled: canStage,
  });

  const eqpRes = useResource(({ signal }) => api.listEquipments({}, signal));
  const classRes = useResource(({ signal }) => api.listEquipmentClasses(undefined, signal));

  const locTree = useMemo(
    () =>
      buildLocTree(
        sitesRes.data ?? [],
        fabsRes.data ?? [],
        areasRes.data ?? [],
        baysRes.data ?? [],
        stagesRes.data ?? [],
      ),
    [sitesRes.data, fabsRes.data, areasRes.data, baysRes.data, stagesRes.data],
  );
  /** 选中节点子树内全部位置 id（列表过滤依据）。 */
  const subtreeIds = useMemo(() => collectSubtreeIds(locTree, selectedKey), [locTree, selectedKey]);

  /** locationId → 「位置类型 · 编码 名称」显示（无权限层拿不到记录时回退显示 id）。 */
  const locLabel = useMemo(() => {
    const map = new Map<string, string>();
    for (const f of fabsRes.data ?? [])
      map.set(f.id, `${t(LOC_LABEL.FAB.labelKey)} · ${f.fabCode} ${f.fabName}`);
    for (const a of areasRes.data ?? [])
      map.set(a.id, `${t(LOC_LABEL.AREA.labelKey)} · ${a.areaCode} ${a.areaName}`);
    for (const b of baysRes.data ?? [])
      map.set(b.id, `${t(LOC_LABEL.BAY.labelKey)} · ${b.bayCode} ${b.bayName}`);
    for (const s of stagesRes.data ?? [])
      map.set(s.id, `${t(LOC_LABEL.STAGE.labelKey)} · ${s.stageCode} ${s.stageName}`);
    return map;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fabsRes.data, areasRes.data, baysRes.data, stagesRes.data, t]);

  /** kindFilter 命中的机型集合（kind 由机型继承，实例不存 kind）。 */
  const classCodeByKind = useMemo(() => {
    const set = new Set<string>();
    for (const cls of classRes.data ?? NO_CLASSES) {
      if (!kindFilter || cls.kindCode === kindFilter) set.add(cls.classCode);
    }
    return set;
  }, [classRes.data, kindFilter]);

  const rows = useMemo(() => {
    let list = eqpRes.data ?? NO_EQP;
    if (classFilter) list = list.filter((e) => e.classCode === classFilter);
    if (kindFilter) list = list.filter((e) => classCodeByKind.has(e.classCode));
    if (selectedKey) list = list.filter((e) => subtreeIds.has(e.locationId));
    const kw = keyword.trim().toLowerCase();
    if (kw) {
      list = list.filter(
        (e) =>
          e.equipmentCode.toLowerCase().includes(kw) ||
          e.equipmentName.toLowerCase().includes(kw) ||
          e.classCode.toLowerCase().includes(kw) ||
          (e.className ?? '').toLowerCase().includes(kw) ||
          (e.ownerUser ?? '').toLowerCase().includes(kw),
      );
    }
    return list;
  }, [eqpRes.data, classFilter, kindFilter, classCodeByKind, selectedKey, subtreeIds, keyword]);

  const openEdit = async (row: Equipment) => {
    try {
      await api.claimLock('equipment', row.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setEditing(row);
  };

  const closeEdit = () => {
    if (editing) {
      void api.releaseLock('equipment', editing.id).catch(() => {});
    }
    setEditing(null);
  };

  const doDelete = async (row: Equipment) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteEquipment(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    eqpRes.refresh();
  };

  const classOptions = useMemo(
    () =>
      (classRes.data ?? NO_CLASSES)
        .filter((c) => !kindFilter || c.kindCode === kindFilter)
        .map((c) => ({ value: c.classCode, label: `${c.classCode} ${c.className}` })),
    [classRes.data, kindFilter],
  );

  const filterOptions = useMemo(
    () => [{ value: '', label: t('mds.equipment.allClasses') }, ...classOptions],
    [classOptions, t],
  );

  const kindFilterOptions = useMemo(
    () => [
      { value: '', label: t('mds.equipment.allKinds') },
      ...KIND_FILTERS.map((k) => ({ value: k, label: kindDict.labelOf(k) })),
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [kindDict.items],
  );

  const columns = useMemo<Array<DataTableColumn<Equipment>>>(
    () => [
      { key: 'equipmentCode', titleKey: 'mds.equipment.code', width: 105, mono: true },
      { key: 'equipmentName', titleKey: 'mds.equipment.name', width: 100 },
      {
        key: 'classCode',
        titleKey: 'mds.equipment.class',
        width: 105,
        ellipsis: true,
        render: (r) => `${r.classCode}${r.className ? ` ${r.className}` : ''}`,
      },
      {
        key: 'kindCode',
        titleKey: 'mds.eqpType.kind',
        width: 80,
        ellipsis: true,
        render: (r) => <span className="mono">{r.kindCode ? kindDict.labelOf(r.kindCode) : '—'}</span>,
      },
      {
        key: 'location',
        titleKey: 'mds.equipment.location',
        width: 105,
        ellipsis: true,
        render: (r) => locLabel.get(r.locationId) ?? `${r.locationType}/${r.locationId}`,
      },
      {
        key: 'ownerUser',
        titleKey: 'mds.equipment.owner',
        width: 80,
        ellipsis: true,
        render: (r) => r.ownerUser ?? '—',
      },
      {
        key: 'owningDept',
        titleKey: 'mds.equipment.owningDept',
        width: 80,
        ellipsis: true,
        render: (r) => (deptRes.data ?? []).find((d) => d.code === r.owningDept)?.name ?? r.owningDept ?? '—',
      },
      {
        key: 'stateCode',
        titleKey: 'mds.equipment.state',
        width: 110,
        render: (r) => (
          <StatusTag
            dict="equipState"
            value={r.stateCode}
            label={stateDict.labelOf(r.stateCode)}
            size="small"
          />
        ),
      },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 55,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 245,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.equipment.update}>
              <CimButton size="small" onClick={() => setUniting(r)}>
                {t('mds.equipment.units')}
              </CimButton>
              <CimButton size="small" onClick={() => setCapping(r)}>
                {t('mds.equipment.capabilities')}
              </CimButton>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.equipment.delete}>
              <CimButton size="small" danger onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, locLabel, kindDict, stateDict, deptRes.data],
  );

  return (
    <Page>
      <GuideBar />
      <div className="topo topo-equipment">
        <aside className="topo-tree">
          <div className="topo-toolbar">
            <select
              className="topo-group-filter"
              value={kindFilter}
              onChange={(e) => {
                setKindFilter(e.target.value);
                setClassFilter('');
              }}
            >
              {kindFilterOptions.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <select
              className="topo-group-filter"
              value={classFilter}
              onChange={(e) => setClassFilter(e.target.value)}
            >
              {filterOptions.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div className="topo-nodes">
            <LocationTree
              nodes={filterLocTree(locTree, '')}
              selectedKey={selectedKey}
              onSelect={setSelectedKey}
              emptyKey="mds.equipment.treeEmpty"
            />
          </div>
        </aside>

        <section className="topo-detail">
          <DataTable<Equipment>
            titleKey="mds.equipment.listTitle"
            columns={columns}
            rows={rows}
            rowKey={(r) => r.id}
            scrollX={1080}
            loading={eqpRes.loading}
            error={eqpRes.error?.message ?? null}
            onRetry={eqpRes.refresh}
            headActions={
              <Can perm={MDS_PERMS.equipment.create}>
                <CimButton type="primary" size="small" onClick={() => { setCreatePrefillClass(''); setCreating(true); }}>
                  {t('mds.common.create')}
                </CimButton>
              </Can>
            }
            search={{
              value: keyword,
              onChange: setKeyword,
              placeholderKey: 'mds.equipment.searchPlaceholder',
            }}
            onRefresh={eqpRes.refresh}
          />
        </section>
      </div>

      {creating && (
        <EquipmentFormModal
          classes={classRes.data ?? NO_CLASSES}
          prefillClass={createPrefillClass}
          initial={null}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            eqpRes.refresh();
          }}
        />
      )}
      {editing && (
        <EquipmentFormModal
          classes={classRes.data ?? NO_CLASSES}
          prefillClass=""
          initial={editing}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            eqpRes.refresh();
          }}
        />
      )}
      {capping && (
        <CapabilityLinkModal
          equipment={capping}
          onClose={() => setCapping(null)}
          onDone={() => {
            setCapping(null);
            toast.success(t('mds.common.updated'));
          }}
        />
      )}
      {uniting && (
        <EquipmentUnitModal
          equipment={uniting}
          onClose={() => setUniting(null)}
        />
      )}
    </Page>
  );
}

const EQP_TITLE = {
  editKey: 'mds.equipment.editTitle',
  createKey: 'mds.equipment.createTitle',
} as const;

/** 机台表单位置层选项：kind=MATERIAL 只能 FAB/AREA/BAY，其余只能 STAGE——与后端校验同口径。 */
function locationTypeOptionsFor(kindCode: string | null | undefined) {
  return kindCode === 'MATERIAL' ? (['FAB', 'AREA', 'BAY'] as const) : (['STAGE'] as const);
}

/**
 * 机台新建/编辑弹窗（v2）——四个字段块：
 * 基本（编码/名称/机型/位置/状态）· 资产与 Owner · 运行控制（E30 控制模式 + 生产模式）·
 * 工艺约束（片径/载具/batch/产能，新建时空值由机型模板默认值回填）。
 * 机型下拉按 kind 筛选联动（大类变化清空机型选择）。
 */
function EquipmentFormModal({
  classes,
  prefillClass,
  initial,
  onClose,
  onDone,
}: {
  classes: EquipmentClass[];
  prefillClass: string;
  initial: Equipment | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const kindDict = useDict('mds.equip_kind');
  const classDict = useDict('mds.equip_class');
  const stateDict = useDict('mds.equip_state');
  const controlDict = useDict('mds.equip_control_mode');
  const prodDict = useDict('mds.equip_production_mode');
  // 部门选项（IAM 组织架构 DEPT；V36 起部门真源=组织架构）
  const deptRes = useResource(({ signal }) => api.listDeptOptions(signal));
  const owningDeptOptions = useMemo<FormOption[]>(
    () => (deptRes.data ?? []).map((d) => ({ value: d.code, label: d.name, hint: d.code })),
    [deptRes.data],
  );
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.equipmentCode ?? '',
    name: initial?.equipmentName ?? '',
    classCode: initial?.classCode ?? prefillClass,
    locationType: (initial?.locationType ?? 'STAGE') as string,
    locationId: initial?.locationId ?? '',
    owner: initial?.ownerUser ?? '',
    owningDept: initial?.owningDept ?? '',
    controlMode: initial?.controlMode ?? '',
    productionMode: initial?.productionMode ?? 'PRODUCTION',
    vendor: initial?.vendor ?? '',
    modelNo: initial?.modelNo ?? '',
    serialNo: initial?.serialNo ?? '',
    assetNo: initial?.assetNo ?? '',
    installDate: initial?.installDate ?? '',
    waferSizes: initial?.waferSizes ?? '',
    carrierTypes: initial?.carrierTypes ?? '',
    batchMin: initial?.batchMin != null ? String(initial.batchMin) : '',
    batchMax: initial?.batchMax != null ? String(initial.batchMax) : '',
    standardWph: initial?.standardWph != null ? String(initial.standardWph) : '',
    state: initial?.stateCode ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  // 状态下拉：编辑时只列「从当前状态出发的合法转换」（E30 口径，后端同款守门）；
  // 新建无旧态不校验转换，给全词表。当前值永远保留在选项里（from==to 不算转换）。
  const allowedRes = useResource(
    ({ signal }) => api.listAllowedStateTargets('EQUIPMENT', initial?.stateCode ?? '', signal),
    { deps: [initial?.stateCode ?? ''], enabled: !!initial?.stateCode },
  );
  const stateOptions = useMemo(() => {
    const all = stateDict.items
      .filter((i) => !i.disabled)
      .map((i) => ({ value: i.value, label: i.label }));
    if (!initial?.stateCode) return all;
    const allowed = allowedRes.data;
    if (!allowed) return all;
    const keep = new Set<string>([...allowed, initial.stateCode]);
    return all.filter((o) => keep.has(o.value));
  }, [stateDict.items, initial?.stateCode, allowedRes.data]);

  // 机型选项 + 选中的机型对象（驱动 kind/位置层联动）
  const selectedClass = useMemo(
    () => classes.find((c) => c.classCode === form.classCode) ?? null,
    [classes, form.classCode],
  );
  const classOptions = useMemo(
    () => classes.map((c) => ({ value: c.classCode, label: `${c.classCode} ${c.className}` })),
    [classes],
  );

  // 位置层选项按机型大类联动（MATERIAL→FAB/AREA/BAY，其余→STAGE；与后端校验同口径）
  const locTypeOptions = useMemo(() => {
    const base = locationTypeOptionsFor(selectedClass?.kindCode).map((lt) => ({
      value: lt,
      label: t(LOC_LABEL[lt].labelKey),
    }));
    if (initial && !locationTypeOptionsFor(selectedClass?.kindCode).includes(initial.locationType as never)) {
      base.unshift({
        value: initial.locationType,
        label: t(LOC_LABEL[initial.locationType as keyof typeof LOC_LABEL].labelKey),
      });
    }
    return base;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedClass, initial]);

  // 各层记录选项（切层联动）；无对应 view 权限时列表为空，仅能展示原值
  const canFab = useHasPermission(MDS_PERMS.fab.view);
  const canArea = useHasPermission(MDS_PERMS.area.view);
  const canBay = useHasPermission(MDS_PERMS.bay.view);
  const canStage = useHasPermission(MDS_PERMS.stage.view);
  const fabsRes = useResource(({ signal }) => api.listFabs(undefined, undefined, signal), {
    enabled: canFab,
  });
  const areasRes = useResource(({ signal }) => api.listAreas(undefined, undefined, signal), {
    enabled: canArea,
  });
  const baysRes = useResource(({ signal }) => api.listBays(undefined, signal), { enabled: canBay });
  const stagesRes = useResource(({ signal }) => api.listStages(undefined, signal), {
    enabled: canStage,
  });

  const locRecordOptions = useMemo(() => {
    switch (form.locationType) {
      case 'FAB':
        return (fabsRes.data ?? []).map((f) => ({ value: f.id, label: `${f.fabCode} ${f.fabName}` }));
      case 'AREA':
        return (areasRes.data ?? []).map((a) => ({
          value: a.id,
          label: `${a.areaCode} ${a.areaName}`,
        }));
      case 'BAY':
        return (baysRes.data ?? []).map((b) => ({
          value: b.id,
          label: `${b.bayCode} ${b.bayName}`,
        }));
      case 'STAGE':
        return (stagesRes.data ?? []).map((s) => ({
          value: s.id,
          label: `${s.stageCode} ${s.stageName}`,
        }));
      default:
        return [];
    }
  }, [form.locationType, fabsRes.data, areasRes.data, baysRes.data, stagesRes.data]);

  const num = (v: string) => (v === '' ? null : Number(v));

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      // —— 基本 ——
      ...(!initial
        ? [
            {
              kind: 'text' as const,
              key: 'code' as const,
              labelKey: 'mds.equipment.code',
              required: true,
              mono: true,
            },
          ]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.equipment.name', required: true },
      {
        kind: 'select' as const,
        key: 'classCode' as const,
        labelKey: 'mds.equipment.class',
        required: true,
        options: classOptions,
      },
      {
        kind: 'select' as const,
        key: 'locationType' as const,
        labelKey: 'mds.equipment.locationType',
        required: true,
        options: locTypeOptions,
      },
      {
        kind: 'select' as const,
        key: 'locationId' as const,
        labelKey: 'mds.equipment.locationRecord',
        required: true,
        options: locRecordOptions,
      },
      {
        kind: 'select' as const,
        key: 'state' as const,
        labelKey: 'mds.equipment.state',
        options: stateOptions,
        allowClear: true,
      },
      // —— 运行控制（SEMI E30 控制模式 + 生产模式；正交于状态快照）——
      {
        kind: 'select' as const,
        key: 'controlMode' as const,
        labelKey: 'mds.equipment.controlMode',
        allowClear: true,
        options: controlDict.items
          .filter((i) => !i.disabled)
          .map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select' as const,
        key: 'productionMode' as const,
        labelKey: 'mds.equipment.productionMode',
        options: prodDict.items
          .filter((i) => !i.disabled)
          .map((i) => ({ value: i.value, label: i.label })),
      },
      // —— 资产与 Owner（ISA-95 PhysicalAsset）——
      { kind: 'text' as const, key: 'owner' as const, labelKey: 'mds.equipment.owner' },
      { kind: 'select' as const, key: 'owningDept' as const, labelKey: 'mds.equipment.owningDept', options: owningDeptOptions, allowClear: true },
      { kind: 'text' as const, key: 'vendor' as const, labelKey: 'mds.equipment.vendor' },
      { kind: 'text' as const, key: 'modelNo' as const, labelKey: 'mds.equipment.modelNo' },
      { kind: 'text' as const, key: 'serialNo' as const, labelKey: 'mds.equipment.serialNo' },
      { kind: 'text' as const, key: 'assetNo' as const, labelKey: 'mds.equipment.assetNo' },
      {
        kind: 'date' as const,
        key: 'installDate' as const,
        labelKey: 'mds.equipment.installDate',
        valueFormat: 'yyyy-MM-dd',
      },
      // —— 工艺约束（新建时空值由机型模板回填；6/8/12 寸用多值表达，不按寸硬编码）——
      { kind: 'text' as const, key: 'waferSizes' as const, labelKey: 'mds.equipment.waferSizes' },
      { kind: 'text' as const, key: 'carrierTypes' as const, labelKey: 'mds.equipment.carrierTypes' },
      { kind: 'number' as const, key: 'batchMin' as const, labelKey: 'mds.equipment.batchMin' },
      { kind: 'number' as const, key: 'batchMax' as const, labelKey: 'mds.equipment.batchMax' },
      { kind: 'number' as const, key: 'standardWph' as const, labelKey: 'mds.equipment.standardWph' },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
      { kind: 'textarea' as const, key: 'remark' as const, labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, classOptions, locTypeOptions, locRecordOptions, stateOptions, controlDict.items, prodDict.items],
  );

  async function submit() {
    if (!form.name.trim() || !form.classCode || !form.locationType || !form.locationId) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const payload = {
      equipmentName: form.name.trim(),
      classCode: form.classCode,
      locationType: form.locationType as Equipment['locationType'],
      locationId: form.locationId,
      ownerUser: form.owner.trim() || null,
      owningDept: form.owningDept || null,
      controlMode: form.controlMode || null,
      productionMode: form.productionMode || null,
      vendor: form.vendor.trim() || null,
      modelNo: form.modelNo.trim() || null,
      serialNo: form.serialNo.trim() || null,
      assetNo: form.assetNo.trim() || null,
      installDate: form.installDate || null,
      waferSizes: form.waferSizes.trim() || null,
      carrierTypes: form.carrierTypes.trim() || null,
      batchMin: num(form.batchMin),
      batchMax: num(form.batchMax),
      standardWph: num(form.standardWph),
      stateCode: form.state || null,
      remark: form.remark || null,
      enabled: form.enabled,
    };
    try {
      if (initial) {
        await api.updateEquipment(initial.id, payload);
      } else {
        await api.createEquipment({ equipmentCode: form.code.trim(), ...payload });
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
      titleKey={initial ? EQP_TITLE.editKey : EQP_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={640}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => {
          setForm((prev) => ({
            ...prev,
            ...patch,
            // 机型变更：位置层回退到新机型大类的合法层并清空已选记录
            ...(patch.classCode && patch.classCode !== prev.classCode
              ? {
                  locationType: locationTypeOptionsFor(
                    classes.find((c) => c.classCode === patch.classCode)?.kindCode,
                  )[0],
                  locationId: '',
                }
              : {}),
            locationId:
              patch.locationType && patch.locationType !== prev.locationType ? '' : prev.locationId,
          }));
        }}
        columns={2}
      />
      {selectedClass && (
        <p className="eqp-type-link">
          {t('mds.eqpType.kind')}
          ：<span className="mono">{kindDict.labelOf(selectedClass.kindCode)}</span>
          {selectedClass.equipClassCode ? (
            <>
              {' · '}
              {t('mds.eqpType.class')}
              ：<span className="mono">{classDict.labelOf(selectedClass.equipClassCode)}</span>
            </>
          ) : null}
        </p>
      )}
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/**
 * 设备↔能力关联弹窗（V35 条目化：{capabilityCode, waferSize} 全量替换提交）。
 *
 * <p>混线厂同机可跑多尺寸晶圆，资格可按尺寸区分：同一能力可登记多行
 * （如 PROC_6" + 6、PROC_8" + 8），waferSize 留空 = 全尺寸通用资格。
 * 后端按 (equipment, capabilityCode, waferSize) 判重并物理删旧插新。</p>
 */
function CapabilityLinkModal({
  equipment,
  onClose,
  onDone,
}: {
  equipment: Equipment;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const sizeDict = useDict('mds.wafer_size');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const capRes = useResource(({ signal }) => api.listCapabilities(undefined, signal));
  const linkRes = useResource(({ signal }) =>
    api.listEquipmentCapabilities(equipment.id, signal),
  );

  const capOptions = useMemo(
    () =>
      (capRes.data ?? NO_CAPS)
        .filter((c) => c.enabled)
        .map((c) => ({ value: c.capabilityCode, label: `${c.capabilityCode} ${c.capabilityName}` })),
    [capRes.data],
  );
  const sizeOptions = useMemo(
    () => sizeDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
    [sizeDict.items],
  );

  const [items, setItems] = useState<EquipmentCapabilityItem[]>([]);
  // 现有关联到达后初始化条目（只初始化一次）
  useEffect(() => {
    if (linkRes.data) setItems(linkRes.data);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkRes.data]);

  const patchItem = (idx: number, patch: Partial<EquipmentCapabilityItem>) => {
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  };

  async function submit() {
    setBusy(true);
    setErr('');
    try {
      await api.setEquipmentCapabilities(
        equipment.id,
        items.map((it) => ({ ...it, capabilityCode: it.capabilityCode.trim() })),
      );
      onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      title={t('mds.equipment.capabilityTitle', {
        name: `${equipment.equipmentCode} ${equipment.equipmentName}`,
      })}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={640}
    >
      {linkRes.loading ? (
        <p className="dim">{t('mds.common.loading')}</p>
      ) : (
        <div>
          <p className="dim" style={{ marginTop: 0 }}>{t('mds.equipment.capabilityHint')}</p>
          {items.map((it, idx) => (
            <div key={idx} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
              <select
                className="topo-group-filter"
                value={it.capabilityCode}
                onChange={(e) => patchItem(idx, { capabilityCode: e.target.value })}
              >
                <option value="" disabled>
                  {t('mds.equipment.capabilityPick')}
                </option>
                {capOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
              <select
                className="topo-group-filter"
                value={it.waferSize ?? ''}
                onChange={(e) => patchItem(idx, { waferSize: e.target.value || null })}
              >
                <option value="">{t('mds.equipment.capabilityAllSizes')}</option>
                {sizeOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
              <CimButton
                size="small"
                danger
                onClick={() => setItems((prev) => prev.filter((_, i) => i !== idx))}
              >
                {t('mds.common.delete')}
              </CimButton>
            </div>
          ))}
          <CimButton
            size="small"
            disabled={!capOptions.length}
            onClick={() => setItems((prev) => [...prev, { capabilityCode: '', waferSize: null }])}
          >
            {t('mds.equipment.capabilityAdd')}
          </CimButton>
        </div>
      )}
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 子单元形态下拉选项（E120 形态枚举；文案走 UNIT_TYPE_LABEL）。 */
const unitTypeOptions = (t: (k: string) => string) =>
  UNIT_TYPE_OPTIONS.map((ut) => ({ value: ut, label: t(UNIT_TYPE_LABEL[ut].labelKey) }));

/**
 * 机台子单元管理弹窗——设备结构树维护（SEMI E120 CEM；v2 端口属性增强）。
 *
 * <p>unitCode 设备内唯一、建后不可改；unitClass 随形态联动默认值（腔=MODULE，
 * 其余=SUBSYSTEM）；状态选项按形态过滤（端口走 E87 词表、腔走 E157 词表、
 * 其余走 E116 EPT 简化三态）。v2：LOAD_PORT 加载具类别/装载目的（E87 口径），
 * PROCESS_CHAMBER 加污染等级（前道交叉污染控制）；capacity 通用化（BUFFER 也可维护）。
 * 写操作守门父设备编辑锁（后端）。</p>
 */
function EquipmentUnitModal({
  equipment,
  onClose,
}: {
  equipment: Equipment;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const unitStateDict = useDict('mds.unit_state');
  const unitRes = useResource(({ signal }) => api.listEquipmentUnits(equipment.id, signal));
  const [formState, setFormState] = useState<
    { mode: 'new' } | { mode: 'edit'; unit: EquipmentUnit } | null
  >(null);

  async function doDelete(unit: EquipmentUnit) {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteEquipmentUnit(equipment.id, unit.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    unitRes.refresh();
  }

  const columns = useMemo<Array<DataTableColumn<EquipmentUnit>>>(
    () => [
      { key: 'unitCode', titleKey: 'mds.unit.code', width: 100, mono: true },
      { key: 'unitName', titleKey: 'mds.unit.name', width: 170 },
      {
        key: 'unitType',
        titleKey: 'mds.unit.type',
        width: 120,
        render: (r) => (
          <span className="mono">
            {t(UNIT_TYPE_LABEL[r.unitType as keyof typeof UNIT_TYPE_LABEL]?.labelKey ?? 'mds.unit.type.OTHER')}
          </span>
        ),
      },
      {
        key: 'unitClass',
        titleKey: 'mds.unit.class',
        width: 100,
        render: (r) => <span className="mono">{r.unitClass}</span>,
      },
      {
        key: 'stateCode',
        titleKey: 'mds.unit.state',
        width: 120,
        render: (r) => (
          <StatusTag
            dict="unitState"
            value={r.stateCode}
            label={unitStateDict.labelOf(r.stateCode)}
            size="small"
          />
        ),
      },
      {
        key: 'port',
        titleKey: 'mds.unit.port',
        width: 120,
        render: (r) => {
          if (r.unitType === 'LOAD_PORT') return `#${r.portNo ?? '—'} / ${r.capacity ?? '—'}`;
          if (r.unitType === 'BUFFER') return `${r.capacity ?? '—'}`;
          return '—';
        },
      },
      {
        key: 'attrs',
        titleKey: 'mds.unit.attrs',
        width: 130,
        ellipsis: true,
        render: (r) => {
          const parts: string[] = [];
          if (r.carrierCategory) parts.push(r.carrierCategory);
          if (r.loadPurpose) parts.push(r.loadPurpose);
          if (r.contaminationLevel) parts.push(`CL-${r.contaminationLevel}`);
          return parts.length ? <span className="mono">{parts.join(' / ')}</span> : '—';
        },
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 130,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.equipment.update}>
              <CimButton size="small" onClick={() => setFormState({ mode: 'edit', unit: r })}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.equipment.delete}>
              <CimButton size="small" danger onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, unitStateDict],
  );

  return (
    <CimModal
      open
      onClose={onClose}
      title={t('mds.unit.title', { name: `${equipment.equipmentCode} ${equipment.equipmentName}` })}
      footer={null}
      width={900}
    >
      <div className="unit-toolbar">
        <Can perm={MDS_PERMS.equipment.update}>
          <CimButton type="primary" size="small" onClick={() => setFormState({ mode: 'new' })}>
            {t('mds.common.create')}
          </CimButton>
        </Can>
        <CimButton size="small" onClick={unitRes.refresh}>
          {t('mds.common.refresh')}
        </CimButton>
      </div>
      <DataTable<EquipmentUnit>
        columns={columns}
        rows={unitRes.data ?? NO_UNITS}
        rowKey={(r) => r.id}
        loading={unitRes.loading}
        error={unitRes.error?.message ?? null}
        onRetry={unitRes.refresh}
      />
      {formState && (
        <UnitFormModal
          equipmentId={equipment.id}
          initial={formState.mode === 'edit' ? formState.unit : null}
          onClose={() => setFormState(null)}
          onDone={() => {
            setFormState(null);
            toast.success(t(formState.mode === 'edit' ? 'mds.common.updated' : 'mds.common.created'));
            unitRes.refresh();
          }}
        />
      )}
    </CimModal>
  );
}

const UNIT_TITLE = {
  editKey: 'mds.unit.editTitle',
  createKey: 'mds.unit.createTitle',
} as const;

/** 子单元新建/编辑弹窗（形态联动 E120 节点类与可选状态；v2 端口属性按形态显隐）。 */
function UnitFormModal({
  equipmentId,
  initial,
  onClose,
  onDone,
}: {
  equipmentId: string;
  initial: EquipmentUnit | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const unitStateDict = useDict('mds.unit_state');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.unitCode ?? '',
    name: initial?.unitName ?? '',
    unitType: (initial?.unitType ?? 'LOAD_PORT') as string,
    unitClass: (initial?.unitClass ?? 'SUBSYSTEM') as string,
    state: initial?.stateCode ?? '',
    portNo: initial?.portNo != null ? String(initial.portNo) : '',
    capacity: initial?.capacity != null ? String(initial.capacity) : '',
    carrierCategory: initial?.carrierCategory ?? '',
    loadPurpose: initial?.loadPurpose ?? '',
    contaminationLevel: initial?.contaminationLevel ?? '',
    enabled: initial?.enabled ?? true,
  });

  // 状态选项由后端状态模型驱动：形态 → resolve（精确匹配 UNIT_LOAD_PORT/UNIT_CHAMBER，
  // 其余形态落 UNIT_GENERIC 兜底），模型的 states 即该形态状态集（取代旧的前端硬编码映射）。
  const modelRes = useResource(
    ({ signal }) => api.resolveStateModel('EQUIPMENT_UNIT', form.unitType, signal),
    { deps: [form.unitType] },
  );
  // 编辑时再收窄：只列「从当前状态出发的合法转换」（E30 口径，后端同款守门）
  const currentState = initial?.stateCode ?? '';
  const allowedRes = useResource(
    ({ signal }) =>
      api.listAllowedStateTargets(modelRes.data?.modelCode ?? '', currentState, signal),
    {
      deps: [modelRes.data?.modelCode ?? '', currentState],
      enabled: !!currentState && !!modelRes.data,
    },
  );
  const stateOptions = useMemo(() => {
    const states = modelRes.data?.states;
    if (!states) return [];
    let codes = states;
    if (currentState && allowedRes.data) {
      const keep = new Set<string>([...allowedRes.data, currentState]);
      codes = states.filter((s) => keep.has(s));
    }
    return unitStateDict.items
      .filter((i) => codes.includes(i.value) && !i.disabled)
      .map((i) => ({ value: i.value, label: i.label }));
  }, [modelRes.data, allowedRes.data, currentState, unitStateDict.items]);

  const num = (v: string) => (v === '' ? null : Number(v));

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'code' as const, labelKey: 'mds.unit.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.unit.name', required: true },
      {
        kind: 'select' as const,
        key: 'unitType' as const,
        labelKey: 'mds.unit.type',
        required: true,
        options: unitTypeOptions(t),
      },
      {
        kind: 'select' as const,
        key: 'unitClass' as const,
        labelKey: 'mds.unit.class',
        required: true,
        options: [
          { value: 'MODULE', label: t('mds.unit.class.MODULE') },
          { value: 'SUBSYSTEM', label: t('mds.unit.class.SUBSYSTEM') },
        ],
      },
      {
        kind: 'select' as const,
        key: 'state' as const,
        labelKey: 'mds.unit.state',
        allowClear: true,
        options: stateOptions,
      },
      ...(form.unitType === 'LOAD_PORT'
        ? ([
            { kind: 'number' as const, key: 'portNo' as const, labelKey: 'mds.unit.portNo' },
            { kind: 'number' as const, key: 'capacity' as const, labelKey: 'mds.unit.capacity' },
            {
              kind: 'select' as const,
              key: 'carrierCategory' as const,
              labelKey: 'mds.unit.carrierCategory',
              allowClear: true,
              options: ['CASSETTE', 'FOUP', 'FOSB', 'MAGAZINE', 'OTHER'].map((v) => ({ value: v, label: v })),
            },
            {
              kind: 'select' as const,
              key: 'loadPurpose' as const,
              labelKey: 'mds.unit.loadPurpose',
              allowClear: true,
              options: ['BOTH', 'INPUT', 'OUTPUT'].map((v) => ({ value: v, label: v })),
            },
          ] as Array<FormField<typeof form>>)
        : []),
      ...(form.unitType === 'PROCESS_CHAMBER'
        ? ([
            {
              kind: 'text' as const,
              key: 'contaminationLevel' as const,
              labelKey: 'mds.unit.contaminationLevel',
            },
          ] as Array<FormField<typeof form>>)
        : []),
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, form.unitType, stateOptions, modelRes.data?.modelCode],
  );

  async function submit() {
    if (!form.name.trim() || (!initial && !form.code.trim())) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const isLoadPort = form.unitType === 'LOAD_PORT';
    try {
      const payload = {
        unitName: form.name.trim(),
        unitType: form.unitType,
        unitClass: form.unitClass as EquipmentUnit['unitClass'],
        stateCode: form.state || null,
        portNo: isLoadPort ? num(form.portNo) : null,
        capacity: num(form.capacity),
        carrierCategory: isLoadPort ? form.carrierCategory || null : null,
        loadPurpose: isLoadPort ? form.loadPurpose || null : null,
        contaminationLevel:
          form.unitType === 'PROCESS_CHAMBER' ? form.contaminationLevel.trim() || null : null,
        enabled: form.enabled,
      };
      if (initial) {
        await api.updateEquipmentUnit(equipmentId, initial.id, payload);
      } else {
        await api.createEquipmentUnit(equipmentId, { unitCode: form.code.trim(), ...payload });
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
      titleKey={initial ? UNIT_TITLE.editKey : UNIT_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => {
          // 形态联动：E120 节点类默认值 + 状态清空（跨词表的脏状态不留）
          setForm((prev) => ({
            ...prev,
            ...patch,
            unitClass:
              patch.unitType && patch.unitType !== prev.unitType
                ? (UNIT_CLASS_BY_TYPE[patch.unitType] ?? 'SUBSYSTEM')
                : prev.unitClass,
            state: patch.unitType && patch.unitType !== prev.unitType ? '' : prev.state,
          }));
        }}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
