import { useMemo, useState } from 'react';
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
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { EquipmentType } from '@/types';

/**
 * 设备类型（EquipmentType）——设备域「类」主数据（ISA-95 Kind→Type—Instance）。
 *
 * <p>kindCode 是设备大类（8 类行业定稿，页面归属依据：MATERIAL→搬运设备页，其余→设备页）；
 * equipClassCode 是大类下的细类（如 ETCH/DEP/PROBER），级联下拉按大类过滤、后端守门从属。
 * typeCode 建后不可改（设备按 code 引用）；被设备引用的类型不可删（后端守门 2004）。</p>
 */

const NO_TYPES: EquipmentType[] = [];

/** 大类→细类从属映射（与后端 EquipmentTypeAdminService.KIND_CLASSES 同值域，前端只做级联过滤）。 */
const KIND_CLASSES: Record<string, string[]> = {
  PROCESS: ['ETCH', 'DEP', 'IMP', 'LITHO', 'CLEAN', 'THERMAL', 'CMP', 'PLATING', 'RTP'],
  METRO: ['CDSEM', 'AFM', 'EL', 'WAT', 'INSPECT', 'REVIEW', 'PARTICLE'],
  TEST: ['PROBER', 'TESTER', 'HANDLER', 'BURNIN'],
  ASSY: ['GRIND', 'SAW', 'DA', 'WB', 'MOLD', 'MARK', 'SING'],
  MATERIAL: ['STOCKER', 'OHT', 'OHS', 'OHB', 'NTB', 'AGV', 'ROBOT', 'LOADER'],
  FACILITY: ['CHILLER', 'PUMP', 'GASBOX', 'WETSCRUBBER', 'FAN', 'ENV', 'POWER', 'GASMON'],
  TOOL: ['CHUCK', 'JIG', 'FIXTURE'],
  OTHER: ['UNCLASS'],
};

export default function EquipmentTypePage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const kindDict = useDict('mds.equip_kind');
  const classDict = useDict('mds.equip_class');

  const [keyword, setKeyword] = useState('');
  const typeRes = useResource(({ signal }) => api.listEquipmentTypes(undefined, signal));
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<EquipmentType | null>(null);

  const rows = useMemo(() => {
    const list = typeRes.data ?? NO_TYPES;
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (r) =>
        r.typeCode.toLowerCase().includes(kw) ||
        r.typeName.toLowerCase().includes(kw) ||
        r.kindCode.toLowerCase().includes(kw) ||
        (r.equipClassCode ?? '').toLowerCase().includes(kw),
    );
  }, [typeRes.data, keyword]);

  const openEdit = async (row: EquipmentType) => {
    try {
      await api.claimLock('equipmentType', row.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setEditing(row);
  };

  const closeEdit = () => {
    if (editing) {
      void api.releaseLock('equipmentType', editing.id).catch(() => {});
    }
    setEditing(null);
  };

  const doDelete = async (row: EquipmentType) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteEquipmentType(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    typeRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<EquipmentType>>>(
    () => [
      { key: 'typeCode', titleKey: 'mds.eqpType.code', width: 180, mono: true },
      { key: 'typeName', titleKey: 'mds.eqpType.name', width: 200 },
      {
        key: 'kindCode',
        titleKey: 'mds.eqpType.kind',
        width: 150,
        render: (r) => <span className="mono">{kindDict.labelOf(r.kindCode)}</span>,
      },
      {
        key: 'equipClassCode',
        titleKey: 'mds.eqpType.class',
        width: 200,
        render: (r) => (
          <span className="mono">{r.equipClassCode ? classDict.labelOf(r.equipClassCode) : '—'}</span>
        ),
      },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      { key: 'remark', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.remark ?? '—' },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.equipmentType.update}>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.equipmentType.delete}>
              <CimButton size="small" danger onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, kindDict, classDict],
  );

  return (
    <Page>
      <DataTable<EquipmentType>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={typeRes.loading}
        error={typeRes.error?.message ?? null}
        onRetry={typeRes.refresh}
        headActions={
          <Can perm={MDS_PERMS.equipmentType.create}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              {t('mds.eqpType.createNew')}
            </CimButton>
          </Can>
        }
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'mds.eqpType.searchPlaceholder',
        }}
        onRefresh={typeRes.refresh}
      />

      {creating && (
        <TypeFormModal
          initial={null}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            typeRes.refresh();
          }}
        />
      )}
      {editing && (
        <TypeFormModal
          initial={editing}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            typeRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

const TYPE_TITLE = {
  editKey: 'mds.eqpType.editTitle',
  createKey: 'mds.eqpType.createTitle',
} as const;

/** 设备类型新建/编辑弹窗（编辑锁由页面层抢/放；typeCode 建后不可改；细类随大类级联）。 */
function TypeFormModal({
  initial,
  onClose,
  onDone,
}: {
  initial: EquipmentType | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const kindDict = useDict('mds.equip_kind');
  const classDict = useDict('mds.equip_class');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.typeCode ?? '',
    name: initial?.typeName ?? '',
    kind: initial?.kindCode ?? 'PROCESS',
    equipClass: initial?.equipClassCode ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  // 细类选项按大类过滤（从属映射 + 字典文案；后端 KIND_CLASSES 同口径守门）
  const classOptions = useMemo(() => {
    const allowed = KIND_CLASSES[form.kind] ?? [];
    return classDict.items
      .filter((i) => allowed.includes(i.value) && !i.disabled)
      .map((i) => ({ value: i.value, label: i.label }));
  }, [form.kind, classDict.items]);

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'code' as const, labelKey: 'mds.eqpType.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.eqpType.name', required: true },
      {
        kind: 'select' as const,
        key: 'kind' as const,
        labelKey: 'mds.eqpType.kind',
        required: true,
        options: kindDict.items
          .filter((i) => !i.disabled)
          .map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select' as const,
        key: 'equipClass' as const,
        labelKey: 'mds.eqpType.class',
        allowClear: true,
        options: classOptions,
      },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark', span: 2 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, kindDict.items, classOptions],
  );

  async function submit() {
    if (!form.name.trim() || !form.kind) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateEquipmentType(initial.id, {
          typeName: form.name.trim(),
          kindCode: form.kind,
          equipClassCode: form.equipClass || null,
          remark: form.remark || null,
          enabled: form.enabled,
        });
      } else {
        await api.createEquipmentType({
          typeCode: form.code.trim(),
          typeName: form.name.trim(),
          kindCode: form.kind,
          equipClassCode: form.equipClass || null,
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
      titleKey={initial ? TYPE_TITLE.editKey : TYPE_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => {
          // 大类变更时清空细类（跨大类的脏细类不留）
          setForm((prev) => ({
            ...prev,
            ...patch,
            equipClass: patch.kind && patch.kind !== prev.kind ? '' : prev.equipClass,
          }));
        }}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
