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
import type { ViewModel, ViewModelColPayload, QueryColumnMeta } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 视图模型（ViewModelPage）——「UI 渲染契约」主从管理页（V39）。
 *
 * <p>头表绑定 query_code；列明细就地编辑，col_key 下拉来自所属查询的
 * dry-run 结果集元数据（保存时后端同口径硬校验）。列显隐/顺序/标题/宽度/
 * 格式化全部在线可改——「调整列」类需求零发版。</p>
 */

interface FormState {
  viewCode: string;
  viewName: string;
  queryCode: string;
  permCode: string;
  allowExport: boolean;
  pageSize: string;
  enabled: boolean;
}

function formFrom(row: ViewModel | null): FormState {
  return {
    viewCode: row?.viewCode ?? '',
    viewName: row?.viewName ?? '',
    queryCode: row?.queryCode ?? '',
    permCode: row?.permCode ?? '',
    allowExport: row?.allowExport ?? false,
    pageSize: row?.pageSize != null ? String(row.pageSize) : '20',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

interface ColRow extends ViewModelColPayload {
  uid: string;
}

export default function ViewModelPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const formatDict = useDict('mds.dq_format_type');

  const [form, setForm] = useState<FormState>(() => formFrom(null));
  const [cols, setCols] = useState<ColRow[]>([]);
  const [queryCols, setQueryCols] = useState<QueryColumnMeta[]>([]);
  const [queryDefIds, setQueryDefIds] = useState<Record<string, string>>({});
  const initialJsonRef = useRef(EMPTY_JSON);
  const initialColsRef = useRef('[]');
  const modeRef = useRef<DetailMode>('view');
  const uidRef = useRef(0);
  const nextUid = () => `c${++uidRef.current}`;

  const md = useMasterDetail<ViewModel>({
    fetchList: (signal) => api.listViewModels(undefined, signal),
    remove: (id) => api.deleteViewModel(id),
    lockType: 'viewModel',
    filter: (r, kw) =>
      r.viewCode.toLowerCase().includes(kw) || r.viewName.toLowerCase().includes(kw),
    isDirty: () =>
      modeRef.current !== 'view' &&
      (JSON.stringify(form) !== initialJsonRef.current ||
        JSON.stringify(cols) !== initialColsRef.current),
  });
  modeRef.current = md.mode;

  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
  }, [md.mode, md.selected]);

  // 查询编码 → 查询 id 映射（colKey 下拉数据源用）
  useEffect(() => {
    api.listQueryDefs().then((defs) => {
      const map: Record<string, string> = {};
      defs.forEach((d) => {
        map[d.queryCode] = d.id;
      });
      setQueryDefIds(map);
    }).catch(() => setQueryDefIds({}));
  }, []);

  useEffect(() => {
    const pull = () => {
      if (!md.selected) return;
      const mapped = md.selected.cols.map((c) => ({
        colKey: c.colKey,
        colTitle: c.colTitle,
        titleKey: c.titleKey,
        width: c.width,
        align: (c.align as ViewModelColPayload['align']) ?? 'LEFT',
        visible: c.visible,
        formatType: (c.formatType as ViewModelColPayload['formatType']) ?? 'NONE',
        formatArg: c.formatArg,
        sortNo: c.sortNo,
        uid: nextUid(),
      }));
      setCols(mapped);
      if (md.mode !== 'view') initialColsRef.current = JSON.stringify(mapped);
    };
    if ((md.mode === 'view' || md.mode === 'edit') && md.selected) {
      pull();
      const qid = queryDefIds[md.selected.queryCode];
      if (qid) {
        api.queryDefColumns(qid).then(setQueryCols).catch(() => setQueryCols([]));
      } else {
        setQueryCols([]);
      }
    } else if (md.mode === 'create') {
      const seed: ColRow[] = [];
      setCols(seed);
      initialColsRef.current = JSON.stringify(seed);
      setQueryCols([]);
    } else {
      setCols([]);
      setQueryCols([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected, queryDefIds]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: ViewModel) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const patchCol = (uid: string, patch: Partial<ViewModelColPayload>) => {
    setCols((list) => list.map((it) => (it.uid === uid ? { ...it, ...patch } : it)));
  };

  const save = async () => {
    md.setErr('');
    if (!form.viewName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        viewCode: form.viewCode.trim(),
        viewName: form.viewName.trim(),
        queryCode: form.queryCode.trim(),
        permCode: form.permCode || null,
        allowExport: form.allowExport,
        pageSize: Number(form.pageSize) || 20,
        enabled: form.enabled,
        cols: cols.map((c, i) => ({
          colKey: c.colKey,
          colTitle: c.colTitle || null,
          titleKey: c.titleKey || null,
          width: c.width ?? null,
          align: c.align ?? 'LEFT',
          visible: c.visible ?? true,
          formatType: c.formatType,
          formatArg: c.formatArg || null,
          sortNo: (i + 1) * 10,
        })),
      };
      const ret =
        md.mode === 'create'
          ? await api.createViewModel(payload)
          : await api.updateViewModel(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const listCols = useMemo<Array<DataTableColumn<ViewModel>>>(
    () => [
      { key: 'viewCode', titleKey: 'mds.dq.viewCode', width: 170, mono: true },
      { key: 'viewName', titleKey: 'mds.dq.viewName', width: 200, ellipsis: true, render: (r) => <span className="flow-name-main">{r.viewName}</span> },
      { key: 'queryCode', titleKey: 'mds.dq.queryCode', width: 170, mono: true },
      { key: 'colCount', titleKey: 'mds.inspection.itemCount', width: 90, render: (r) => r.cols.length },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? 'flow-status flow-status-active' : 'flow-status'}>
            {t(r.enabled ? 'mds.topo.enabled' : 'mds.topo.disabled')}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const headerActions =
    md.mode === 'view' && md.selected ? (
      <Can perm={MDS_PERMS.viewModel.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
    ) : null;

  const fields = useMemo<Array<FormField<FormState>>>(
    () => [
      { kind: 'text', key: 'viewCode', labelKey: 'mds.dq.viewCode', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'viewName', labelKey: 'mds.dq.viewName', required: true },
      { kind: 'text', key: 'queryCode', labelKey: 'mds.dq.queryCode', mono: true, required: true },
      { kind: 'text', key: 'permCode', labelKey: 'mds.dq.permCode', mono: true },
      { kind: 'number', key: 'pageSize', labelKey: 'mds.dq.pageSize', min: 1, max: 200 },
      { kind: 'switch', key: 'allowExport', labelKey: 'mds.dq.allowExport' },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode],
  );

  const readOnly = md.mode === 'view';
  const selected = md.selected;

  const colCols = useMemo<Array<DataTableColumn<ColRow>>>(
    () => [
      {
        key: 'colKey',
        titleKey: 'mds.dq.colKey',
        width: 180,
        render: (r) =>
          readOnly ? (
            r.colKey
          ) : (
            <select
              style={{ width: '100%' }}
              value={r.colKey}
              onChange={(e) => patchCol(r.uid, { colKey: e.target.value })}
            >
              <option value="">—</option>
              {queryCols.map((c) => (
                <option key={c.key} value={c.key}>{c.key}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'colTitle',
        titleKey: 'mds.dq.colTitle',
        width: 150,
        render: (r) => (
          <input
            style={{ width: '100%' }}
            value={r.colTitle ?? ''}
            readOnly={readOnly}
            onChange={(e) => patchCol(r.uid, { colTitle: e.target.value })}
          />
        ),
      },
      {
        key: 'width',
        titleKey: 'mds.dq.width',
        width: 90,
        render: (r) => (
          <input
            style={{ width: '100%' }}
            type="number"
            min="40"
            value={r.width ?? ''}
            readOnly={readOnly}
            onChange={(e) => patchCol(r.uid, { width: e.target.value === '' ? null : Number(e.target.value) })}
          />
        ),
      },
      {
        key: 'align',
        titleKey: 'mds.dq.align',
        width: 100,
        render: (r) =>
          readOnly ? (
            r.align ?? 'LEFT'
          ) : (
            <select
              style={{ width: '100%' }}
              value={r.align ?? 'LEFT'}
              onChange={(e) => patchCol(r.uid, { align: e.target.value as ViewModelColPayload['align'] })}
            >
              <option value="LEFT">LEFT</option>
              <option value="CENTER">CENTER</option>
              <option value="RIGHT">RIGHT</option>
            </select>
          ),
      },
      {
        key: 'visible',
        titleKey: 'mds.dq.visible',
        width: 70,
        render: (r) => (
          <input
            type="checkbox"
            checked={r.visible ?? true}
            disabled={readOnly}
            onChange={(e) => patchCol(r.uid, { visible: e.target.checked })}
          />
        ),
      },
      {
        key: 'formatType',
        titleKey: 'mds.dq.formatType',
        width: 120,
        render: (r) =>
          readOnly ? (
            r.formatType ?? 'NONE'
          ) : (
            <select
              style={{ width: '100%' }}
              value={r.formatType}
              onChange={(e) => patchCol(r.uid, { formatType: e.target.value as ViewModelColPayload['formatType'] })}
            >
              {formatDict.items.filter((i) => !i.disabled).map((i) => (
                <option key={i.value} value={i.value}>{i.label}</option>
              ))}
            </select>
          ),
      },
      {
        key: 'formatArg',
        titleKey: 'mds.dq.formatArg',
        width: 180,
        render: (r) => (
          <input
            style={{ width: '100%' }}
            value={r.formatArg ?? ''}
            readOnly={readOnly}
            onChange={(e) => patchCol(r.uid, { formatArg: e.target.value })}
          />
        ),
      },
      {
        key: 'ops',
        titleKey: 'mds.common.actions',
        width: 76,
        render: (r) =>
          !readOnly && (
            <CimButton size="small" danger onClick={() => setCols((list) => list.filter((it) => it.uid !== r.uid))}>
              {t('mds.common.delete')}
            </CimButton>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [readOnly, queryCols, formatDict.items, t],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<ViewModel>
          titleKey="mds.dq.viewListTitle"
          columns={listCols}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.dq.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.viewModel.create}>
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
                  <h3 className="op-detail-title">{t('mds.dq.viewListTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{selected!.viewCode}</h3>
                    <span className={selected!.enabled ? 'flow-status flow-status-active' : 'flow-status'}>
                      {t(selected!.enabled ? 'mds.topo.enabled' : 'mds.topo.disabled')}
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
                disabled={readOnly}
                onChange={(patch: Record<string, unknown>) => setForm((f) => ({ ...f, ...(patch as Partial<FormState>) }))}
                columns={2}
              />

              <h4 className="op-detail-title" style={{ marginTop: 16 }}>
                {t('mds.dq.colsTitle')}
              </h4>
              <DataTable<ColRow>
                columns={colCols}
                rows={cols}
                rowKey={(r) => r.uid}
                size="small"
                flat
                actions={
                  !readOnly && (
                    <CimButton
                      size="small"
                      onClick={() => setCols((list) => [...list, { colKey: '', formatType: 'NONE', uid: nextUid() }])}
                    >
                      {t('mds.dq.colAdd')}
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
