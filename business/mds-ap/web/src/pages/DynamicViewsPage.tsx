import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  CimButton,
  DataTable,
  EmptyState,
  Page,
  Panel,
  useDictContext,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { useAuthStore } from '@/store/authStore';
import type { DynamicMeta, ViewModel } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 动态视图（DynamicViewsPage）——元数据驱动的通用列表渲染页（V39）。
 *
 * <p>左侧选 ViewModel，右侧按 meta 契约动态构造 DataTable：列显隐/顺序/宽度/
 * 对齐/字典翻译/日期格式化全部来自元数据；筛选控件按 Query 声明参数生成
 * （DICT 参数渲染字典下拉）。分页走服务端（data 契约），导出走 export 契约
 * （allow_export 管控）。MES 后期直接复用同一组契约端点。</p>
 */

interface Row {
  [key: string]: unknown;
}

/** 静态列表列定义（不走 DataTableColumn 泛型推导，动态列在下方 useMemo 构造）。 */
interface ListRow {
  id: string;
  viewCode: string;
  viewName: string;
  queryCode: string;
  enabled: boolean;
}

export default function DynamicViewsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const dictCtx = useDictContext();

  const [views, setViews] = useState<ListRow[]>([]);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [meta, setMeta] = useState<DynamicMeta | null>(null);
  const [metaErr, setMetaErr] = useState<string | null>(null);
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [dataErr, setDataErr] = useState<string | null>(null);

  // 视图清单
  useEffect(() => {
    api.listViewModels().then((all) => {
      const mapped = all.map((v: ViewModel) => ({
        id: v.id,
        viewCode: v.viewCode,
        viewName: v.viewName,
        queryCode: v.queryCode,
        enabled: v.enabled,
      }));
      setViews(mapped);
      if (mapped.length > 0 && !selectedCode) setSelectedCode(mapped[0].viewCode);
    }).catch(() => setViews([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 选中视图 → meta
  useEffect(() => {
    if (!selectedCode) {
      setMeta(null);
      return;
    }
    setMetaErr(null);
    api.dynamicMeta(selectedCode)
      .then((m) => {
        setMeta(m);
        setPageSize(m.pageSize > 0 ? m.pageSize : 20);
        setPage(1);
        // DICT 参数字典预加载
        m.params.filter((p) => p.type === 'DICT' && p.dictCode).forEach((p) => dictCtx.load(p.dictCode!));
        // DICT 格式化列的字典预加载
        m.columns.filter((c) => c.formatType === 'DICT' && c.formatArg).forEach((c) => dictCtx.load(c.formatArg!));
        setParamValues({});
      })
      .catch((e) => setMetaErr(e instanceof Error ? e.message : String(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCode]);

  const fetchData = useCallback((m: DynamicMeta, p: number, s: number, pv: Record<string, string>) => {
    setLoading(true);
    setDataErr(null);
    api.dynamicData(m.viewCode, { params: pv, page: p, size: s })
      .then((d) => {
        setRows(d.rows);
        setTotal(d.total);
      })
      .catch((e) => setDataErr(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false));
  }, []);

  // meta 或分页变化 → 拉数据
  useEffect(() => {
    if (meta) fetchData(meta, page, pageSize, paramValues);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meta, page, pageSize, fetchData]);

  const refresh = () => {
    if (meta) fetchData(meta, page, pageSize, paramValues);
  };

  const listCols = useMemo<Array<DataTableColumn<ListRow>>>(
    () => [
      { key: 'viewCode', titleKey: 'mds.dq.viewCode', width: 170, mono: true },
      { key: 'viewName', titleKey: 'mds.dq.viewName', width: 180, ellipsis: true, render: (r) => <span className="flow-name-main">{r.viewName}</span> },
      { key: 'queryCode', titleKey: 'mds.dq.queryCode', width: 150, mono: true },
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

  // 动态列：按 meta 构造（titleKey 优先，字面量兜底——i18n 未命中原样回显）
  const keyedRows = useMemo(() => rows.map((r, i) => ({ ...r, __k: i })), [rows]);

  const dynCols = useMemo<Array<DataTableColumn<Row>>>(() => {
    if (!meta) return [];
    return meta.columns
      .filter((c) => c.visible)
      .map((c) => ({
        key: c.key,
        titleKey: c.titleKey ?? c.colTitle ?? c.key,
        width: c.width ?? undefined,
        align: (c.align?.toLowerCase() as 'left' | 'center' | 'right') ?? 'left',
        render: (r: Row) => formatCell(r[c.key], c.formatType, c.formatArg, dictCtx),
      }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meta, dictCtx]);

  const doExport = () => {
    if (!meta) return;
    const qs = new URLSearchParams(
      Object.entries(paramValues).filter(([, v]) => v !== '' && v != null),
    ).toString();
    const token = useAuthStore.getState().token;
    fetch(`/api/v1/mds/view-models/${encodeURIComponent(meta.viewCode)}/export${qs ? `?${qs}` : ''}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    })
      .then((resp) => {
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        return resp.blob();
      })
      .then((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${meta.viewCode}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        toast.success(t('mds.common.saved'));
      })
      .catch((e) => toast.error(e instanceof Error ? e.message : String(e)));
  };

  return (
    <Page>
      <GuideBar />
      <div className="op-layout">
        <DataTable<ListRow>
          titleKey="mds.dq.dynamicViews"
          columns={listCols}
          rows={views}
          rowKey={(r) => r.id}
          search={undefined}
          onRowClick={(r) => setSelectedCode(r.viewCode)}
          rowClassName={(r) => (r.viewCode === selectedCode ? 'row-selected' : '')}
        />

        <Panel className="op-detail">
          {!meta && !metaErr ? (
            <EmptyState titleKey="mds.dq.dynamic.selectHint" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                <h3 className="op-detail-title">{meta ? `${meta.viewCode} ${meta.viewName}` : t('mds.dq.dynamicViews')}</h3>
                {meta?.allowExport && (
                  <div className="op-detail-actions">
                    <CimButton size="small" onClick={doExport}>
                      {t('mds.dq.dynamic.export')}
                    </CimButton>
                  </div>
                )}
              </div>

              {metaErr && <p className="op-error">{metaErr}</p>}
              {dataErr && <p className="op-error">{dataErr}</p>}

              {meta && meta.params.length > 0 && (
                <div className="state-initial-states" style={{ alignItems: 'flex-end' }}>
                  {meta.params.map((p) => (
                    <div key={p.key} style={{ minWidth: 180 }}>
                      <label className="state-editor-label">{p.label}</label>
                      {p.type === 'DICT' && p.dictCode ? (
                        <select
                          className="line-select"
                          style={{ width: '100%' }}
                          value={paramValues[p.key] ?? ''}
                          onChange={(e) => setParamValues((pv) => ({ ...pv, [p.key]: e.target.value }))}
                        >
                          <option value="">—</option>
                          {dictCtx.get(p.dictCode).filter((i) => !i.disabled).map((i) => (
                            <option key={i.value} value={i.value}>{i.label}</option>
                          ))}
                        </select>
                      ) : (
                        <input
                          style={{ width: '100%' }}
                          type={p.type === 'DATE' ? 'date' : p.type === 'NUMBER' ? 'number' : 'text'}
                          value={paramValues[p.key] ?? ''}
                          onChange={(e) => setParamValues((pv) => ({ ...pv, [p.key]: e.target.value }))}
                        />
                      )}
                    </div>
                  ))}
                  <CimButton type="primary" size="small" onClick={refresh}>
                    {t('mds.common.search')}
                  </CimButton>
                </div>
              )}

              <DataTable<Row>
                columns={dynCols}
                rows={keyedRows}
                rowKey={(r) => String(r.__k)}
                loading={loading}
                scrollX="max-content"
                pagination={{
                  page,
                  size: pageSize,
                  total,
                  onChange: (p, s) => {
                    setPage(p);
                    setPageSize(s);
                  },
                }}
                showCount={false}
                title={undefined}
              />
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}

/** 单元格格式化：DICT 字典翻译 / DATE、DATETIME 截取 / 其余原样。 */
function formatCell(
  value: unknown,
  formatType: string,
  formatArg: string | null,
  dictCtx: { get: (code: string) => Array<{ value: string; label: string }> },
): string {
  if (value == null) return '—';
  switch (formatType) {
    case 'DICT': {
      if (!formatArg) return String(value);
      const hit = dictCtx.get(formatArg).find((i) => i.value === String(value));
      return hit ? hit.label : String(value);
    }
    case 'DATE':
      return String(value).slice(0, 10);
    case 'DATETIME':
      return String(value).replace('T', ' ').slice(0, 19);
    default:
      return String(value);
  }
}
