import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useDict,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import { useHasPermission } from '@/lib/usePermission';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import LocationTree, {
  buildLocTree,
  collectSubtreeIds,
  filterLocTree,
} from '@/components/LocationTree';
import type { Capability, Equipment, EquipmentType, EquipmentUnit } from '@/types';

/**
 * 设备列表（EquipmentListPage）——搬运设备页与设备页的共享实现。
 *
 * <p>一张表两个视图（行业 Process/Non-Process 两分法）：view=process 只见非 MATERIAL
 * 大类设备（绑 Stage），view=amhs 只见 MATERIAL（AMHS 静态台账：OHT/OHS/Stocker/
 * OHB/NTB）。左侧位置树选中后按「设备位置落在该子树内」过滤；表单选中设备类型后
 * 联动展示其大类/细类（kind/class 由类型继承）；stateCode 是状态快照（E10 六态词表，
 * 先手工维护、后续接 EAP 事件），页面刻意不出现「当前位置/电量」这类动态字段（归 MCS）。</p>
 */

const NO_EQP: Equipment[] = [];
const NO_TYPES: EquipmentType[] = [];
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

/** 形态 → 可选状态（E87 端口态 / E157 腔态 / E116 EPT，按形态过滤统一词表）。 */
const UNIT_STATES_BY_TYPE: Record<string, string[]> = {
  LOAD_PORT: ['READY_TO_LOAD', 'READY_TO_UNLOAD', 'TRANSFER_BLOCKED', 'OUT_OF_SERVICE'],
  PROCESS_CHAMBER: ['IDLE', 'EXECUTING', 'DOWN', 'PM'],
  ALIGNER: ['IDLE', 'BUSY', 'DOWN'],
  ROBOT: ['IDLE', 'BUSY', 'DOWN'],
  LOAD_LOCK: ['IDLE', 'BUSY', 'DOWN'],
  BUFFER: ['IDLE', 'BUSY', 'DOWN'],
  OTHER: ['IDLE', 'BUSY', 'DOWN'],
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

export default function EquipmentListPage({ view }: { view: 'process' | 'amhs' }) {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const stateDict = useDict('mds.equip_state');
  const kindDict = useDict('mds.equip_kind');
  const classDict = useDict('mds.equip_class');

  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Equipment | null>(null);
  const [capping, setCapping] = useState<Equipment | null>(null);
  const [uniting, setUniting] = useState<Equipment | null>(null);

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

  const eqpRes = useResource(({ signal }) => api.listEquipments({ view }, signal));
  const typeRes = useResource(({ signal }) => api.listEquipmentTypes(undefined, signal));

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

  const rows = useMemo(() => {
    let list = eqpRes.data ?? NO_EQP;
    if (typeFilter) list = list.filter((e) => e.typeCode === typeFilter);
    if (selectedKey) list = list.filter((e) => subtreeIds.has(e.locationId));
    const kw = keyword.trim().toLowerCase();
    if (kw) {
      list = list.filter(
        (e) =>
          e.equipmentCode.toLowerCase().includes(kw) ||
          e.equipmentName.toLowerCase().includes(kw) ||
          e.typeCode.toLowerCase().includes(kw) ||
          (e.typeName ?? '').toLowerCase().includes(kw),
      );
    }
    return list;
  }, [eqpRes.data, typeFilter, selectedKey, subtreeIds, keyword]);

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

  /** 页面视图允许的类型（process→kind≠MATERIAL；amhs→MATERIAL——行业 Process/Non-Process 两分法）。 */
  const viewTypes = useMemo(() => {
    const list = typeRes.data ?? NO_TYPES;
    return view === 'process'
      ? list.filter((tp) => tp.kindCode !== 'MATERIAL')
      : list.filter((tp) => tp.kindCode === 'MATERIAL');
  }, [typeRes.data, view]);

  const typeOptions = useMemo(
    () => viewTypes.map((tp) => ({ value: tp.typeCode, label: `${tp.typeCode} ${tp.typeName}` })),
    [viewTypes],
  );

  const filterOptions = useMemo(
    () => [{ value: '', label: t('mds.equipment.allTypes') }, ...typeOptions],
    [typeOptions, t],
  );

  const columns = useMemo<Array<DataTableColumn<Equipment>>>(
    () => [
      { key: 'equipmentCode', titleKey: 'mds.equipment.code', width: 160, mono: true },
      { key: 'equipmentName', titleKey: 'mds.equipment.name', width: 180 },
      {
        key: 'typeCode',
        titleKey: 'mds.equipment.type',
        width: 170,
        render: (r) => `${r.typeCode}${r.typeName ? ` ${r.typeName}` : ''}`,
      },
      {
        key: 'typeKind',
        titleKey: 'mds.eqpType.kind',
        width: 110,
        render: (r) => <span className="mono">{r.typeKind ? kindDict.labelOf(r.typeKind) : '—'}</span>,
      },
      {
        key: 'typeClass',
        titleKey: 'mds.eqpType.class',
        width: 120,
        render: (r) => {
          const tp = (typeRes.data ?? NO_TYPES).find((x) => x.typeCode === r.typeCode);
          return <span className="mono">{tp?.equipClassCode ? classDict.labelOf(tp.equipClassCode) : '—'}</span>;
        },
      },
      {
        key: 'location',
        titleKey: 'mds.equipment.location',
        width: 200,
        render: (r) => locLabel.get(r.locationId) ?? `${r.locationType}/${r.locationId}`,
      },
      {
        key: 'stateCode',
        titleKey: 'mds.equipment.state',
        width: 110,
        render: (r) => <span className="mono">{stateDict.labelOf(r.stateCode)}</span>,
      },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 80,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 250,
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
    [t, locLabel, kindDict, classDict, typeRes.data],
  );

  return (
    <Page>
      <div className="topo topo-equipment">
        <aside className="topo-tree">
          <div className="topo-toolbar">
            <select
              className="topo-group-filter"
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
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
            columns={columns}
            rows={rows}
            rowKey={(r) => r.id}
            loading={eqpRes.loading}
            error={eqpRes.error?.message ?? null}
            onRetry={eqpRes.refresh}
            headActions={
              <Can perm={MDS_PERMS.equipment.create}>
                <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
                  {t('mds.equipment.createNew')}
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
          view={view}
          types={viewTypes}
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
          view={view}
          types={viewTypes}
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

/** 设备表单的位置层选项（PROCESS 只能 STAGE；其余类别 FAB/AREA/BAY——与后端校验同口径）。 */
function locationTypeOptions(view: 'process' | 'amhs') {
  if (view === 'process') return ['STAGE'] as const;
  return ['FAB', 'AREA', 'BAY'] as const;
}

/** 设备新建/编辑弹窗（类型下拉按页面视图过滤大类；选中类型后联动展示其大类/细类；位置联动选层+选记录）。 */
function EquipmentFormModal({
  view,
  types,
  initial,
  onClose,
  onDone,
}: {
  view: 'process' | 'amhs';
  types: EquipmentType[];
  initial: Equipment | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const kindDict = useDict('mds.equip_kind');
  const classDict = useDict('mds.equip_class');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.equipmentCode ?? '',
    name: initial?.equipmentName ?? '',
    typeCode: initial?.typeCode ?? '',
    locationType: (initial?.locationType ?? locationTypeOptions(view)[0]) as string,
    locationId: initial?.locationId ?? '',
    state: initial?.stateCode ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  // 类型选项（按页面视图过滤大类）+ 选中的类型对象（驱动大类/细类联动展示）
  const typeOptions = useMemo(
    () => types.map((tp) => ({ value: tp.typeCode, label: `${tp.typeCode} ${tp.typeName}` })),
    [types],
  );
  const selectedType = useMemo(
    () => types.find((tp) => tp.typeCode === form.typeCode) ?? null,
    [types, form.typeCode],
  );

  // 位置层选项（页面视图口径；编辑时若原层不在视图选项里也保留显示）
  const locTypeOptions = useMemo(() => {
    const base = locationTypeOptions(view).map((lt) => ({
      value: lt,
      label: t(LOC_LABEL[lt].labelKey),
    }));
    if (initial && !locationTypeOptions(view).includes(initial.locationType as never)) {
      base.unshift({
        value: initial.locationType,
        label: t(LOC_LABEL[initial.locationType as keyof typeof LOC_LABEL].labelKey),
      });
    }
    return base;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, initial]);

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

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
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
        key: 'typeCode' as const,
        labelKey: 'mds.equipment.type',
        required: true,
        options: typeOptions,
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
        dict: 'mds.equip_state',
        allowClear: true,
      },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark', span: 2 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    [initial, typeOptions, locTypeOptions, locRecordOptions],
  );

  async function submit() {
    if (!form.name.trim() || !form.typeCode || !form.locationType || !form.locationId) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateEquipment(initial.id, {
          equipmentName: form.name.trim(),
          typeCode: form.typeCode,
          locationType: form.locationType as Equipment['locationType'],
          locationId: form.locationId,
          stateCode: form.state || null,
          remark: form.remark || null,
          enabled: form.enabled,
        });
      } else {
        await api.createEquipment({
          equipmentCode: form.code.trim(),
          equipmentName: form.name.trim(),
          typeCode: form.typeCode,
          locationType: form.locationType as Equipment['locationType'],
          locationId: form.locationId,
          stateCode: form.state || null,
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
      titleKey={initial ? EQP_TITLE.editKey : EQP_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={520}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => {
          // 换位置层时清空已选记录，避免留下跨层的脏 locationId
          setForm((prev) => ({
            ...prev,
            ...patch,
            locationId: patch.locationType && patch.locationType !== prev.locationType ? '' : prev.locationId,
          }));
        }}
        columns={2}
      />
      {selectedType && (
        <p className="eqp-type-link">
          {t('mds.eqpType.kind')}
          ：<span className="mono">{kindDict.labelOf(selectedType.kindCode)}</span>
          {selectedType.equipClassCode ? (
            <>
              {' · '}
              {t('mds.eqpType.class')}
              ：<span className="mono">{classDict.labelOf(selectedType.equipClassCode)}</span>
            </>
          ) : null}
        </p>
      )}
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 设备↔能力关联弹窗（全量替换提交；checkbox 勾选）。 */
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
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const capRes = useResource(({ signal }) => api.listCapabilities(undefined, signal));
  const linkRes = useResource(({ signal }) =>
    api.listEquipmentCapabilities(equipment.id, signal),
  );

  const options = useMemo(
    () =>
      (capRes.data ?? NO_CAPS)
        .filter((c) => c.enabled)
        .map((c) => ({ value: c.capabilityCode, label: `${c.capabilityCode} ${c.capabilityName}` })),
    [capRes.data],
  );

  const [form, setForm] = useState<{ caps: string[] }>({ caps: [] });
  // 现有关联到达后初始化勾选（只初始化一次）
  useEffect(() => {
    if (linkRes.data) setForm({ caps: linkRes.data });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkRes.data]);

  const fields = useMemo<Array<FormField<{ caps: string[] }>>>(
    () => [
      {
        kind: 'checkbox' as const,
        key: 'caps' as const,
        labelKey: 'mds.equipment.capabilities',
        options,
        span: 2,
      },
    ],
    [options],
  );

  async function submit() {
    setBusy(true);
    setErr('');
    try {
      await api.setEquipmentCapabilities(equipment.id, form.caps);
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
      width={560}
    >
      {linkRes.loading ? (
        <p className="dim">{t('mds.common.loading')}</p>
      ) : (
        <FormGrid
          fields={fields}
          value={form}
          onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          columns={2}
        />
      )}
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 子单元形态下拉选项（E120 形态枚举；文案走 UNIT_TYPE_LABEL）。 */
const unitTypeOptions = (t: (k: string) => string) =>
  UNIT_TYPE_OPTIONS.map((ut) => ({ value: ut, label: t(UNIT_TYPE_LABEL[ut].labelKey) }));

/**
 * 设备子单元管理弹窗——设备结构树维护（SEMI E120 CEM）。
 *
 * <p>unitCode 设备内唯一、建后不可改；unitClass 随形态联动默认值（腔=MODULE，
 * 其余=SUBSYSTEM）；状态选项按形态过滤（端口走 E87 词表、腔走 E157 词表、
 * 其余走 E116 EPT 简化三态）。写操作守门父设备编辑锁（后端）。</p>
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
      { key: 'unitName', titleKey: 'mds.unit.name', width: 190 },
      {
        key: 'unitType',
        titleKey: 'mds.unit.type',
        width: 130,
        render: (r) => (
          <span className="mono">
            {t(UNIT_TYPE_LABEL[r.unitType as keyof typeof UNIT_TYPE_LABEL]?.labelKey ?? 'mds.unit.type.OTHER')}
          </span>
        ),
      },
      {
        key: 'unitClass',
        titleKey: 'mds.unit.class',
        width: 110,
        render: (r) => <span className="mono">{r.unitClass}</span>,
      },
      {
        key: 'stateCode',
        titleKey: 'mds.unit.state',
        width: 120,
        render: (r) => <span className="mono">{unitStateDict.labelOf(r.stateCode)}</span>,
      },
      {
        key: 'port',
        titleKey: 'mds.unit.port',
        width: 110,
        render: (r) =>
          r.unitType === 'LOAD_PORT'
            ? `#${r.portNo ?? '—'} / ${r.capacity ?? '—'}`
            : '—',
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
            {t('mds.unit.createNew')}
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

/** 子单元新建/编辑弹窗（形态联动 E120 节点类与可选状态；unitCode 建后不可改）。 */
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
    enabled: initial?.enabled ?? true,
  });

  // 状态选项按形态过滤（词表统一、口径按形态收窄；E87 端口态 / E157 腔态 / E116 EPT）
  const stateOptions = useMemo(() => {
    const allowed = UNIT_STATES_BY_TYPE[form.unitType] ?? [];
    return unitStateDict.items
      .filter((i) => allowed.includes(i.value) && !i.disabled)
      .map((i) => ({ value: i.value, label: i.label }));
  }, [form.unitType, unitStateDict.items]);

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
          ] as Array<FormField<typeof form>>)
        : []),
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, form.unitType, stateOptions],
  );

  async function submit() {
    if (!form.name.trim() || (!initial && !form.code.trim())) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const portNo = form.portNo === '' ? null : Number(form.portNo);
    const capacity = form.capacity === '' ? null : Number(form.capacity);
    try {
      if (initial) {
        await api.updateEquipmentUnit(equipmentId, initial.id, {
          unitName: form.name.trim(),
          unitType: form.unitType,
          unitClass: form.unitClass as EquipmentUnit['unitClass'],
          stateCode: form.state || null,
          portNo,
          capacity,
          enabled: form.enabled,
        });
      } else {
        await api.createEquipmentUnit(equipmentId, {
          unitCode: form.code.trim(),
          unitName: form.name.trim(),
          unitType: form.unitType,
          unitClass: form.unitClass as EquipmentUnit['unitClass'],
          stateCode: form.state || null,
          portNo,
          capacity,
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
      titleKey={initial ? UNIT_TITLE.editKey : UNIT_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={520}
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
