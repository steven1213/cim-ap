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
import type { Carrier, Equipment } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 载具台账（CarrierPage）——SEMI E87 Carrier 口径，AMHS 全链物理载体主数据（V31）。
 *
 * <p>布局为「左列表 + 右详情」主从结构（{@link useMasterDetail} 统一动线）。载具号
 * carrierCode 是 MCS/MES 全链追踪的主键口径，建后不可改；类型/状态挂业务字典
 * （mds.carrier_type / mds.carrier_status）；归属库下拉 = MATERIAL 大类存储设备
 * （Stocker/OHB/NTB）；内容物 currentLotId 是 MES 批次号字符串引用（空 = 空载具）。</p>
 */

const NO_EQP: Equipment[] = [];

interface CarrierFormState {
  carrierCode: string;
  carrierName: string;
  carrierType: string;
  status: string;
  homeEquipmentId: string;
  currentLotId: string;
  waferQty: string;
  enabled: boolean;
}

function formFrom(row: Carrier | null): CarrierFormState {
  return {
    carrierCode: row?.carrierCode ?? '',
    carrierName: row?.carrierName ?? '',
    carrierType: row?.carrierType ?? '',
    status: row?.status ?? '',
    homeEquipmentId: row?.homeEquipmentId ?? '',
    currentLotId: row?.currentLotId ?? '',
    waferQty: row?.waferQty != null ? String(row.waferQty) : '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function CarrierPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const typeDict = useDict('mds.carrier_type');
  const statusDict = useDict('mds.carrier_status');

  // 归属库下拉 = MATERIAL 大类设备（Stocker/OHB/NTB 等存储设备）
  const eqpRes = useResource(({ signal }) => api.listEquipments({ view: 'amhs' }, signal));
  const equipmentOptions = useMemo(
    () =>
      (eqpRes.data ?? NO_EQP).map((e) => ({
        value: e.id,
        label: `${e.equipmentCode} ${e.equipmentName}`,
      })),
    [eqpRes.data],
  );

  const [form, setForm] = useState<CarrierFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<Carrier>({
    fetchList: (signal) => api.listCarriers(undefined, signal),
    remove: (id) => api.deleteCarrier(id),
    lockType: 'carrier',
    filter: (r, kw) =>
      r.carrierCode.toLowerCase().includes(kw) || r.carrierName.toLowerCase().includes(kw),
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

  const openEdit = async (row: Carrier) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.carrierName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        carrierName: form.carrierName.trim(),
        carrierType: form.carrierType || null,
        status: form.status || null,
        homeEquipmentId: form.homeEquipmentId || null,
        currentLotId: form.currentLotId.trim() || null,
        waferQty: form.waferQty === '' ? null : Number(form.waferQty),
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createCarrier({ carrierCode: form.carrierCode.trim(), ...payload })
          : await api.updateCarrier(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<Carrier>>>(
    () => [
      { key: 'carrierCode', titleKey: 'mds.carrier.code', width: 130, mono: true },
      { key: 'carrierName', titleKey: 'mds.carrier.name', width: 200, ellipsis: true, render: (r) => <span className="flow-name-main">{r.carrierName}</span> },
      { key: 'carrierType', titleKey: 'mds.carrier.type', width: 110, render: (r) => r.carrierType ?? '—' },
      { key: 'status', titleKey: 'mds.carrier.status', width: 110, render: (r) => r.status ?? '—' },
      { key: 'homeEquipmentCode', titleKey: 'mds.carrier.homeEqp', width: 140, render: (r) => r.homeEquipmentCode ?? '—' },
      { key: 'currentLotId', titleKey: 'mds.carrier.lot', width: 140, mono: true, render: (r) => r.currentLotId ?? '—' },
      { key: 'waferQty', titleKey: 'mds.carrier.qty', width: 90, render: (r) => r.waferQty ?? '—' },
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
      <Can perm={MDS_PERMS.carrier.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.carrier.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const fields = useMemo<Array<FormField<CarrierFormState>>>(
    () => [
      { kind: 'text', key: 'carrierCode', labelKey: 'mds.carrier.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'carrierName', labelKey: 'mds.carrier.name', required: true },
      {
        kind: 'select',
        key: 'carrierType',
        labelKey: 'mds.carrier.type',
        allowClear: true,
        options: typeDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'status',
        labelKey: 'mds.carrier.status',
        allowClear: true,
        options: statusDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'homeEquipmentId',
        labelKey: 'mds.carrier.homeEqp',
        allowClear: true,
        options: equipmentOptions,
      },
      { kind: 'text', key: 'currentLotId', labelKey: 'mds.carrier.lot', mono: true },
      { kind: 'number', key: 'waferQty', labelKey: 'mds.carrier.qty', min: 0 },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [equipmentOptions, typeDict.items, statusDict.items],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<Carrier>
          titleKey="mds.carrier.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.carrier.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.carrier.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.carrier.delete}>
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
                  <h3 className="op-detail-title">{t('mds.carrier.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.carrierCode}</h3>
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
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<CarrierFormState>) }))}
                columns={2}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
