import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Can,
  CimButton,
  DataTable,
  EmptyState,
  FormGrid,
  Page,
  Panel,
  useDict,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail, type DetailMode } from '@/lib/useMasterDetail';
import type { EquipmentType, EqKind } from '@/types';

/**
 * 设备类型（EquipmentTypePage，动线改版）——设备域「类」主数据（ISA-95 Kind→Type—Instance）。
 *
 * <p>布局为「左列表 + 右详情」主从结构（{@link useMasterDetail} 统一动线）：高频动线
 * （搜索 → 点选 → 就地编辑 → 保存）零弹窗；新建/批量删除在列表工具栏；编辑/删除贴详情头。
 * 编辑先抢锁（type=equipmentType）、保存同事务清锁、取消显式释放。kindCode 是设备大类
 * （8 类行业定稿，页面归属依据：MATERIAL→搬运设备页，其余→设备页）；equipClassCode 是
 * 大类下的细类，级联下拉按大类过滤、后端守门从属。typeCode 建后不可改（设备按 code 引用）；
 * 被设备引用的类型不可删（后端守门 2004）。</p>
 */

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

interface TypeFormState {
  typeCode: string;
  typeName: string;
  kindCode: string;
  equipClassCode: string;
  remark: string;
  enabled: boolean;
}

function formFrom(row: EquipmentType | null): TypeFormState {
  return {
    typeCode: row?.typeCode ?? '',
    typeName: row?.typeName ?? '',
    kindCode: row?.kindCode ?? 'PROCESS',
    equipClassCode: row?.equipClassCode ?? '',
    remark: row?.remark ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function EquipmentTypePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const kindDict = useDict('mds.equip_kind');
  const classDict = useDict('mds.equip_class');

  const [form, setForm] = useState<TypeFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<EquipmentType>({
    fetchList: (signal) => api.listEquipmentTypes(undefined, signal),
    remove: (id) => api.deleteEquipmentType(id),
    lockType: 'equipmentType',
    filter: (r, kw) =>
      r.typeCode.toLowerCase().includes(kw) ||
      r.typeName.toLowerCase().includes(kw) ||
      r.kindCode.toLowerCase().includes(kw) ||
      (r.equipClassCode ?? '').toLowerCase().includes(kw),
    isDirty: () => modeRef.current !== 'view' && JSON.stringify(form) !== initialJsonRef.current,
  });
  modeRef.current = md.mode;

  // 查看态表单跟随选中行（防止切行后详情残留上一行的表单值）
  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: EquipmentType) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.typeName.trim() || !form.kindCode) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        typeName: form.typeName.trim(),
        kindCode: form.kindCode as EqKind,
        equipClassCode: form.equipClassCode || null,
        remark: form.remark || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createEquipmentType({ typeCode: form.typeCode.trim(), ...payload })
          : await api.updateEquipmentType(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  // 左列表列（瘦身：全字段在右侧详情，操作列删除）
  const columns = useMemo<Array<DataTableColumn<EquipmentType>>>(
    () => [
      { key: 'typeCode', titleKey: 'mds.eqpType.code', width: 180, mono: true },
      { key: 'typeName', titleKey: 'mds.eqpType.name', width: 180, ellipsis: true, render: (r) => <span className="flow-name-main">{r.typeName}</span> },
      {
        key: 'kindCode',
        titleKey: 'mds.eqpType.kind',
        width: 130,
        render: (r) => <span className="mono">{kindDict.labelOf(r.kindCode)}</span>,
      },
      {
        key: 'equipClassCode',
        titleKey: 'mds.eqpType.class',
        width: 160,
        render: (r) => (
          <span className="mono">{r.equipClassCode ? classDict.labelOf(r.equipClassCode) : '—'}</span>
        ),
      },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? 'flow-status flow-status-active' : 'flow-status flow-status-disabled'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      { key: 'remark', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.remark ?? '—' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, kindDict, classDict],
  );

  // 详情头动作（对象级，贴详情头）
  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.equipmentType.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.equipmentType.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  // 细类选项按大类过滤（从属映射 + 字典文案；后端 KIND_CLASSES 同口径守门）
  const classOptions = useMemo(() => {
    const allowed = KIND_CLASSES[form.kindCode] ?? [];
    return classDict.items
      .filter((i) => allowed.includes(i.value) && !i.disabled)
      .map((i) => ({ value: i.value, label: i.label }));
  }, [form.kindCode, classDict.items]);

  const fields = useMemo<Array<FormField<TypeFormState>>>(
    () => [
      { kind: 'text', key: 'typeCode', labelKey: 'mds.eqpType.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'typeName', labelKey: 'mds.eqpType.name', required: true },
      {
        kind: 'select',
        key: 'kindCode',
        labelKey: 'mds.eqpType.kind',
        required: true,
        options: kindDict.items
          .filter((i) => !i.disabled)
          .map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'equipClassCode',
        labelKey: 'mds.eqpType.class',
        allowClear: true,
        options: classOptions,
      },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
      { kind: 'textarea', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, kindDict.items, classOptions],
  );

  return (
    <Page>
      <div className="op-layout">
        <DataTable<EquipmentType>
          titleKey="mds.eqpType.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.eqpType.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.equipmentType.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.equipmentType.delete}>
              <CimButton
                size="small"
                danger
                disabled={!md.checkedKeys.length}
                onClick={() => void md.doBatchDelete()}
              >
                {t('mds.common.batchDelete')}
                {md.checkedKeys.length ? ` (${md.checkedKeys.length})` : ''}
              </CimButton>
            </Can>
          }
          onRefresh={md.res.refresh}
          selectedKeys={md.checkedKeys}
          onSelectionChange={md.setCheckedKeys}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
        />

        <Panel className="op-detail">
          {!md.selected && md.mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {md.mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.eqpType.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.typeCode}</h3>
                    <span className={md.selected!.enabled ? 'flow-status flow-status-active' : 'flow-status flow-status-disabled'}>
                      {md.selected!.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
                    </span>
                  </>
                )}
                {md.mode === 'edit' && <span className="op-editing-badge">{t('mds.common.editing')}</span>}
                <div className="op-detail-actions">
                  {md.mode === 'view' && headerActions}
                  {(md.mode === 'edit' || md.mode === 'create') && (
                    <>
                      <CimButton type="primary" size="small" disabled={md.busy} onClick={() => void save()}>
                        {t('mds.common.save')}
                      </CimButton>
                      <CimButton size="small" onClick={md.backToView}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </>
                  )}
                </div>
              </div>

              {md.err && <p className="op-error">{md.err}</p>}

              <FormGrid
                fields={fields}
                value={form}
                disabled={md.mode === 'view'}
                onChange={(patch: Record<string, unknown>) =>
                  setForm((f) => {
                    // 大类变更时清空细类（跨大类的脏细类不留）
                    const next = { ...f, ...(patch as Partial<TypeFormState>) };
                    if (patch.kindCode && patch.kindCode !== f.kindCode) next.equipClassCode = '';
                    return next;
                  })
                }
                columns={2}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
