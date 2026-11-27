import { useEffect, useMemo, useState } from 'react';
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
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { EdcPlan, EdcPlanDetail, EdcPlanItemPayload } from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useMasterDetail } from '@/lib/useMasterDetail';
import GuideBar from '@/components/GuideBar';
import ImportDialog from '@/components/ImportDialog';

/**
 * EDC 方案（EdcPlanPage，V28）——采集契约聚合层：绑 产品(可选)+流程+工序，
 * 明细 = 采集项 × Measurement Spec（规格限挂行，同参数按产品/工序差异化管控）。
 *
 * <p>四态生命周期镜像流程：UNFROZEN→FROZEN→ACTIVE→DISABLED（activate 降级同族旧版）；
 * 明细仅 UNFROZEN 可维护（发布数据只读）。动线：建方案 → 从参数集带入或 Excel 导入
 * 明细 → 调整采样/规格 → 冻结 → 激活。编辑锁 type=edcPlan。</p>
 */

interface PlanFormState {
  planCode: string;
  productCode: string;
  flowFamilyCode: string;
  flowVersionNo: string;
  opCode: string;
  description: string;
}

const NO_ITEMS: EdcPlanItemPayload[] = [];

function formFrom(row: EdcPlan | null): PlanFormState {
  return {
    planCode: row?.planCode ?? '',
    productCode: row?.productCode ?? '',
    flowFamilyCode: row?.flowFamilyCode ?? '',
    flowVersionNo: row?.flowVersionNo != null ? String(row.flowVersionNo) : '1',
    opCode: row?.opCode ?? '',
    description: row?.description ?? '',
  };
}

/** 状态徽标样式（与流程状态同色系）。 */
function statusClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'flow-status flow-status-active';
    case 'FROZEN': return 'flow-status flow-status-frozen';
    case 'DISABLED': return 'flow-status flow-status-disabled';
    default: return 'flow-status flow-status-draft';
  }
}

export default function EdcPlanPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const opsRes = useResource(({ signal }) => api.listOperations(undefined, signal));
  const setsRes = useResource(({ signal }) => api.listEdcParamSets(signal));
  const defsRes = useResource(({ signal }) => api.listParamDefs(signal));

  const md = useMasterDetail<EdcPlan>({
    fetchList: (signal) => api.listEdcPlans(signal),
    remove: (id) => api.deleteEdcPlan(id),
    lockType: 'edcPlan',
    filter: (r, kw) =>
      r.planCode.toLowerCase().includes(kw) ||
      r.flowFamilyCode.toLowerCase().includes(kw) ||
      (r.productCode ?? '').toLowerCase().includes(kw),
  });

  const [form, setForm] = useState<PlanFormState>(() => formFrom(null));
  const [detail, setDetail] = useState<EdcPlanDetail | null>(null);
  const [items, setItems] = useState<EdcPlanItemPayload[]>(NO_ITEMS);
  const [memberOp, setMemberOp] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const itemsEditable = md.mode === 'view' && detail?.status === 'UNFROZEN';

  useEffect(() => {
    if (md.mode !== 'view') return;
    setForm(formFrom(md.selected));
    setErr('');
    if (md.selected) {
      api.getEdcPlan(md.selected.id)
        .then((d) => {
          setDetail(d);
          setItems(d.items.map((i) => ({
            attrKey: i.attrKey,
            collectMode: i.collectMode,
            sampleRule: i.sampleRule,
            intervalMinutes: i.intervalMinutes ?? undefined,
            required: i.required,
            target: i.target ?? undefined,
            lsl: i.lsl ?? undefined,
            usl: i.usl ?? undefined,
            oocAction: i.oocAction,
            sortNo: i.sortNo,
          })));
        })
        .catch(() => setDetail(null));
    } else {
      setDetail(null);
      setItems(NO_ITEMS);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    setItems(NO_ITEMS);
    setDetail(null);
    setErr('');
    md.enterCreate();
  };

  const openEdit = async (row: EdcPlan) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    setErr('');
  };

  const savePlan = async () => {
    setErr('');
    if (!form.planCode.trim() || !form.flowFamilyCode.trim() || !form.opCode) {
      setErr(t('mds.edc.plan.errRequired'));
      return;
    }
    setBusy(true);
    try {
      const payload: api.EdcPlanPayload = {
        productCode: form.productCode.trim() || null,
        flowFamilyCode: form.flowFamilyCode.trim(),
        flowVersionNo: Number(form.flowVersionNo) || 1,
        opCode: form.opCode,
        description: form.description || null,
      };
      const ret = md.mode === 'create'
        ? await api.createEdcPlan({ planCode: form.planCode.trim(), ...payload })
        : await api.updateEdcPlan(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const saveItems = async () => {
    if (!md.selected) return;
    setErr('');
    setBusy(true);
    try {
      await api.saveEdcPlanItems(md.selected.id, items);
      toast.success(t('mds.common.saved'));
      const d = await api.getEdcPlan(md.selected.id);
      setDetail(d);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doLifecycle = async (action: 'freeze' | 'unfreeze' | 'activate' | 'disable') => {
    if (!md.selected) return;
    setErr('');
    setBusy(true);
    try {
      const fn = {
        freeze: api.freezeEdcPlan,
        unfreeze: api.unfreezeEdcPlan,
        activate: api.activateEdcPlan,
        disable: api.disableEdcPlan,
      }[action];
      await fn(md.selected.id);
      toast.success(t('mds.common.saved'));
      const d = await api.getEdcPlan(md.selected.id);
      setDetail(d);
      md.res.refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const applySet = async (setCode: string) => {
    if (!md.selected || !itemsEditable) return;
    setErr('');
    setBusy(true);
    try {
      const d = await api.applyEdcPlanSet(md.selected.id, setCode);
      setDetail(d);
      setItems(d.items.map((i) => ({
        attrKey: i.attrKey,
        collectMode: i.collectMode,
        sampleRule: i.sampleRule,
        intervalMinutes: i.intervalMinutes ?? undefined,
        required: i.required,
        target: i.target ?? undefined,
        lsl: i.lsl ?? undefined,
        usl: i.usl ?? undefined,
        oocAction: i.oocAction,
        sortNo: i.sortNo,
      })));
      toast.success(t('mds.common.saved'));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const memberCandidates = useMemo(() => {
    const inPlan = new Set(items.map((i) => i.attrKey));
    return (defsRes.data ?? []).filter((d) => d.enabled && !inPlan.has(d.attrKey));
  }, [defsRes.data, items]);

  const columns = useMemo<Array<DataTableColumn<EdcPlan>>>(
    () => [
      { key: 'planCode', titleKey: 'mds.edc.plan.colCode', width: 140, mono: true },
      { key: 'versionNo', titleKey: 'mds.edc.plan.colVersion', width: 70, mono: true, render: (r) => `V${r.versionNo}` },
      { key: 'status', titleKey: 'mds.edc.plan.colStatus', width: 100,
        render: (r) => <span className={statusClass(r.status)}>{t(`mds.edc.status.${r.status}`)}</span> },
      { key: 'productCode', titleKey: 'mds.edc.plan.colProduct', width: 130, mono: true,
        render: (r) => r.productCode ?? <span className="dim">{t('mds.edc.plan.generic')}</span> },
      { key: 'flow', titleKey: 'mds.edc.plan.colFlow', width: 160, mono: true,
        render: (r) => `${r.flowFamilyCode} V${r.flowVersionNo}` },
      { key: 'opCode', titleKey: 'mds.edc.plan.colOp', width: 120, mono: true },
      { key: 'activatedAt', titleKey: 'mds.edc.plan.colActivated', width: 140,
        render: (r) => r.activatedAt?.slice(0, 16).replace('T', ' ') ?? '—' },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  const opOptions = useMemo(
    () => (opsRes.data ?? [])
      .filter((o) => o.opType === 'PROCESS')
      .map((o) => ({ value: o.opCode, label: `${o.opCode} ${o.opName}` })),
    [opsRes.data],
  );

  const fields = useMemo<Array<FormField<PlanFormState>>>(
    () => [
      { kind: 'text', key: 'planCode', labelKey: 'mds.edc.plan.colCode', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'productCode', labelKey: 'mds.edc.plan.colProduct', mono: true, placeholderKey: 'mds.edc.plan.productHint' },
      { kind: 'text', key: 'flowFamilyCode', labelKey: 'mds.edc.plan.colFlow', mono: true, required: true },
      { kind: 'number', key: 'flowVersionNo', labelKey: 'mds.edc.plan.colVersion' },
      {
        kind: 'select',
        key: 'opCode',
        labelKey: 'mds.edc.plan.colOp',
        required: true,
        allowClear: false,
        options: opOptions,
      },
      { kind: 'textarea', key: 'description', labelKey: 'mds.common.remark', span: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, opOptions]);

  const itemColumns = useMemo<Array<DataTableColumn<{ id: string; attrKey: string; sortNo: number; param: api.EdcParamLite | null } & Record<string, unknown>>>>(
    () => [
      { key: 'attrKey', titleKey: 'mds.edc.colAttr', width: 160, mono: true },
      { key: 'name', titleKey: 'mds.edc.colAttrName', width: 150, ellipsis: true,
        render: (i) => i.param?.attrName ?? '—' },
      { key: 'unit', titleKey: 'mds.edc.colUnit', width: 80, render: (i) => i.param?.unit ?? '—' },
      { key: 'collectMode', titleKey: 'mds.edc.item.colMode', width: 90,
        render: (i) => t(`mds.edc.item.mode.${(i as unknown as EdcPlanItemPayload).collectMode ?? 'AUTO'}`) },
      { key: 'sampleRule', titleKey: 'mds.edc.item.colRule', width: 110,
        render: (i) => {
          const it = i as unknown as EdcPlanItemPayload;
          return t(`mds.edc.item.rule.${it.sampleRule ?? 'BY_LOT'}`)
            + (it.intervalMinutes ? ` · ${it.intervalMinutes}min` : '');
        } },
      { key: 'required', titleKey: 'mds.edc.item.colRequired', width: 70,
        render: (i) => ((i as unknown as EdcPlanItemPayload).required ? '✓' : '—') },
      { key: 'spec', titleKey: 'mds.edc.item.colSpec', width: 170, mono: true,
        render: (i) => {
          const it = i as unknown as EdcPlanItemPayload;
          const parts = [it.lsl, it.target, it.usl].map((v) => v ?? '·');
          return `${parts[0]} ≤ ${parts[1]} ≤ ${parts[2]}`.replace(/≤ · ≤/, '·');
        } },
      { key: 'oocAction', titleKey: 'mds.edc.item.colAction', width: 90,
        render: (i) => t(`mds.edc.item.action.${(i as unknown as EdcPlanItemPayload).oocAction ?? 'NONE'}`) },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  /** 明细表行：草稿态显示 items（编辑中含未保存行），查看态显示 detail.items。 */
  const itemRows = useMemo(() => {
    const base = items.map((i, idx) => ({ id: `${i.attrKey}-${idx}`, ...i }));
    return base as unknown as Array<{ id: string; attrKey: string; sortNo: number; param: api.EdcParamLite | null } & Record<string, unknown>>;
  }, [items, detail]);

  const lifecycleButtons = md.mode === 'view' && detail && (
    <>
      {detail.status === 'UNFROZEN' && (
        <Can perm={MDS_PERMS.edcPlan.update}>
          <CimButton size="small" disabled={busy} onClick={() => void doLifecycle('freeze')}>
            {t('mds.edc.plan.freeze')}
          </CimButton>
        </Can>
      )}
      {detail.status === 'FROZEN' && (
        <>
          <Can perm={MDS_PERMS.edcPlan.update}>
            <CimButton size="small" disabled={busy} onClick={() => void doLifecycle('unfreeze')}>
              {t('mds.edc.plan.unfreeze')}
            </CimButton>
          </Can>
          <Can perm={MDS_PERMS.edcPlan.update}>
            <CimButton type="primary" size="small" disabled={busy} onClick={() => void doLifecycle('activate')}>
              {t('mds.edc.plan.activate')}
            </CimButton>
          </Can>
          <Can perm={MDS_PERMS.edcPlan.update}>
            <CimButton size="small" disabled={busy} onClick={() => void doLifecycle('disable')}>
              {t('mds.edc.plan.disable')}
            </CimButton>
          </Can>
        </>
      )}
      {detail.status === 'ACTIVE' && (
        <Can perm={MDS_PERMS.edcPlan.update}>
          <CimButton size="small" danger disabled={busy} onClick={() => void doLifecycle('disable')}>
            {t('mds.edc.plan.disable')}
          </CimButton>
        </Can>
      )}
    </>
  );

  // 编辑/删除仅 UNFROZEN/DISABLED 可用（与后端 update/delete 守门同口径，ACTIVE/FROZEN 不出按钮）
  const mutable = md.selected?.status === 'UNFROZEN' || md.selected?.status === 'DISABLED';
  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      {lifecycleButtons}
      {mutable && (
        <Can perm={MDS_PERMS.edcPlan.update}>
          <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
            {t('mds.common.edit')}
          </CimButton>
        </Can>
      )}
      {mutable && (
        <Can perm={MDS_PERMS.edcPlan.delete}>
          <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
            {t('mds.common.delete')}
          </CimButton>
        </Can>
      )}
    </>
  ) : null;

  const setOptions = (setsRes.data ?? []).filter((s) => s.enabled !== false);

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<EdcPlan>
          titleKey="mds.edc.plan.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.edc.plan.searchHint' }}
          headActions={
            <Can perm={MDS_PERMS.edcPlan.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          onRefresh={md.res.refresh}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
          emptyTitleKey="mds.edc.plan.empty"
        />

        <Panel className="op-detail">
          {!md.selected && md.mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {md.mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.edc.plan.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{md.selected!.planCode} V{md.selected!.versionNo}</h3>
                    <span className={statusClass(md.selected!.status)}>
                      {t(`mds.edc.status.${md.selected!.status}`)}
                    </span>
                  </>
                )}
                {md.mode === 'edit' && <span className="op-editing-badge">{t('mds.common.editing')}</span>}
                <div className="op-detail-actions">
                  {md.mode === 'view' && headerActions}
                  {(md.mode === 'edit' || md.mode === 'create') && (
                    <>
                      <CimButton type="primary" size="small" disabled={md.busy} onClick={() => void savePlan()}>
                        {t('mds.common.save')}
                      </CimButton>
                      <CimButton size="small" onClick={md.backToView}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </>
                  )}
                </div>
              </div>

              {(err || md.err) && <p className="op-error">{err || md.err}</p>}

              <FormGrid
                fields={fields}
                value={form}
                disabled={md.mode === 'view'}
                onChange={(patch: Record<string, unknown>) =>
                  setForm((f) => ({ ...f, ...(patch as Partial<PlanFormState>) }))
                }
                columns={2}
              />

              {/* 明细块：查看态展示 + UNFROZEN 可维护/导入/集带入 */}
              {md.selected && md.mode !== 'create' && (
                <div className="pf-bind-section">
                  <div className="edc-plan-items-head">
                    <h4 className="pf-bind-key">{t('mds.edc.plan.items')}</h4>
                    {itemsEditable && (
                      <div className="op-detail-actions">
                        <select
                          className="topo-group-filter"
                          defaultValue=""
                          onChange={(e) => {
                            if (e.target.value) void applySet(e.target.value);
                            e.target.value = '';
                          }}
                        >
                          <option value="" disabled>{t('mds.edc.plan.applySet')}</option>
                          {setOptions.map((s) => (
                            <option key={s.id} value={s.setCode}>{s.setCode} {s.setName}</option>
                          ))}
                        </select>
                        <CimButton size="small" onClick={() => setImportOpen(true)}>
                          {t('mds.edc.importBtn')}
                        </CimButton>
                        {!memberOp && (
                          <CimButton size="small" onClick={() => setMemberOp(true)}>
                            {t('mds.edc.plan.addItem')}
                          </CimButton>
                        )}
                      </div>
                    )}
                  </div>

                  <DataTable
                    columns={itemColumns}
                    rows={itemRows}
                    rowKey={(i) => i.id}
                    loading={false}
                  />

                  {itemsEditable && memberOp && (
                    <div className="edc-item-adder">
                      <select
                        className="topo-group-filter"
                        onChange={(e) => {
                          const key = e.target.value;
                          if (key) {
                            setItems((xs) => [...xs, {
                              attrKey: key,
                              collectMode: 'AUTO',
                              sampleRule: 'BY_LOT',
                              required: false,
                              oocAction: 'NONE',
                              sortNo: xs.length,
                            }]);
                          }
                          setMemberOp(false);
                        }}
                        defaultValue=""
                      >
                        <option value="" disabled>{t('mds.edc.set.pickMember')}</option>
                        {memberCandidates.map((d) => (
                          <option key={d.id} value={d.attrKey}>{d.attrKey} {d.attrName}</option>
                        ))}
                      </select>
                      <CimButton size="small" onClick={() => setMemberOp(false)}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </div>
                  )}

                  {itemsEditable && items.length > 0 && (
                    <div className="edc-plan-items-foot">
                      <CimButton type="primary" size="small" disabled={busy} onClick={() => void saveItems()}>
                        {t('mds.edc.plan.saveItems')}
                      </CimButton>
                      <CimButton size="small" onClick={() => {
                        const d = detail;
                        setItems((d?.items ?? []).map((i) => ({
                          attrKey: i.attrKey,
                          collectMode: i.collectMode,
                          sampleRule: i.sampleRule,
                          intervalMinutes: i.intervalMinutes ?? undefined,
                          required: i.required,
                          target: i.target ?? undefined,
                          lsl: i.lsl ?? undefined,
                          usl: i.usl ?? undefined,
                          oocAction: i.oocAction,
                          sortNo: i.sortNo,
                        })));
                        setMemberOp(false);
                      }}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </div>
                  )}
                  {!itemsEditable && md.mode === 'view' && detail?.status !== 'UNFROZEN' && (
                    <p className="dim">{t('mds.edc.plan.itemsReadOnly')}</p>
                  )}
                </div>
              )}
            </div>
          )}
        </Panel>
      </div>

      <ImportDialog
        open={importOpen}
        onClose={() => setImportOpen(false)}
        titleKey="mds.edc.plan.importTitle"
        kind="PLAN_ITEM"
        targetId={md.selected?.id ?? null}
        templateName="edc-plan-items-template.xlsx"
        onDone={() => {
          if (md.selected) {
            api.getEdcPlan(md.selected.id).then((d) => {
              setDetail(d);
              setItems(d.items.map((i) => ({
                attrKey: i.attrKey,
                collectMode: i.collectMode,
                sampleRule: i.sampleRule,
                intervalMinutes: i.intervalMinutes ?? undefined,
                required: i.required,
                target: i.target ?? undefined,
                lsl: i.lsl ?? undefined,
                usl: i.usl ?? undefined,
                oocAction: i.oocAction,
                sortNo: i.sortNo,
              })));
            }).catch(() => {});
          }
          md.res.refresh();
        }}
      />
    </Page>
  );
}
