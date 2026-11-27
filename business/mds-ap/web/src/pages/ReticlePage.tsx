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
import type { Equipment, Reticle } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 光罩管理（ReticlePage）——光刻关键耗材级主数据（V32）。
 *
 * <p>布局为「左列表 + 右详情」主从结构。reticleCode 全局唯一建后不可改（工序/
 * 流程步骤 reticleRef 按 code 引用本表，写入侧后端校验存在性、删除反向引用拒删，
 * 引用闭环）；状态挂 mds.reticle_status 字典；寿命双轨 = 使用次数
 * （usageCount/usageLimit）+ 有效期 expiryDate；存放库下拉 = MATERIAL 大类设备。</p>
 */

const NO_EQP: Equipment[] = [];

interface ReticleFormState {
  reticleCode: string;
  reticleName: string;
  layer: string;
  waferSize: string;
  plateSize: string;
  techCode: string;
  status: string;
  homeEquipmentId: string;
  usageCount: string;
  usageLimit: string;
  expiryDate: string;
  enabled: boolean;
}

function formFrom(row: Reticle | null): ReticleFormState {
  return {
    reticleCode: row?.reticleCode ?? '',
    reticleName: row?.reticleName ?? '',
    layer: row?.layer ?? '',
    waferSize: row?.waferSize ?? '',
    plateSize: row?.plateSize ?? '',
    techCode: row?.techCode ?? '',
    status: row?.status ?? '',
    homeEquipmentId: row?.homeEquipmentId ?? '',
    usageCount: row?.usageCount != null ? String(row.usageCount) : '0',
    usageLimit: row?.usageLimit != null ? String(row.usageLimit) : '',
    expiryDate: row?.expiryDate ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

export default function ReticlePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const statusDict = useDict('mds.reticle_status');

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

  const [form, setForm] = useState<ReticleFormState>(() => formFrom(null));
  const initialJsonRef = useRef(EMPTY_JSON);
  const modeRef = useRef<DetailMode>('view');

  // 制程技术下拉（techCode 逻辑引用守门在后端，前端只做选择）
  const [techs, setTechs] = useState<Awaited<ReturnType<typeof api.listTechnologies>>>([]);
  useEffect(() => {
    let alive = true;
    api.listTechnologies().then((rows) => {
      if (alive) setTechs(rows);
    }).catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  const techOptions = useMemo(
    () => techs.map((x) => ({ value: x.techCode, label: `${x.techCode} · ${x.techName}` })),
    [techs],
  );

  const md = useMasterDetail<Reticle>({
    fetchList: (signal) => api.listReticles(undefined, signal),
    remove: (id) => api.deleteReticle(id),
    lockType: 'reticle',
    filter: (r, kw) =>
      r.reticleCode.toLowerCase().includes(kw) || r.reticleName.toLowerCase().includes(kw),
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

  const openEdit = async (row: Reticle) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const save = async () => {
    md.setErr('');
    if (!form.reticleName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        reticleName: form.reticleName.trim(),
        layer: form.layer.trim() || null,
        waferSize: form.waferSize || null,
        plateSize: form.plateSize || null,
        techCode: form.techCode || null,
        status: form.status || null,
        homeEquipmentId: form.homeEquipmentId || null,
        usageCount: form.usageCount === '' ? 0 : Number(form.usageCount),
        usageLimit: form.usageLimit === '' ? null : Number(form.usageLimit),
        expiryDate: form.expiryDate || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createReticle({ reticleCode: form.reticleCode.trim(), ...payload })
          : await api.updateReticle(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<Reticle>>>(
    () => [
      { key: 'reticleCode', titleKey: 'mds.reticle.code', width: 140, mono: true },
      { key: 'reticleName', titleKey: 'mds.reticle.name', width: 200, ellipsis: true, render: (r) => <span className="flow-name-main">{r.reticleName}</span> },
      { key: 'layer', titleKey: 'mds.reticle.layer', width: 90, render: (r) => r.layer ?? '—' },
      { key: 'waferSize', titleKey: 'mds.reticle.waferSize', width: 80, render: (r) => (r.waferSize ? `${r.waferSize}"` : '—') },
      { key: 'plateSize', titleKey: 'mds.reticle.plateSize', width: 90, render: (r) => r.plateSize ?? '—' },
      { key: 'techCode', titleKey: 'mds.reticle.techCode', width: 100, mono: true, render: (r) => r.techCode ?? '—' },
      { key: 'status', titleKey: 'mds.reticle.status', width: 100, render: (r) => r.status ?? '—' },
      { key: 'homeEquipmentCode', titleKey: 'mds.reticle.homeEqp', width: 140, render: (r) => r.homeEquipmentCode ?? '—' },
      {
        key: 'usage',
        titleKey: 'mds.reticle.usage',
        width: 120,
        render: (r) => (r.usageLimit != null ? `${r.usageCount} / ${r.usageLimit}` : String(r.usageCount)),
      },
      { key: 'expiryDate', titleKey: 'mds.reticle.expiry', width: 120, render: (r) => r.expiryDate ?? '—' },
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
      <Can perm={MDS_PERMS.reticle.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
      <Can perm={MDS_PERMS.reticle.delete}>
        <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
          {t('mds.common.delete')}
        </CimButton>
      </Can>
    </>
  ) : null;

  const fields = useMemo<Array<FormField<ReticleFormState>>>(
    () => [
      { kind: 'text', key: 'reticleCode', labelKey: 'mds.reticle.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'reticleName', labelKey: 'mds.reticle.name', required: true },
      { kind: 'text', key: 'layer', labelKey: 'mds.reticle.layer', mono: true },
      { kind: 'select', key: 'waferSize', labelKey: 'mds.reticle.waferSize', dict: 'mds.wafer_size', allowClear: true },
      { kind: 'select', key: 'plateSize', labelKey: 'mds.reticle.plateSize', dict: 'mds.reticle_plate_size', allowClear: true },
      {
        kind: 'select',
        key: 'techCode',
        labelKey: 'mds.reticle.techCode',
        options: techOptions,
        allowClear: true,
      },
      {
        kind: 'select',
        key: 'status',
        labelKey: 'mds.reticle.status',
        allowClear: true,
        options: statusDict.items.filter((i) => !i.disabled).map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'select',
        key: 'homeEquipmentId',
        labelKey: 'mds.reticle.homeEqp',
        allowClear: true,
        options: equipmentOptions,
      },
      { kind: 'number', key: 'usageCount', labelKey: 'mds.reticle.usageCount', min: 0 },
      { kind: 'number', key: 'usageLimit', labelKey: 'mds.reticle.usageLimit', min: 0 },
      { kind: 'date', key: 'expiryDate', labelKey: 'mds.reticle.expiry', valueFormat: 'YYYY-MM-DD', displayFormat: 'YYYY-MM-DD' },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, equipmentOptions, statusDict.items, techOptions],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<Reticle>
          titleKey="mds.reticle.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.reticle.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.reticle.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.reticle.delete}>
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
                  <h3 className="op-detail-title">{t('mds.reticle.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.reticleCode}</h3>
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
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<ReticleFormState>) }))}
                columns={2}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
