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
import type { ConsBom, ConsBomLinePayload } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 消耗品 BOM（ConsBomPage）——「产品×流程×工序×机台」消耗契约（V38）。
 *
 * <p>布局为「左列表 + 右详情」主从结构；详情 = BOM 头表单 + 消耗明细行编辑。
 * 头表绑定上下文（产品/流程/工序必填、机台可选腔体级覆盖）；明细行定义消耗规则
 * （FIXED 固定用量 / VARIABLE 变量消耗：参数键×每单位用量，公式计算归运行时），
 * 页内行增删改、保存一次提交（后端全量替换）。状态机 DRAFT→ACTIVE→SUPERSEDED
 * /DISABLED：仅 DRAFT 可编辑/激活，激活时同上下文旧 ACTIVE 自动画 SUPERSEDED、
 * 版本号取 max+1；工序升版后展示版本漂移告警。</p>
 */

interface BomFormState {
  bomCode: string;
  bomName: string;
  productId: string;
  flowId: string;
  operationId: string;
  equipmentId: string;
  enabled: boolean;
}

function formFrom(row: ConsBom | null): BomFormState {
  return {
    bomCode: row?.bomCode ?? '',
    bomName: row?.bomName ?? '',
    productId: row?.productId ?? '',
    flowId: row?.flowId ?? '',
    operationId: row?.operationId ?? '',
    equipmentId: row?.equipmentId ?? '',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

/** 行编辑视图：payload + 页内临时行键（不提交后端）。 */
type LineRow = ConsBomLinePayload & { uid: string };

const EMPTY_LINE: ConsBomLinePayload = {
  specId: '',
  consumeType: 'FIXED',
  basis: 'PER_LOT',
  stdQty: 1,
  tolerancePct: null,
  lossFactor: null,
  paramKey: '',
  paramQtyPerUnit: null,
  substituteGroup: '',
  subPriority: null,
  expiryPolicy: '',
  sortNo: 0,
  note: '',
};

const LINE_INPUT_STYLE = { width: '100%' } as const;

const STATUS_CLASS: Record<string, string> = {
  DRAFT: 'flow-status',
  ACTIVE: 'flow-status flow-status-active',
  SUPERSEDED: 'flow-status flow-status-disabled',
  DISABLED: 'flow-status flow-status-disabled',
};

function statusLabelKey(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'mds.consBom.statusActive';
    case 'SUPERSEDED':
      return 'mds.consBom.statusSuperseded';
    case 'DISABLED':
      return 'mds.consBom.statusDisabled';
    default:
      return 'mds.consBom.statusDraft';
  }
}

export default function ConsBomPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const typeDict = useDict('mds.cons_consume_type');
  const basisDict = useDict('mds.cons_basis');
  const expiryDict = useDict('mds.cons_expiry_policy');

  const [form, setForm] = useState<BomFormState>(() => formFrom(null));
  const [lines, setLines] = useState<LineRow[]>([]);
  const uidRef = useRef(0);
  const nextUid = () => `l${++uidRef.current}`;
  const initialJsonRef = useRef(EMPTY_JSON);
  const initialLinesRef = useRef('[]');
  const modeRef = useRef<DetailMode>('view');

  const md = useMasterDetail<ConsBom>({
    fetchList: (signal) => api.listConsBoms(undefined, signal),
    remove: (id) => api.deleteConsBom(id),
    lockType: 'consBom',
    filter: (r, kw) =>
      r.bomCode.toLowerCase().includes(kw) || r.bomName.toLowerCase().includes(kw),
    isDirty: () =>
      modeRef.current !== 'view' &&
      (JSON.stringify(form) !== initialJsonRef.current ||
        JSON.stringify(lines) !== initialLinesRef.current),
  });
  modeRef.current = md.mode;

  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  // 明细跟随选中行/模式：查看与编辑拉现行明细，新建给一条空行起步
  useEffect(() => {
    const pull = () => {
      if (!md.selected) return;
      api
        .listConsBomLines(md.selected.id)
        .then((rows) => {
          const mapped = rows.map((r) => ({
            specId: r.specId,
            consumeType: r.consumeType ?? 'FIXED',
            basis: r.basis ?? 'PER_LOT',
            stdQty: r.stdQty,
            tolerancePct: r.tolerancePct,
            lossFactor: r.lossFactor,
            paramKey: r.paramKey ?? '',
            paramQtyPerUnit: r.paramQtyPerUnit,
            substituteGroup: r.substituteGroup ?? '',
            subPriority: r.subPriority,
            expiryPolicy: r.expiryPolicy ?? '',
            sortNo: r.sortNo,
            note: r.note ?? '',
            uid: nextUid(),
          }));
          setLines(mapped);
          if (md.mode !== 'view') initialLinesRef.current = JSON.stringify(mapped);
        })
        .catch(() => setLines([]));
    };
    if ((md.mode === 'view' || md.mode === 'edit') && md.selected) {
      pull();
    } else if (md.mode === 'create') {
      const seed: LineRow[] = [{ ...EMPTY_LINE, sortNo: 10, uid: nextUid() }];
      setLines(seed);
      initialLinesRef.current = JSON.stringify(seed);
    } else {
      setLines([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: ConsBom) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const patchLine = (uid: string, patch: Partial<ConsBomLinePayload>) => {
    setLines((list) => list.map((it) => (it.uid === uid ? { ...it, ...patch } : it)));
  };

  const removeLine = (uid: string) => {
    setLines((list) => list.filter((it) => it.uid !== uid));
  };

  const save = async () => {
    md.setErr('');
    if (!form.bomName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    const bad = lines.findIndex(
      (it) =>
        !it.specId ||
        it.stdQty <= 0 ||
        (it.consumeType === 'VARIABLE' && (!it.paramKey || !it.paramQtyPerUnit || it.paramQtyPerUnit <= 0)),
    );
    if (bad >= 0) {
      md.setErr(lines[bad].specId ? t('mds.consBom.errStdQty') : t('mds.consBom.errSpecRequired'));
      if (lines[bad].consumeType === 'VARIABLE') md.setErr(t('mds.consBom.errParamRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const headerPayload = {
        bomName: form.bomName.trim(),
        productId: form.productId,
        flowId: form.flowId,
        operationId: form.operationId,
        equipmentId: form.equipmentId || null,
        enabled: form.enabled,
      };
      const ret =
        md.mode === 'create'
          ? await api.createConsBom({ bomCode: form.bomCode.trim(), ...headerPayload })
          : await api.updateConsBom(md.selected!.id, headerPayload);
      await api.replaceConsBomLines(
        ret.id,
        lines.map((it, i) => ({
          specId: it.specId,
          consumeType: it.consumeType,
          basis: it.basis,
          stdQty: Number(it.stdQty),
          tolerancePct: it.tolerancePct == null ? null : Number(it.tolerancePct),
          lossFactor: it.lossFactor,
          paramKey: it.paramKey || null,
          paramQtyPerUnit: it.paramQtyPerUnit == null ? null : Number(it.paramQtyPerUnit),
          substituteGroup: it.substituteGroup || null,
          subPriority: it.subPriority,
          expiryPolicy: it.expiryPolicy || null,
          sortNo: (i + 1) * 10,
          note: it.note,
        })),
      );
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const lifecycle = async (row: ConsBom, action: 'activate' | 'disable') => {
    md.setBusy(true);
    try {
      if (action === 'activate') await api.activateConsBom(row.id);
      else await api.disableConsBom(row.id);
      toast.success(t('mds.common.saved'));
      md.res.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<ConsBom>>>(
    () => [
      { key: 'bomCode', titleKey: 'mds.consBom.code', width: 160, mono: true },
      { key: 'bomName', titleKey: 'mds.consBom.name', width: 200, ellipsis: true, render: (r) => <span className="flow-name-main">{r.bomName}</span> },
      { key: 'productCode', titleKey: 'mds.consBom.product', width: 90, render: (r) => r.productCode ?? '—' },
      { key: 'flowCode', titleKey: 'mds.consBom.flow', width: 120, mono: true, render: (r) => r.flowCode ?? '—' },
      { key: 'operationCode', titleKey: 'mds.consBom.operation', width: 120, mono: true, render: (r) => (r.operationCode ? `${r.operationCode}${r.opVersionNo != null ? ` v${r.opVersionNo}` : ''}` : '—') },
      { key: 'equipmentCode', titleKey: 'mds.consBom.equipment', width: 110, render: (r) => r.equipmentCode ?? '—' },
      {
        key: 'status',
        titleKey: 'mds.consBom.status',
        width: 90,
        render: (r) => <span className={STATUS_CLASS[r.status] ?? 'flow-status'}>{t(statusLabelKey(r.status))}</span>,
      },
      { key: 'versionNo', titleKey: 'mds.consBom.versionNo', width: 70, render: (r) => (r.versionNo > 0 ? `V${r.versionNo}` : '—') },
      { key: 'lineCount', titleKey: 'mds.inspection.itemCount', width: 70 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const headerActions = md.mode === 'view' && md.selected ? (
    <>
      {md.selected.status === 'DRAFT' && (
        <Can perm={MDS_PERMS.consBom.update}>
          <CimButton type="primary" size="small" disabled={md.busy} onClick={() => void lifecycle(md.selected!, 'activate')}>
            {t('mds.consBom.activate')}
          </CimButton>
        </Can>
      )}
      {md.selected.status === 'ACTIVE' && (
        <Can perm={MDS_PERMS.consBom.update}>
          <CimButton size="small" disabled={md.busy} onClick={() => void lifecycle(md.selected!, 'disable')}>
            {t('mds.consBom.disable')}
          </CimButton>
        </Can>
      )}
      {md.selected.status === 'DRAFT' && (
        <Can perm={MDS_PERMS.consBom.update}>
          <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
            {t('mds.common.edit')}
          </CimButton>
        </Can>
      )}
      {(md.selected.status === 'DRAFT' || md.selected.status === 'DISABLED') && (
        <Can perm={MDS_PERMS.consBom.delete}>
          <CimButton size="small" danger onClick={() => void md.doDelete(md.selected!)}>
            {t('mds.common.delete')}
          </CimButton>
        </Can>
      )}
    </>
  ) : null;

  // 绑定上下文选项（悬浮加载一次；BOM 页非高频页，直接拉全量）
  const [productOptions, setProductOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [flowOptions, setFlowOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [operationOptions, setOperationOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [equipmentOptions, setEquipmentOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [specOptions, setSpecOptions] = useState<Array<{ value: string; label: string }>>([]);

  useEffect(() => {
    api.listProducts().then((rows) =>
      setProductOptions(rows.map((p) => ({ value: p.id, label: `${p.productCode} ${p.productName}` }))),
    ).catch(() => setProductOptions([]));
    api.listFlows().then((rows) =>
      setFlowOptions(rows.map((f) => ({ value: f.id, label: `${f.flowCode} ${f.flowName}` }))),
    ).catch(() => setFlowOptions([]));
    api.listOperations().then((rows) =>
      setOperationOptions(rows.map((o) => ({ value: o.id, label: `${o.opCode} ${o.opName}` }))),
    ).catch(() => setOperationOptions([]));
    api.listEquipments().then((rows) =>
      setEquipmentOptions(rows.map((e) => ({ value: e.id, label: `${e.equipmentCode} ${e.equipmentName}` }))),
    ).catch(() => setEquipmentOptions([]));
    // 规格下拉只给「可被 BOM 引用」的：ACTIVE 且 CONSUMABLE（后端同款守门）
    api.listConsumables().then((rows) =>
      setSpecOptions(
        rows
          .filter((c) => c.lifecycle === 'ACTIVE' && c.materialKind === 'CONSUMABLE')
          .map((c) => ({ value: c.id, label: `${c.consCode} ${c.consName}` })),
      ),
    ).catch(() => setSpecOptions([]));
  }, []);

  const fields = useMemo<Array<FormField<BomFormState>>>(
    () => [
      { kind: 'text', key: 'bomCode', labelKey: 'mds.consBom.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'bomName', labelKey: 'mds.consBom.name', required: true },
      { kind: 'select', key: 'productId', labelKey: 'mds.consBom.product', required: true, allowClear: true, options: productOptions },
      { kind: 'select', key: 'flowId', labelKey: 'mds.consBom.flow', required: true, allowClear: true, options: flowOptions },
      { kind: 'select', key: 'operationId', labelKey: 'mds.consBom.operation', required: true, allowClear: true, options: operationOptions },
      { kind: 'select', key: 'equipmentId', labelKey: 'mds.consBom.equipment', allowClear: true, options: equipmentOptions },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode, productOptions, flowOptions, operationOptions, equipmentOptions],
  );

  const readOnly = md.mode === 'view';
  const selected = md.selected;
  const opDrift =
    !readOnly && selected && selected.sourceOpVersion != null && selected.opVersionNo != null &&
    selected.sourceOpVersion !== selected.opVersionNo;

  // 明细行列定义：行内控件编辑（select/input），只读态纯文本。
  // 放不下时由 DataTable 内置横向滚动（列宽合计 > 容器自动出滚动条）。
  const lineColumns = useMemo<Array<DataTableColumn<LineRow>>>(
    () => [
      {
        key: 'specId',
        titleKey: 'mds.consBom.lineSpec',
        width: 200,
        render: (r) =>
          readOnly ? (
            specOptions.find((o) => o.value === r.specId)?.label ?? r.specId
          ) : (
            <select style={LINE_INPUT_STYLE} value={r.specId} onChange={(e) => patchLine(r.uid, { specId: e.target.value })}>
              <option value="">—</option>
              {specOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'consumeType',
        titleKey: 'mds.consBom.lineType',
        width: 110,
        render: (r) =>
          readOnly ? (
            r.consumeType ?? '—'
          ) : (
            <select
              style={LINE_INPUT_STYLE}
              value={r.consumeType ?? 'FIXED'}
              onChange={(e) =>
                patchLine(r.uid, {
                  consumeType: e.target.value as 'FIXED' | 'VARIABLE',
                  paramKey: e.target.value === 'FIXED' ? '' : r.paramKey,
                  paramQtyPerUnit: e.target.value === 'FIXED' ? null : r.paramQtyPerUnit,
                })
              }
            >
              {typeDict.items.filter((i) => !i.disabled).map((i) => (
                <option key={i.value} value={i.value}>{i.label}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'basis',
        titleKey: 'mds.consBom.lineBasis',
        width: 110,
        render: (r) =>
          readOnly ? (
            r.basis ?? '—'
          ) : (
            <select style={LINE_INPUT_STYLE} value={r.basis ?? 'PER_LOT'} onChange={(e) => patchLine(r.uid, { basis: e.target.value })}>
              {basisDict.items.filter((i) => !i.disabled).map((i) => (
                <option key={i.value} value={i.value}>{i.label}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'stdQty',
        titleKey: 'mds.consBom.lineStdQty',
        width: 90,
        render: (r) =>
          readOnly ? (
            r.stdQty
          ) : (
            <input
              style={LINE_INPUT_STYLE}
              type="number"
              min="0"
              step="any"
              value={r.stdQty}
              onChange={(e) => patchLine(r.uid, { stdQty: Number(e.target.value) })}
            />
          ),
      },
      {
        key: 'tolerancePct',
        titleKey: 'mds.consBom.lineTolerance',
        width: 90,
        render: (r) =>
          readOnly ? (
            r.tolerancePct != null ? `${r.tolerancePct}%` : '—'
          ) : (
            <input
              style={LINE_INPUT_STYLE}
              type="number"
              min="0"
              max="100"
              step="any"
              value={r.tolerancePct ?? ''}
              onChange={(e) => patchLine(r.uid, { tolerancePct: e.target.value === '' ? null : Number(e.target.value) })}
            />
          ),
      },
      {
        key: 'paramKey',
        titleKey: 'mds.consBom.lineParam',
        width: 130,
        render: (r) =>
          readOnly ? (
            r.paramKey ?? '—'
          ) : r.consumeType === 'VARIABLE' ? (
            <input style={LINE_INPUT_STYLE} value={r.paramKey ?? ''} onChange={(e) => patchLine(r.uid, { paramKey: e.target.value })} />
          ) : (
            '—'
          ),
      },
      {
        key: 'paramQtyPerUnit',
        titleKey: 'mds.consBom.lineParamQty',
        width: 120,
        render: (r) =>
          readOnly ? (
            r.paramQtyPerUnit ?? '—'
          ) : r.consumeType === 'VARIABLE' ? (
            <input
              style={LINE_INPUT_STYLE}
              type="number"
              min="0"
              step="any"
              value={r.paramQtyPerUnit ?? ''}
              onChange={(e) => patchLine(r.uid, { paramQtyPerUnit: e.target.value === '' ? null : Number(e.target.value) })}
            />
          ) : (
            '—'
          ),
      },
      {
        key: 'expiryPolicy',
        titleKey: 'mds.consBom.lineExpiry',
        width: 110,
        render: (r) =>
          readOnly ? (
            r.expiryPolicy ?? '—'
          ) : (
            <select style={LINE_INPUT_STYLE} value={r.expiryPolicy ?? ''} onChange={(e) => patchLine(r.uid, { expiryPolicy: e.target.value })}>
              <option value="">—</option>
              {expiryDict.items.filter((i) => !i.disabled).map((i) => (
                <option key={i.value} value={i.value}>{i.label}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'ops',
        titleKey: 'mds.common.actions',
        width: 76,
        render: (r) =>
          !readOnly && (
            <CimButton size="small" danger onClick={() => removeLine(r.uid)}>
              {t('mds.common.delete')}
            </CimButton>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [readOnly, specOptions, typeDict.items, basisDict.items, expiryDict.items, t],
  );


  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<ConsBom>
          titleKey="mds.consBom.listTitle"
          columns={columns}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.consBom.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.consBom.create}>
              <CimButton type="primary" size="small" onClick={openCreate}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          onRefresh={md.res.refresh}
          onRowClick={(r) => void md.onRowClick(r)}
          rowClassName={(r) => (r.id === md.selectedId ? 'row-selected' : '')}
        />

        <Panel className="op-detail">
          {!selected && md.mode !== 'create' ? (
            <EmptyState titleKey="mds.common.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                {md.mode === 'create' ? (
                  <h3 className="op-detail-title">{t('mds.consBom.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{selected!.bomCode}</h3>
                    <span className={STATUS_CLASS[selected!.status] ?? 'flow-status'}>
                      {t(statusLabelKey(selected!.status))}
                      {selected!.versionNo > 0 ? ` · V${selected!.versionNo}` : ''}
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
              {opDrift && (
                <p className="op-error" style={{ color: 'var(--cim-warn, #b8860b)' }}>
                  {t('mds.consBom.opDrift', { source: selected!.sourceOpVersion, current: selected!.opVersionNo })}
                </p>
              )}

              <FormGrid
                fields={fields}
                value={form}
                disabled={readOnly}
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<BomFormState>) }))}
                columns={2}
              />

              <h4 className="op-detail-title" style={{ marginTop: 16 }}>
                {t('mds.consBom.linesTitle')}
              </h4>
              <DataTable<LineRow>
                columns={lineColumns}
                rows={lines}
                rowKey={(r) => r.uid}
                size="small"
                flat
                actions={
                  !readOnly && (
                    <CimButton
                      size="small"
                      onClick={() =>
                        setLines((list) => [...list, { ...EMPTY_LINE, sortNo: (list.length + 1) * 10, uid: nextUid() }])
                      }
                    >
                      {t('mds.consBom.lineAdd')}
                    </CimButton>
                  )
                }
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
