import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
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
import type { Consumable, Equipment } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 耗材台账（ConsumablePage）——生产辅助物料台账级主数据（V34）。
 *
 * <p>布局为「左列表 + 右详情」主从结构。consCode 全局唯一建后不可改；类别/单位/
 * 寿命方式挂业务字典（mds.consumable_type / mds.measure_unit / mds.consumable_life_type）；
 * 寿命三轨（次数/天数/产量）+ 预警阈值；库存水位为台账快照（领用流水归物料域）；
 * 存放库下拉 = MATERIAL 大类设备。</p>
 */

const NO_EQP: Equipment[] = [];

interface ConsFormState {
  consCode: string;
  consName: string;
  specModel: string;
  consType: string;
  unit: string;
  vendor: string;
  partNo: string;
  expiryDate: string;
  lifeType: string;
  lifeLimit: string;
  lifeUsed: string;
  warnPercent: string;
  stockQty: string;
  minQty: string;
  homeEquipmentId: string;
  enabled: boolean;
}

function formFrom(row: Consumable | null): ConsFormState {
  return {
    consCode: row?.consCode ?? '',
    consName: row?.consName ?? '',
    specModel: row?.specModel ?? '',
    consType: row?.consType ?? '',
    unit: row?.unit ?? '',
    vendor: row?.vendor ?? '',
    partNo: row?.partNo ?? '',
    expiryDate: row?.expiryDate ?? '',
    lifeType: row?.lifeType ?? '',
    lifeLimit: row?.lifeLimit != null ? String(row.lifeLimit) : '',
    lifeUsed: row?.lifeUsed != null ? String(row.lifeUsed) : '0',
    warnPercent: row?.warnPercent != null ? String(row.warnPercent) : '',
    stockQty: row?.stockQty != null ? String(row.stockQty) : '0',
    minQty: row?.minQty != null ? String(row.minQty) : '',
    homeEquipmentId: row?.homeEquipmentId ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function ConsumablePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const typeDict = useDict('mds.consumable_type');
  const unitDict = useDict('mds.measure_unit');
  const lifeDict = useDict('mds.consumable_life_type');

  // 存放库下拉 = MATERIAL 大类设备（Stocker/OHB/NTB 等存储设备）
  const eqpRes = useResource(({ signal }) => api.listEquipments({ view: 'amhs' }, signal));
  const equipmentOptions = useMemo(
    () =>
      (eqpRes.data ?? NO_EQP).map((e) => ({
        value: e.id,
        label: `${e.equipmentCode} ${e.equipmentName}`,
      })),
    [eqpRes.data],
  );

  const [form, setForm] = useState<ConsFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<Consumable>({
    fetchList: (signal) => api.listConsumables(undefined, signal),
    remove: (id) => api.deleteConsumable(id),
    lockType: 'consumable',
    filter: (r, kw) =>
      r.consCode.toLowerCase().includes(kw) || r.consName.toLowerCase().includes(kw),
    isDirty: () => modeRef.current !== 'view' && JSON.stringify(form) !== initialJsonRef.current,
  });
  modeRef.current = md.mode;

  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: Consumable) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const num = (v: string) => (v === '' ? null : Number(v));

  const save = async () => {
    md.setErr('');
    if (!form.consName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        consName: form.consName.trim(),
        specModel: form.specModel.trim() || null,
        consType: form.consType || null,
        unit: form.unit || null,
        vendor: form.vendor.trim() || null,
        partNo: form.partNo.trim() || null,
        expiryDate: form.expiryDate || null,
        lifeType: form.lifeType || null,
        lifeLimit: num(form.lifeLimit),
        lifeUsed: form.lifeUsed === '' ? 0 : Number(form.lifeUsed),
        warnPercent: num(form.warnPercent),
        stockQty: form.stockQty === '' ? 0 : Number(form.stockQty),
        minQty: num(form.minQty),
        homeEquipmentId: form.homeEquipmentId || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createConsumable({ consCode: form.consCode.trim(), ...payload })
          : await api.updateConsumable(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<Consumable>>>(
    () => [
      { key: 'consCode', titleKey: 'mds.consumable.code', width: 140, mono: true },
      { key: 'consName', titleKey: 'mds.consumable.name', width: 200, ellipsis: true, render: (r) => <span className="flow-name-main">{r.consName}</span> },
      { key: 'specModel', titleKey: 'mds.consumable.spec', width: 130, mono: true, render: (r) => r.specModel ?? '—' },
      { key: 'consType', titleKey: 'mds.consumable.type', width: 100, render: (r) => r.consType ?? '—' },
      { key: 'vendor', titleKey: 'mds.consumable.vendor', width: 110, ellipsis: true, render: (r) => r.vendor ?? '—' },
      { key: 'partNo', titleKey: 'mds.consumable.partNo', width: 110, mono: true, ellipsis: true, render: (r) => r.partNo ?? '—' },
      { key: 'expiryDate', titleKey: 'mds.consumable.expiry', width: 110, render: (r) => r.expiryDate ?? '—' },
      { key: 'stockQty', titleKey: 'mds.consumable.stock', width: 100, render: (r) => `${r.stockQty}${r.unit ? ' ' + r.unit : ''}` },
      { key: 'minQty', titleKey: 'mds.consumable.min', width: 90, render: (r) => r.minQty ?? '—' },
      {
        key: 'life',
        titleKey: 'mds.consumable.life',
        width: 120,
        render: (r) => (r.lifeLimit != null ? `${r.lifeUsed} / ${r.lifeLimit}` : (r.lifeType ?? '—')),
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
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      <Can perm={MDS_PERMS.consumable.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.consumable.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const fields = useMemo<Array<FormField<ConsFormState>>>(
    () => [
      { kind: 'text', key: 'consCode', labelKey: 'mds.consumable.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'consName', labelKey: 'mds.consumable.name', required: true },
      { kind: 'text', key: 'specModel', labelKey: 'mds.consumable.spec', mono: true },
      {
        kind: 'select',
        key: 'consType',
        labelKey: 'mds.consumable.type',
        allowClear: true,
        options: typeDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'unit',
        labelKey: 'mds.consumable.unit',
        allowClear: true,
        options: unitDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      { kind: 'text', key: 'vendor', labelKey: 'mds.consumable.vendor' },
      { kind: 'text', key: 'partNo', labelKey: 'mds.consumable.partNo', mono: true },
      { kind: 'date', key: 'expiryDate', labelKey: 'mds.consumable.expiry', valueFormat: 'YYYY-MM-DD', displayFormat: 'YYYY-MM-DD' },
      {
        kind: 'select',
        key: 'lifeType',
        labelKey: 'mds.consumable.lifeType',
        allowClear: true,
        options: lifeDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      { kind: 'number', key: 'lifeLimit', labelKey: 'mds.consumable.lifeLimit', min: 0 },
      { kind: 'number', key: 'lifeUsed', labelKey: 'mds.consumable.lifeUsed', min: 0 },
      { kind: 'number', key: 'warnPercent', labelKey: 'mds.consumable.warnPercent', min: 0, max: 100, unit: '%' },
      { kind: 'number', key: 'stockQty', labelKey: 'mds.consumable.stock', min: 0 },
      { kind: 'number', key: 'minQty', labelKey: 'mds.consumable.min', min: 0 },
      {
        kind: 'select',
        key: 'homeEquipmentId',
        labelKey: 'mds.consumable.homeEqp',
        allowClear: true,
        options: equipmentOptions,
      },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, equipmentOptions, typeDict.items, unitDict.items, lifeDict.items],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<Consumable>
          titleKey="mds.consumable.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.consumable.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.consumable.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.consumable.delete}>
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
                  <h3 className="op-detail-title">{t('mds.consumable.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.consCode}</h3>
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
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<ConsFormState>) }))}
                columns={2}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
