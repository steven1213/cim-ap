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
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { OperationVarParam, OperationView } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 工序库（OperationsPage，V20 / 动线改版）——P-T-R-O 四层建模的 O 层管理页。
 *
 * <p>布局为「左列表 + 右详情」主从结构，按用户操作动线组织：</p>
 * <ul>
 *   <li>高频动线（搜索 → 点选 → 就地编辑 → 保存 → 生命周期动作）全程零弹窗；
 *       编辑在右侧详情面板内完成，不丢视线焦点。</li>
 *   <li>中频动作（新增/批量删除）在列表工具栏；复制新版为内联输入条（不弹窗）。</li>
 *   <li>对象级生命周期按钮贴详情头，且按状态动态出现（UNFROZEN→编辑/冻结/删除；
 *       FROZEN→解冻/发布/禁用/复制新版；ACTIVE→禁用/复制新版；DISABLED→删除），
 *       只出现当前合法的动作。</li>
 *   <li>批量删除经列表勾选（仅 UNFROZEN/DISABLED 可勾），逐条删除并汇报成败。</li>
 * </ul>
 *
 * <p>Operation = 独立主数据，生命周期四态：UNFROZEN → FROZEN → ACTIVE（生效，同族
 * 唯一，只有 ACTIVE 可被路线引用）→ DISABLED；改冻结/生效版本 = 复制同族新版本。
 * 被路线步骤引用的工序禁删禁禁用（后端 2004 守门）。12 寸预留字段表单不暴露。</p>
 */

type DetailMode = 'view' | 'edit' | 'create' | 'copy';

/** 可变参数编辑行（视图模型；保存时映射为 OperationVarParamPayload）。 */
interface VarParamRow {
  paramKey: string;
  defaultValue: string;
  minValue: string;
  maxValue: string;
  overrideLevel: string;
}

interface OpFormState {
  opCode: string;
  opName: string;
  opType: OperationView['opType'];
  techCode: string;
  recipeRef: string;
  equipType: string;
  capabilityCode: string;
  edcPlanCode: string;
  qtimeMin: string;
  qtimeMax: string;
  reworkLimit: string;
  holdPoint: boolean;
  holdReason: string;
  batchMode: string;
  batchSize: string;
  inspectPlan: string;
  samplingRate: string;
  remark: string;
  /** 可变参数（V29；进编辑态时从后端拉入，随主表单一起保存）。 */
  varParams: VarParamRow[];
}

const emptyVarRow = (): VarParamRow => ({
  paramKey: '', defaultValue: '', minValue: '', maxValue: '', overrideLevel: 'NONE',
});

function varRowsFrom(rows: OperationVarParam[] | undefined | null): VarParamRow[] {
  return (rows ?? []).map((r) => ({
    paramKey: r.paramKey,
    defaultValue: r.defaultValue ?? '',
    minValue: r.minValue ?? '',
    maxValue: r.maxValue ?? '',
    overrideLevel: r.overrideLevel ?? 'NONE',
  }));
}

function formFrom(row: OperationView | null): OpFormState {
  return {
    opCode: row?.opCode ?? '',
    opName: row?.opName ?? '',
    opType: (row?.opType ?? 'PROCESS') as OperationView['opType'],
    techCode: row?.techCode ?? '',
    recipeRef: row?.recipeRef ?? '',
    equipType: row?.equipType ?? '',
    capabilityCode: row?.capabilityCode ?? '',
    edcPlanCode: row?.edcPlanCode ?? '',
    qtimeMin: row?.qtimeMin != null ? String(row.qtimeMin) : '',
    qtimeMax: row?.qtimeMax != null ? String(row.qtimeMax) : '',
    reworkLimit: row?.reworkLimit != null ? String(row.reworkLimit) : '',
    holdPoint: Boolean(row?.holdPoint),
    holdReason: row?.holdReason ?? '',
    batchMode: row?.batchMode ?? '',
    batchSize: row?.batchSize != null ? String(row.batchSize) : '',
    inspectPlan: row?.inspectPlan ?? '',
    samplingRate: row?.samplingRate != null ? String(row.samplingRate) : '',
    remark: row?.remark ?? '',
    varParams: [],
  };
}

export default function OperationsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [keyword, setKeyword] = useState('');
  const opsRes = useResource(({ signal }) => api.listOperations(keyword || undefined, signal), {
    deps: [keyword],
  });
  const ops = opsRes.data ?? [];

  // 左列表：选中 + 勾选（批量删除）
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<string[]>([]);

  // 右详情：view | edit | create | copy 四态
  const [mode, setMode] = useState<DetailMode>('view');
  const [form, setForm] = useState<OpFormState>(() => formFrom(null));
  const initialRef = useRef('');
  const [copyCode, setCopyCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 下拉数据源（详情表单用；页面级加载一次）
  const techsRes = useResource(({ signal }) => api.listTechnologies(undefined, signal));
  const equipClassRes = useResource(({ signal }) => api.listEquipmentClasses(undefined, signal));
  const capsRes = useResource(({ signal }) => api.listCapabilities(undefined, signal));
  // 可变参数候选键（参数定义字典，仅启用项）
  const paramDefsRes = useResource(({ signal }) => api.listParamDefs(signal));

  const selected = useMemo(() => ops.find((r) => r.id === selectedId) ?? null, [ops, selectedId]);
  const dirty = mode === 'edit' || mode === 'create' ? JSON.stringify(form) !== initialRef.current : false;

  // 查看态可变参数（任意状态可读；编辑态走 form.varParams）
  const varParamsRes = useResource(
    ({ signal }) =>
      mode === 'view' && selected
        ? api.listOperationVarParams(selected.id, signal)
        : Promise.resolve([] as OperationVarParam[]),
    { deps: [mode, selected?.id] },
  );

  // 查看态表单跟随选中行（防止切行后详情残留上一行的表单值）
  useEffect(() => {
    if (mode === 'view') setForm(formFrom(selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, selected]);

  const set = (patch: Partial<OpFormState>) => setForm((f) => ({ ...f, ...patch }));
  const numOrNull = (v: string) => (v.trim() === '' ? null : Number(v));

  // ---- 可变参数行编辑（edit 态） ----
  const setVarRow = (idx: number, patch: Partial<VarParamRow>) =>
    setForm((f) => ({
      ...f,
      varParams: f.varParams.map((r, i) => (i === idx ? { ...r, ...patch } : r)),
    }));
  const addVarRow = () =>
    setForm((f) => ({ ...f, varParams: [...f.varParams, emptyVarRow()] }));
  const removeVarRow = (idx: number) =>
    setForm((f) => ({ ...f, varParams: f.varParams.filter((_, i) => i !== idx) }));

  // ---- 模式切换 ----
  const enterCreate = () => {
    setForm(formFrom(null));
    initialRef.current = JSON.stringify(form);
    setErr('');
    setMode('create');
  };
  const enterEdit = async (row: OperationView) => {
    setErr('');
    setMode('edit');
    const base = formFrom(row);
    try {
      base.varParams = varRowsFrom(await api.listOperationVarParams(row.id));
    } catch {
      base.varParams = []; // 拉取失败不阻塞编辑；保存时后端守门兜底
    }
    setForm(base);
    initialRef.current = JSON.stringify(base);
  };
  const enterCopy = () => {
    setCopyCode('');
    setErr('');
    setMode('copy');
  };
  const backToView = () => {
    setErr('');
    setMode('view');
  };

  const onRowClick = async (row: OperationView) => {
    if (row.id === selectedId && mode === 'view') return;
    if (dirty) {
      const ok = await confirm({
        titleKey: 'mds.common.discardTitle',
        contentKey: 'mds.common.discardText',
        okKey: 'mds.common.ok',
      });
      if (!ok) return;
    }
    setSelectedId(row.id);
    setMode('view');
    setErr('');
  };

  // ---- 保存 ----
  const save = async () => {
    setErr('');
    if (!form.opName.trim()) {
      setErr(t('mds.operation.errNameRequired'));
      return;
    }
    // 可变参数预校验（与后端 saveVarParams 守门同口径）
    const blankRow = form.varParams.find((r) => !r.paramKey.trim());
    if (blankRow) {
      setErr(t('mds.operation.errVarParamKey'));
      return;
    }
    const keys = form.varParams.map((r) => r.paramKey.trim());
    const dupKey = keys.find((k, i) => keys.indexOf(k) !== i);
    if (dupKey) {
      setErr(t('mds.operation.errVarParamDup', { key: dupKey }));
      return;
    }
    setBusy(true);
    try {
      const payload = {
        opCode: form.opCode.trim() || null,
        opName: form.opName.trim(),
        opType: form.opType,
        techCode: form.techCode || null,
        recipeRef: form.recipeRef || null,
        equipType: form.equipType || null,
        capabilityCode: form.capabilityCode || null,
        edcPlanCode: form.edcPlanCode || null,
        qtimeMin: numOrNull(form.qtimeMin),
        qtimeMax: numOrNull(form.qtimeMax),
        reworkLimit: numOrNull(form.reworkLimit),
        holdPoint: form.holdPoint,
        holdReason: form.holdReason || null,
        batchMode: form.batchMode || null,
        batchSize: numOrNull(form.batchSize),
        inspectPlan: form.inspectPlan || null,
        samplingRate: numOrNull(form.samplingRate),
        remark: form.remark || null,
      };
      const ret =
        mode === 'create'
          ? await api.createOperation(payload)
          : await api.updateOperation(selected!.id, payload);
      // 可变参数随主表单联保（仅编辑态；整单替换语义）
      if (mode === 'edit') {
        await api.saveOperationVarParams(
          selected!.id,
          form.varParams.map((r, i) => ({
            paramKey: r.paramKey.trim(),
            defaultValue: r.defaultValue || null,
            minValue: r.minValue || null,
            maxValue: r.maxValue || null,
            overrideLevel: r.overrideLevel || 'NONE',
            sortNo: i,
          })),
        );
      }
      toast.success(t('mds.common.saved'));
      opsRes.refresh();
      setSelectedId(ret.id);
      backToView();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const submitCopy = async () => {
    if (!selected) return;
    if (!copyCode.trim()) {
      setErr(t('mds.operation.errCodeRequired'));
      return;
    }
    setBusy(true);
    try {
      const ret = await api.copyOperation(selected.id, copyCode.trim());
      toast.success(t('mds.common.saved'));
      opsRes.refresh();
      setSelectedId(ret.id);
      setCopyCode('');
      backToView();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  // ---- 生命周期 / 删除 ----
  const doAction = async (row: OperationView, action: 'freeze' | 'unfreeze' | 'activate' | 'disable') => {
    const ok = await confirm({
      titleKey: `mds.operation.${action}Confirm`,
      contentKey: 'mds.operation.actionConfirmText',
      okKey: 'mds.common.ok',
    });
    if (!ok) return;
    try {
      if (action === 'freeze') await api.freezeOperation(row.id);
      else if (action === 'unfreeze') await api.unfreezeOperation(row.id);
      else if (action === 'activate') await api.activateOperation(row.id);
      else await api.disableOperation(row.id);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    opsRes.refresh();
  };

  const doDelete = async (row: OperationView) => {
    const ok = await confirm({
      titleKey: 'mds.operation.deleteConfirm',
      contentKey: 'mds.operation.deleteConfirmText',
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteOperation(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    if (row.id === selectedId) setSelectedId(null);
    setCheckedKeys((ks) => ks.filter((k) => k !== row.id));
    opsRes.refresh();
  };

  const doBatchDelete = async () => {
    if (!checkedKeys.length) return;
    const ok = await confirm({
      titleKey: 'mds.operation.batchDeleteConfirm',
      content: t('mds.operation.batchDeleteConfirmText', { n: checkedKeys.length }),
      okKey: 'mds.common.delete',
      danger: true,
    });
    if (!ok) return;
    let okCount = 0;
    let failCount = 0;
    for (const id of checkedKeys) {
      try {
        await api.deleteOperation(id);
        okCount += 1;
      } catch {
        failCount += 1;
      }
    }
    if (failCount === 0) toast.success(t('mds.common.deleted'));
    else toast.error(t('mds.common.batchResult', { ok: okCount, fail: failCount }));
    if (selectedId && checkedKeys.includes(selectedId)) setSelectedId(null);
    setCheckedKeys([]);
    opsRes.refresh();
  };

  // ---- 左列表列（瘦身：全字段在右侧详情） ----
  const columns = useMemo<Array<DataTableColumn<OperationView>>>(
    () => [
    {
      key: 'opCode',
      titleKey: 'mds.operation.field.opCode',
      width: 160,
      mono: true,
      render: (r) => (
        <span>
          {r.opCode}
          <span className="flow-name-sub"> V{r.versionNo ?? 1}</span>
        </span>
      ),
    },
    { key: 'opName', titleKey: 'mds.operation.field.opName', width: 150, ellipsis: true, render: (r) => <span className="flow-name-main">{r.opName}</span> },
    {
      key: 'opType',
      titleKey: 'mds.operation.field.opType',
      width: 80,
      render: (r) => t(`mds.operation.type.${r.opType}`),
    },
    {
      key: 'status',
      titleKey: 'mds.operation.field.status',
      width: 84,
      render: (r) => (
        <span className={`flow-status flow-status-${r.status.toLowerCase()}`}>
          {t(`mds.operation.status.${r.status}`)}
        </span>
      ),
    },
    { key: 'capabilityCode', titleKey: 'mds.operation.field.capabilityCode', width: 110, mono: true },
    {
      key: 'referenced',
      titleKey: 'mds.operation.field.referenced',
      width: 80,
      render: (r) =>
        r.referenced ? <span className="op-ref-yes">{t('mds.operation.referencedYes')}</span> : <span className="dim">—</span>,
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [t]);

  const checkedDeletable = checkedKeys.length > 0;

  // ---- 详情头动作（对象级，按状态动态出现） ----
  const headerActions = mode === 'view' && selected ? (
    <>
      {selected.status === 'UNFROZEN' && (
        <Can perm={MDS_PERMS.operation.update}>
          <CimButton type="primary" size="small" onClick={() => void enterEdit(selected)}>
            {t('mds.common.edit')}
          </CimButton>
        </Can>
      )}
      {selected.status === 'UNFROZEN' && (
        <Can perm={MDS_PERMS.operation.update}>
          <CimButton size="small" onClick={() => void doAction(selected, 'freeze')}>
            {t('mds.operation.freeze')}
          </CimButton>
        </Can>
      )}
      {selected.status === 'FROZEN' && (
        <Can perm={MDS_PERMS.operation.update}>
          <CimButton size="small" onClick={() => void doAction(selected, 'unfreeze')}>
            {t('mds.operation.unfreeze')}
          </CimButton>
        </Can>
      )}
      {selected.status === 'FROZEN' && (
        <Can perm={MDS_PERMS.operation.update}>
          <CimButton type="primary" size="small" onClick={() => void doAction(selected, 'activate')}>
            {t('mds.operation.activate')}
          </CimButton>
        </Can>
      )}
      {(selected.status === 'FROZEN' || selected.status === 'ACTIVE') && (
        <Can perm={MDS_PERMS.operation.update}>
          <CimButton size="small" danger onClick={() => void doAction(selected, 'disable')}>
            {t('mds.operation.disable')}
          </CimButton>
        </Can>
      )}
      {(selected.status === 'FROZEN' || selected.status === 'ACTIVE') && (
        <Can perm={MDS_PERMS.operation.create}>
          <CimButton size="small" onClick={enterCopy}>
            {t('mds.operation.copyNew')}
          </CimButton>
        </Can>
      )}
      {(selected.status === 'UNFROZEN' || selected.status === 'DISABLED') && (
        <Can perm={MDS_PERMS.operation.delete}>
          <CimButton size="small" danger onClick={() => void doDelete(selected)}>
            {t('mds.common.delete')}
          </CimButton>
        </Can>
      )}
    </>
  ) : null;

  const fields = useMemo<Array<FormField<OpFormState>>>(
    () => {
    const locked = mode === 'edit';
    return [
      { kind: 'text', key: 'opCode', labelKey: 'mds.operation.field.opCode', mono: true, disabled: mode !== 'create', required: mode === 'create' },
      { kind: 'text', key: 'opName', labelKey: 'mds.operation.field.opName', required: true },
      {
        kind: 'select',
        key: 'opType',
        labelKey: 'mds.operation.field.opType',
        disabled: locked,
        options: [
          { value: 'PROCESS', label: t('mds.operation.type.PROCESS') },
          { value: 'INSPECT', label: t('mds.operation.type.INSPECT') },
          { value: 'REWORK', label: t('mds.operation.type.REWORK') },
        ],
      },
      {
        kind: 'select',
        key: 'techCode',
        labelKey: 'mds.operation.field.techCode',
        allowClear: true,
        options: (techsRes.data ?? []).map((tech) => ({ value: tech.techCode, label: tech.techName ?? tech.techCode })),
      },
      { kind: 'text', key: 'recipeRef', labelKey: 'mds.operation.field.recipeRef', mono: true },
      {
        kind: 'select',
        key: 'equipType',
        labelKey: 'mds.operation.field.equipType',
        allowClear: true,
        options: (equipClassRes.data ?? []).map((eq) => ({ value: eq.classCode, label: eq.className ?? eq.classCode })),
      },
      {
        kind: 'select',
        key: 'capabilityCode',
        labelKey: 'mds.operation.field.capabilityCode',
        allowClear: true,
        options: (capsRes.data ?? []).map((cap) => ({
          value: cap.capabilityCode,
          label: cap.capabilityName ?? cap.capabilityCode,
        })),
      },
      { kind: 'text', key: 'edcPlanCode', labelKey: 'mds.operation.field.edcPlanCode', mono: true },
      { kind: 'number', key: 'qtimeMin', labelKey: 'mds.operation.field.qtimeMin' },
      { kind: 'number', key: 'qtimeMax', labelKey: 'mds.operation.field.qtimeMax' },
      { kind: 'number', key: 'reworkLimit', labelKey: 'mds.operation.field.reworkLimit' },
      { kind: 'switch', key: 'holdPoint', labelKey: 'mds.operation.field.holdPoint' },
      ...(form.holdPoint ? [{ kind: 'text' as const, key: 'holdReason' as const, labelKey: 'mds.operation.field.holdReason' }] : []),
      {
        kind: 'select',
        key: 'batchMode',
        labelKey: 'mds.operation.field.batchMode',
        allowClear: true,
        options: [
          { value: 'SERIAL', label: t('mds.operation.batch.SERIAL') },
          { value: 'PARALLEL', label: t('mds.operation.batch.PARALLEL') },
          { value: 'BATCH', label: t('mds.operation.batch.BATCH') },
        ],
      },
      { kind: 'number', key: 'batchSize', labelKey: 'mds.operation.field.batchSize' },
      ...(form.opType === 'INSPECT'
        ? [
            { kind: 'text' as const, key: 'inspectPlan' as const, labelKey: 'mds.operation.field.inspectPlan', mono: true },
            { kind: 'number' as const, key: 'samplingRate' as const, labelKey: 'mds.operation.field.samplingRate' },
          ]
        : []),
      { kind: 'textarea', key: 'remark', labelKey: 'mds.operation.field.remark' },
    ];
  }, [t, mode, form.holdPoint, form.opType, techsRes.data, equipClassRes.data, capsRes.data]);

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<OperationView>
          titleKey="mds.operation.listTitle"
          columns={columns}
          rows={ops}
          rowKey={(r) => r.id}
          loading={opsRes.loading}
          error={opsRes.error?.message ?? null}
          onRetry={opsRes.refresh}
          search={{ value: keyword, onChange: setKeyword, placeholderKey: 'mds.operation.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.operation.create}>
              <CimButton type="primary" size="small" onClick={enterCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          actions={
            <Can perm={MDS_PERMS.operation.delete}>
              <CimButton
                size="small"
                danger
                disabled={!checkedDeletable}
                onClick={() => void doBatchDelete()}
              >
                {t('mds.common.batchDelete')}
                {checkedDeletable ? ` (${checkedKeys.length})` : ''}
              </CimButton>
            </Can>
          }
          onRefresh={opsRes.refresh}
          selectedKeys={checkedKeys}
          onSelectionChange={setCheckedKeys}
          selectable={(r) => r.status === 'UNFROZEN' || r.status === 'DISABLED'}
          onRowClick={(r) => void onRowClick(r)}
          rowClassName={(r) => (r.id === selectedId ? 'row-selected' : '')}
          emptyTitleKey="mds.operation.empty"
        />

        <Panel className="op-detail">
          {!selected && mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.operation.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">
                      {selected!.opCode}
                      <span className="flow-name-sub"> V{selected!.versionNo ?? 1}</span>
                    </h3>
                    <span className={`flow-status flow-status-${selected!.status.toLowerCase()}`}>
                      {t(`mds.operation.status.${selected!.status}`)}
                    </span>
                    {selected!.referenced && (
                      <span className="op-ref-yes">{t('mds.operation.referencedYes')}</span>
                    )}
                  </>
                )}
                {mode === 'edit' && <span className="op-editing-badge">{t('mds.common.editing')}</span>}
                <div className="op-detail-actions">
                  {mode === 'view' && headerActions}
                  {(mode === 'edit' || mode === 'create') && (
                    <>
                      <CimButton type="primary" size="small" disabled={busy} onClick={() => void save()}>
                        {t('mds.common.save')}
                      </CimButton>
                      <CimButton size="small" onClick={backToView}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </>
                  )}
                  {mode === 'copy' && (
                    <>
                      <CimButton type="primary" size="small" disabled={busy} onClick={() => void submitCopy()}>
                        {t('mds.common.ok')}
                      </CimButton>
                      <CimButton size="small" onClick={backToView}>
                        {t('mds.common.cancel')}
                      </CimButton>
                    </>
                  )}
                </div>
              </div>

              {mode === 'copy' && selected && (
                <div className="op-copy-bar">
                  <span className="dim">{t('mds.operation.copyFrom', { code: selected.opCode, version: selected.versionNo ?? 1 })}</span>
                  <input
                    className="op-search"
                    placeholder={t('mds.operation.field.newOpCode')}
                    value={copyCode}
                    onChange={(e) => setCopyCode(e.target.value)}
                  />
                </div>
              )}

              {err && <p className="op-error">{err}</p>}

              {mode === 'copy' ? (
                <p className="dim">{t('mds.operation.copyHint')}</p>
              ) : (
                <>
                  <FormGrid
                    fields={fields}
                    value={form}
                    disabled={mode === 'view'}
                    onChange={(patch: Record<string, unknown>) => set(patch as Partial<OpFormState>)}
                    columns={2}
                  />

                  {/* —— 可变参数（V29：Wafer/Lot 级覆盖声明；create 先建后维护）—— */}
                  <h4 style={{ marginTop: 16 }}>{t('mds.operation.varParamTitle')}</h4>
                  {mode === 'create' ? (
                    <p className="dim">{t('mds.operation.varParam.createHint')}</p>
                  ) : (
                    <>
                      <p className="dim">{t('mds.operation.varParamHint')}</p>
                      <table className="icm-table">
                        <thead>
                          <tr>
                            <th>{t('mds.operation.field.paramKey')}</th>
                            <th>{t('mds.operation.field.paramDefault')}</th>
                            <th>{t('mds.operation.field.paramMin')}</th>
                            <th>{t('mds.operation.field.paramMax')}</th>
                            <th>{t('mds.operation.field.overrideLevel')}</th>
                            {mode === 'edit' && <th />}
                          </tr>
                        </thead>
                        <tbody>
                          {mode === 'edit'
                            ? form.varParams.map((r, idx) => (
                                <tr key={idx}>
                                  <td>
                                    <select
                                      className="op-search"
                                      style={{ width: 180 }}
                                      value={r.paramKey}
                                      onChange={(e) => setVarRow(idx, { paramKey: e.target.value })}
                                    >
                                      <option value="">{t('mds.recipeResolve.selectParamHint')}</option>
                                      {(paramDefsRes.data ?? [])
                                        .filter((d) => d.enabled)
                                        .map((d) => (
                                          <option key={d.attrKey} value={d.attrKey}>{d.attrKey}</option>
                                        ))}
                                    </select>
                                  </td>
                                  <td>
                                    <input className="op-search" style={{ width: 90 }} value={r.defaultValue}
                                      onChange={(e) => setVarRow(idx, { defaultValue: e.target.value })} />
                                  </td>
                                  <td>
                                    <input className="op-search" style={{ width: 80 }} value={r.minValue}
                                      onChange={(e) => setVarRow(idx, { minValue: e.target.value })} />
                                  </td>
                                  <td>
                                    <input className="op-search" style={{ width: 80 }} value={r.maxValue}
                                      onChange={(e) => setVarRow(idx, { maxValue: e.target.value })} />
                                  </td>
                                  <td>
                                    <select
                                      className="op-search"
                                      style={{ width: 110 }}
                                      value={r.overrideLevel}
                                      onChange={(e) => setVarRow(idx, { overrideLevel: e.target.value })}
                                    >
                                      {['NONE', 'LOT', 'WAFER', 'SLOT'].map((lv) => (
                                        <option key={lv} value={lv}>{t(`mds.operation.varLevel.${lv}`)}</option>
                                      ))}
                                    </select>
                                  </td>
                                  <td>
                                    <CimButton size="small" danger onClick={() => removeVarRow(idx)}>
                                      {t('mds.operation.varParam.remove')}
                                    </CimButton>
                                  </td>
                                </tr>
                              ))
                            : varRowsFrom(varParamsRes.data).map((r) => (
                                <tr key={r.paramKey}>
                                  <td className="mono">{r.paramKey}</td>
                                  <td>{r.defaultValue || <span className="dim">—</span>}</td>
                                  <td>{r.minValue || <span className="dim">—</span>}</td>
                                  <td>{r.maxValue || <span className="dim">—</span>}</td>
                                  <td>{t(`mds.operation.varLevel.${r.overrideLevel}`)}</td>
                                </tr>
                              ))}
                          {mode === 'edit' && !form.varParams.length && (
                            <tr><td colSpan={6} className="dim">{t('mds.operation.varParamHint')}</td></tr>
                          )}
                          {mode === 'view' && !varRowsFrom(varParamsRes.data).length && (
                            <tr><td colSpan={5} className="dim">{t('mds.operation.varParamHint')}</td></tr>
                          )}
                        </tbody>
                      </table>
                      {mode === 'edit' && (
                        <div style={{ marginTop: 8 }}>
                          <CimButton size="small" onClick={addVarRow}>
                            {t('mds.operation.varParam.create')}
                          </CimButton>
                        </div>
                      )}
                    </>
                  )}
                </>
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
