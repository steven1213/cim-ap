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
import type { QueryDef, QueryParamPayload, QueryColumnMeta } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 查询定义（QueryDefPage）——「取数契约」管理页（V39）。
 *
 * <p>SQL 文本保存前三层验证（语法 SELECT-only / 参数双向一致 / 只读 dry-run），
 * 任一层失败 400 带具体原因；验证通过时间落 validated_at。声明参数全量替换，
 * SQL 中 :param_key 占位必须与参数表完全一致。结果集列元数据（dry-run）供
 * ViewModel 配置列时选择。</p>
 */

interface FormState {
  queryCode: string;
  queryName: string;
  sqlText: string;
  pageSizeMax: string;
  enabled: boolean;
}

function formFrom(row: QueryDef | null): FormState {
  return {
    queryCode: row?.queryCode ?? '',
    queryName: row?.queryName ?? '',
    sqlText: row?.sqlText ?? '',
    pageSizeMax: row?.pageSizeMax != null ? String(row.pageSizeMax) : '500',
    enabled: row?.enabled ?? true,
  };
}

const EMPTY_JSON = JSON.stringify(formFrom(null));

interface ParamRow extends QueryParamPayload {
  uid: string;
}

const EMPTY_PARAM: QueryParamPayload = {
  paramKey: '',
  paramLabel: '',
  paramType: 'STRING',
  required: false,
  dictCode: null,
  sortNo: 0,
};

const STATUS_CLASS = 'flow-status';

export default function QueryDefPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const typeDict = useDict('mds.dq_param_type');

  const [form, setForm] = useState<FormState>(() => formFrom(null));
  const [params, setParams] = useState<ParamRow[]>([]);
  const [columns, setColumns] = useState<QueryColumnMeta[]>([]);
  const initialJsonRef = useRef(EMPTY_JSON);
  const initialParamsRef = useRef('[]');
  const modeRef = useRef<DetailMode>('view');
  const uidRef = useRef(0);
  const nextUid = () => `p${++uidRef.current}`;

  const md = useMasterDetail<QueryDef>({
    fetchList: (signal) => api.listQueryDefs(undefined, signal),
    remove: (id) => api.deleteQueryDef(id),
    lockType: 'queryDef',
    filter: (r, kw) =>
      r.queryCode.toLowerCase().includes(kw) || r.queryName.toLowerCase().includes(kw),
    isDirty: () =>
      modeRef.current !== 'view' &&
      (JSON.stringify(form) !== initialJsonRef.current ||
        JSON.stringify(params) !== initialParamsRef.current),
  });
  modeRef.current = md.mode;

  useEffect(() => {
    if (md.mode === 'view') setForm(formFrom(md.selected));
  }, [md.mode, md.selected]);

  // 参数跟随选中行/模式；编辑态拉列元数据
  useEffect(() => {
    const pull = () => {
      if (!md.selected) return;
      const mapped = md.selected.params.map((p) => ({
        paramKey: p.paramKey,
        paramLabel: p.paramLabel,
        paramType: (p.paramType as QueryParamPayload['paramType']) ?? 'STRING',
        required: p.required,
        dictCode: p.dictCode,
        sortNo: p.sortNo,
        uid: nextUid(),
      }));
      setParams(mapped);
      if (md.mode !== 'view') initialParamsRef.current = JSON.stringify(mapped);
    };
    if ((md.mode === 'view' || md.mode === 'edit') && md.selected) {
      pull();
      api.queryDefColumns(md.selected.id).then(setColumns).catch(() => setColumns([]));
    } else if (md.mode === 'create') {
      const seed: ParamRow[] = [];
      setParams(seed);
      initialParamsRef.current = JSON.stringify(seed);
      setColumns([]);
    } else {
      setParams([]);
      setColumns([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [md.mode, md.selected]);

  const openCreate = () => {
    setForm(formFrom(null));
    initialJsonRef.current = EMPTY_JSON;
    md.enterCreate();
  };

  const openEdit = async (row: QueryDef) => {
    if (!(await md.enterEdit(row))) return;
    setForm(formFrom(row));
    initialJsonRef.current = JSON.stringify(formFrom(row));
  };

  const patchParam = (uid: string, patch: Partial<QueryParamPayload>) => {
    setParams((list) => list.map((it) => (it.uid === uid ? { ...it, ...patch } : it)));
  };

  const save = async () => {
    md.setErr('');
    if (!form.queryName.trim()) {
      md.setErr(t('mds.common.errNameRequired'));
      return;
    }
    if (!form.sqlText.trim()) {
      md.setErr(t('mds.biz.dq.sqlEmpty'));
      return;
    }
    md.setBusy(true);
    try {
      const payload = {
        queryCode: form.queryCode.trim(),
        queryName: form.queryName.trim(),
        sqlText: form.sqlText,
        pageSizeMax: Number(form.pageSizeMax) || 500,
        enabled: form.enabled,
        params: params.map((p) => ({
          paramKey: p.paramKey,
          paramLabel: p.paramLabel,
          paramType: p.paramType,
          required: p.required ?? false,
          dictCode: p.dictCode || null,
          sortNo: 0,
        })),
      };
      const ret =
        md.mode === 'create'
          ? await api.createQueryDef(payload)
          : await api.updateQueryDef(md.selected!.id, payload);
      toast.success(t('mds.common.saved'));
      md.saved(ret.id);
    } catch (e) {
      md.setErr(e instanceof Error ? e.message : String(e));
    } finally {
      md.setBusy(false);
    }
  };

  const columnsCols = useMemo<Array<DataTableColumn<QueryDef>>>(
    () => [
      { key: 'queryCode', titleKey: 'mds.dq.code', width: 180, mono: true },
      { key: 'queryName', titleKey: 'mds.dq.name', width: 220, ellipsis: true, render: (r) => <span className="flow-name-main">{r.queryName}</span> },
      { key: 'pageSizeMax', titleKey: 'mds.dq.pageSizeMax', width: 110, align: 'right' },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? 'flow-status flow-status-active' : STATUS_CLASS}>
            {t(r.enabled ? 'mds.topo.enabled' : 'mds.topo.disabled')}
          </span>
        ),
      },
      { key: 'validatedAt', titleKey: 'mds.dq.validatedAt', width: 160, render: (r) => r.validatedAt?.replace('T', ' ').slice(0, 19) ?? '—' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const headerActions =
    md.mode === 'view' && md.selected ? (
      <Can perm={MDS_PERMS.queryDef.update}>
        <CimButton type="primary" size="small" onClick={() => void openEdit(md.selected!)}>
          {t('mds.common.edit')}
        </CimButton>
      </Can>
    ) : null;

  const fields = useMemo<Array<FormField<FormState>>>(
    () => [
      { kind: 'text', key: 'queryCode', labelKey: 'mds.dq.code', mono: true, disabled: md.mode !== 'create', required: md.mode === 'create' },
      { kind: 'text', key: 'queryName', labelKey: 'mds.dq.name', required: true },
      { kind: 'number', key: 'pageSizeMax', labelKey: 'mds.dq.pageSizeMax', min: 1, max: 5000 },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [md.mode],
  );

  const readOnly = md.mode === 'view';
  const selected = md.selected;

  // 参数行内编辑（DataTable flat）
  const paramCols = useMemo<Array<DataTableColumn<ParamRow>>>(
    () => [
      {
        key: 'paramKey',
        titleKey: 'mds.dq.paramKey',
        width: 160,
        render: (r) => (
          <input
            style={{ width: '100%' }}
            value={r.paramKey}
            readOnly={readOnly}
            onChange={(e) => patchParam(r.uid, { paramKey: e.target.value })}
          />
        ),
      },
      {
        key: 'paramLabel',
        titleKey: 'mds.dq.paramLabel',
        width: 180,
        render: (r) => (
          <input
            style={{ width: '100%' }}
            value={r.paramLabel}
            readOnly={readOnly}
            onChange={(e) => patchParam(r.uid, { paramLabel: e.target.value })}
          />
        ),
      },
      {
        key: 'paramType',
        titleKey: 'mds.dq.paramType',
        width: 130,
        render: (r) => (
          <select
            style={{ width: '100%' }}
            value={r.paramType}
            disabled={readOnly}
            onChange={(e) => patchParam(r.uid, { paramType: e.target.value as QueryParamPayload['paramType'] })}
          >
            {typeDict.items.filter((i) => !i.disabled).map((i) => (
              <option key={i.value} value={i.value}>{i.label}</option>
            ))}
          </select>
        ),
      },
      {
        key: 'required',
        titleKey: 'mds.dq.required',
        width: 80,
        render: (r) => (
          <input
            type="checkbox"
            checked={r.required ?? false}
            disabled={readOnly}
            onChange={(e) => patchParam(r.uid, { required: e.target.checked })}
          />
        ),
      },
      {
        key: 'dictCode',
        titleKey: 'mds.dq.dictCode',
        width: 200,
        render: (r) =>
          r.paramType === 'DICT' ? (
            <input
              style={{ width: '100%' }}
              value={r.dictCode ?? ''}
              readOnly={readOnly}
              onChange={(e) => patchParam(r.uid, { dictCode: e.target.value })}
            />
          ) : (
            '—'
          ),
      },
      {
        key: 'ops',
        titleKey: 'mds.common.actions',
        width: 76,
        render: (r) =>
          !readOnly && (
            <CimButton size="small" danger onClick={() => setParams((list) => list.filter((it) => it.uid !== r.uid))}>
              {t('mds.common.delete')}
            </CimButton>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [readOnly, typeDict.items, t],
  );

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<QueryDef>
          titleKey="mds.dq.listTitle"
          columns={columnsCols}
          rows={md.rows}
          rowKey={(r) => r.id}
          loading={md.res.loading}
          error={md.res.error?.message ?? null}
          onRetry={md.res.refresh}
          search={{ value: md.keyword, onChange: md.setKeyword, placeholderKey: 'mds.dq.searchPlaceholder' }}
          headActions={
            <Can perm={MDS_PERMS.queryDef.create}>
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
                  <h3 className="op-detail-title">{t('mds.dq.createTitle')}</h3>
                ) : (
                  <>
                    <h3 className="op-detail-title">{selected!.queryCode}</h3>
                    <span className={selected!.enabled ? 'flow-status flow-status-active' : STATUS_CLASS}>
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
                {t('mds.dq.sqlText')}
              </h4>
              <textarea
                style={{ width: '100%', minHeight: 140, fontFamily: 'monospace', fontSize: 12 }}
                value={form.sqlText}
                readOnly={readOnly}
                onChange={(e) => setForm((f) => ({ ...f, sqlText: e.target.value }))}
              />

              <h4 className="op-detail-title" style={{ marginTop: 16 }}>
                {t('mds.dq.paramsTitle')}
              </h4>
              <DataTable<ParamRow>
                columns={paramCols}
                rows={params}
                rowKey={(r) => r.uid}
                size="small"
                flat
                actions={
                  !readOnly && (
                    <CimButton
                      size="small"
                      onClick={() =>
                        setParams((list) => [...list, { ...EMPTY_PARAM, uid: nextUid() }])
                      }
                    >
                      {t('mds.dq.paramAdd')}
                    </CimButton>
                  )
                }
              />

              {columns.length > 0 && (
                <>
                  <h4 className="op-detail-title" style={{ marginTop: 16 }}>
                    {t('mds.dq.columnsTitle')}
                  </h4>
                  <DataTable<QueryColumnMeta>
                    columns={[
                      { key: 'key', titleKey: 'mds.dq.colKey', width: 220, mono: true },
                      { key: 'label', titleKey: 'mds.dq.colTitle', width: 220 },
                      { key: 'sqlType', titleKey: 'mds.dq.paramType', width: 120 },
                    ]}
                    rows={columns}
                    rowKey={(r) => r.key}
                    size="small"
                    flat
                    showCount={false}
                  />
                </>
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
