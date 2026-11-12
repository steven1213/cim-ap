import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimDatePicker,
  CimDescriptions,
  CimDrawer,
  CimInput,
  CimSelect,
  CimTag,
  DataTable,
  Page,
  Panel,
  Toolbar,
  type DataTableColumn,
  type DescItem,
  CimButton,
  InlineAlert,
  KpiCard,

  KpiGrid,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import * as api from '@/lib/rmsApi';
import type { AuditRow, AuditSummary } from '@/types';

/** 已知动作类型（供类型过滤下拉；标签经 `rms.audit.t.*` 本地化，缺失回退原码）。 */
const AUDIT_TYPES = [
  'RECIPE_CREATED', 'RECIPE_UPDATED', 'RECIPE_DELETED', 'RECIPE_COPIED',
  'RECIPE_VERSION_CREATED', 'RECIPE_ACTIVATED',
  'SIGNOFF_SUBMITTED', 'SIGNOFF_APPROVED', 'SIGNOFF_REJECTED',
  'SIGNOFF_TEMP_ALLOWED', 'SIGNOFF_TEMP_CANCELLED',
  'COMPARE_RUN', 'COMPARE_BATCH',
  'DEVICE_TYPE_CREATED', 'DEVICE_TYPE_UPDATED', 'DEVICE_TYPE_DELETED',
  'DEVICE_AREA_CREATED', 'DEVICE_AREA_UPDATED', 'DEVICE_AREA_DELETED',
  'DEVICE_CREATED', 'DEVICE_UPDATED', 'DEVICE_DELETED',
];

const TYPE_OPTIONS = AUDIT_TYPES.map((tp) => ({ value: tp, labelKey: `rms.audit.t.${tp}` }));

const RESULT_OPTIONS = [
  { value: 'SUCCESS', labelKey: 'rms.audit.resultOk' },
  { value: 'FAILURE', labelKey: 'rms.audit.resultFail' },
];

const LIMIT_OPTIONS = [
  { value: '100', labelKey: 'rms.audit.recent100' },
  { value: '200', labelKey: 'rms.audit.recent200' },
  { value: '500', labelKey: 'rms.audit.recent500' },
];

const NO_ROWS: AuditRow[] = [];

/** 动作码 → 语义档（后端返回的是动作码，颜色只能按「动作语义」判定，不是 UI 自选）。 */
function toneOfAction(type: string): StatusTone {
  if (type.endsWith('_DELETED') || type === 'SIGNOFF_REJECTED') return 'err';
  if (type === 'SIGNOFF_TEMP_CANCELLED') return 'warn';
  if (type.startsWith('SIGNOFF_')) return type === 'SIGNOFF_APPROVED' ? 'ok' : 'warn';
  if (type.startsWith('COMPARE_')) return type === 'COMPARE_RUN' ? 'idle' : 'run';
  if (type.endsWith('_ACTIVATED')) return 'ok';
  return 'idle';
}

interface AuditFilter {
  type: string;
  result: string;
  actor: string;
  from: string;
  to: string;
  keyword: string;
  limit: number;
}

const EMPTY_FILTER: AuditFilter = {
  type: '', result: '', actor: '', from: '', to: '', keyword: '', limit: 200,
};

/**
 * 审计日志（Req 12/17/18/19、FR-H1~H3）：多维过滤 + 汇总统计 + 配方对象历史 + 详情抽屉。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>筛选拆成「草稿」与「已应用」两份状态。旧实现点搜索才 `load()`，但翻页器/
 *       刷新按钮调的是同一个 `load()`，读的是**当时闭包里的 state** —— 改了下拉没点搜索
 *       再点刷新，请求带的是旧条件。现在渲染只读草稿、取数只读已应用，两者不会串。</li>
 *   <li>取数与竞态交给 `useResource`（连续点搜索时先发的慢回包不会覆盖新结果）。</li>
 *   <li>错误交给 `DataTable` 的 `error` 属性（整块错误态 + 重试），不再用页顶红条 ——
 *       红条会和「表格空」同时出现，看不出是取数失败还是真没数据。</li>
 * </ul>
 */
export default function AuditLogPage() {
  const { t } = useTranslation();

  const [draft, setDraft] = useState<AuditFilter>(EMPTY_FILTER);
  const [applied, setApplied] = useState<AuditFilter>(EMPTY_FILTER);
  const [detail, setDetail] = useState<AuditRow | null>(null);

  // 配方对象历史
  const [hCode, setHCode] = useState('');
  const [hFrom, setHFrom] = useState('');
  const [hTo, setHTo] = useState('');
  const [hQuery, setHQuery] = useState<{ code: string; from: string; to: string } | null>(null);
  const [hError, setHError] = useState('');

  const summaryRes = useResource(() => api.auditSummary());

  const listRes = useResource(
    () =>
      api.listAudit({
        limit: applied.limit,
        ...(applied.type ? { type: applied.type } : {}),
        ...(applied.result ? { result: applied.result } : {}),
        ...(applied.actor.trim() ? { actor: applied.actor.trim() } : {}),
        ...(applied.from ? { from: applied.from } : {}),
        ...(applied.to ? { to: applied.to } : {}),
        ...(applied.keyword.trim() ? { keyword: applied.keyword.trim() } : {}),
      }),
    { deps: [applied], keepPreviousData: true },
  );
  const rows = listRes.data ?? NO_ROWS;

  const historyRes = useResource(
    () =>
      hQuery
        ? api.recipeHistory({
            code: hQuery.code,
            ...(hQuery.from ? { from: hQuery.from } : {}),
            ...(hQuery.to ? { to: hQuery.to } : {}),
            max: 200,
          })
        : Promise.resolve(NO_ROWS),
    { deps: [hQuery], enabled: !!hQuery },
  );

  const summary: AuditSummary | undefined = summaryRes.data;

  const typeLabel = (tp: string) => t(`rms.audit.t.${tp}`, { defaultValue: tp });

  const columns = useMemo<Array<DataTableColumn<AuditRow>>>(
    () => [
      { key: 'createTime', titleKey: 'rms.audit.th.time', mono: true, width: 170 },
      {
        key: 'type',
        titleKey: 'rms.audit.th.type',
        width: 150,
        render: (r) => (
          <CimTag tone={toneOfAction(r.type)} size="small">{typeLabel(r.type)}</CimTag>
        ),
      },
      {
        key: 'result',
        titleKey: 'rms.audit.th.result',
        width: 80,
        render: (r) => (
          <CimTag tone={r.result === 'FAILURE' ? 'err' : 'ok'} size="small">
            {r.result === 'FAILURE' ? t('rms.audit.fail') : t('rms.audit.ok')}
          </CimTag>
        ),
      },
      {
        key: 'actor',
        titleKey: 'rms.audit.th.actor',
        mono: true,
        width: 110,
        render: (r) => r.actor ?? t('rms.common.dash'),
      },
      {
        key: 'target',
        titleKey: 'rms.audit.th.target',
        mono: true,
        width: 150,
        ellipsis: true,
        render: (r) => r.target ?? t('rms.common.dash'),
      },
      {
        key: 'detail',
        titleKey: 'rms.audit.th.detail',
        ellipsis: true,
        render: (r) => <span className="dim">{r.detail ?? t('rms.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 90,
        render: (r) => (
          <CimButton size="small" onClick={() => setDetail(r)}>
            {t('rms.audit.detail')}
          </CimButton>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const historyColumns = useMemo<Array<DataTableColumn<AuditRow>>>(
    () => [
      { key: 'createTime', titleKey: 'rms.audit.th.time', mono: true, width: 170 },
      {
        key: 'type',
        titleKey: 'rms.audit.th.type',
        width: 160,
        render: (r) => (
          <CimTag tone={toneOfAction(r.type)} size="small">{typeLabel(r.type)}</CimTag>
        ),
      },
      {
        key: 'actor',
        titleKey: 'rms.audit.th.actor',
        mono: true,
        width: 110,
        render: (r) => r.actor ?? t('rms.common.dash'),
      },
      {
        key: 'detail',
        titleKey: 'rms.audit.th.detail',
        ellipsis: true,
        render: (r) => <span className="dim">{r.detail ?? t('rms.common.dash')}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const detailItems = useMemo<Array<DescItem<AuditRow>>>(
    () => [
      { key: 'time', labelKey: 'rms.audit.th.time', dataIndex: 'createTime' },
      {
        key: 'type',
        labelKey: 'rms.audit.th.type',
        render: (r) => (
          <CimTag tone={toneOfAction(r.type)} size="small">{typeLabel(r.type)}</CimTag>
        ),
      },
      {
        key: 'result',
        labelKey: 'rms.audit.th.result',
        render: (r) => (
          <CimTag tone={r.result === 'FAILURE' ? 'err' : 'ok'} size="small">
            {r.result === 'FAILURE' ? t('rms.audit.fail') : t('rms.audit.ok')}
          </CimTag>
        ),
      },
      { key: 'actor', labelKey: 'rms.audit.th.actor', dataIndex: 'actor' },
      { key: 'target', labelKey: 'rms.audit.th.target', dataIndex: 'target' },
      { key: 'detail', labelKey: 'rms.audit.th.detail', dataIndex: 'detail', span: 3 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const apply = (patch: Partial<AuditFilter>) => setApplied((prev) => ({ ...prev, ...patch }));

  const queryHistory = () => {
    const code = hCode.trim();
    if (!code) {
      setHError(t('rms.audit.history.codeRequired'));
      return;
    }
    setHError('');
    setHQuery({ code, from: hFrom, to: hTo });
  };

  return (
    <Page>
      {/* KPI */}
      <KpiGrid>
        <KpiCard label={t('rms.audit.kpi.total')} value={summary?.total ?? '—'} />
        <KpiCard tone="ok" label={t('rms.audit.kpi.success')} value={summary?.success ?? '—'} />
        <KpiCard tone="warn" label={t('rms.audit.kpi.failure')} value={summary?.failure ?? '—'} />
        <KpiCard tone="info" label={t('rms.audit.kpi.today')} value={summary?.today ?? '—'} />
      </KpiGrid>

      {/* 动作分布 */}
      {summary && summary.topTypes.length > 0 && (
        <Panel titleKey="rms.audit.topTypes">
          <div className="tag-list">
            {summary.topTypes.map((tc) => (
              <span key={tc.type} title={tc.type}>
                <CimTag tone={toneOfAction(tc.type)}>
                  {typeLabel(tc.type)}
                  <b className="tag-count">{tc.count}</b>
                </CimTag>
              </span>
            ))}
          </div>
        </Panel>
      )}

      {/* 审计流水 */}
      <Toolbar
        left={
          <>
            <CimSelect
              value={draft.type}
              onChange={(v) => setDraft((p) => ({ ...p, type: typeof v === 'string' ? v : '' }))}
              options={TYPE_OPTIONS}
              placeholderKey="rms.audit.filterType"
              width={170}
            />
            <CimSelect
              value={draft.result}
              onChange={(v) => setDraft((p) => ({ ...p, result: typeof v === 'string' ? v : '' }))}
              options={RESULT_OPTIONS}
              placeholderKey="rms.audit.filterResult"
              width={130}
            />
            <CimInput
              value={draft.actor}
              onChange={(v) => setDraft((p) => ({ ...p, actor: v }))}
              placeholderKey="rms.audit.actorPh"
              width={130}
              onEnter={() => apply({ actor: draft.actor })}
            />
            <CimDatePicker
              value={draft.from}
              onChange={(v) => setDraft((p) => ({ ...p, from: v ?? '' }))}
              placeholderKey="rms.audit.filterFrom"
              width={145}
            />
            <CimDatePicker
              value={draft.to}
              onChange={(v) => setDraft((p) => ({ ...p, to: v ?? '' }))}
              placeholderKey="rms.audit.filterTo"
              width={145}
            />
            <CimInput
              value={draft.keyword}
              onChange={(v) => setDraft((p) => ({ ...p, keyword: v }))}
              placeholderKey="rms.audit.keywordPh"
              width={170}
              onEnter={() => apply({ keyword: draft.keyword })}
            />
            <CimSelect
              value={String(draft.limit)}
              onChange={(v) => {
                const limit = Number(v) || 200;
                setDraft((p) => ({ ...p, limit }));
                // 条数原本就是「改了立刻生效」，这里保留，避免用户改完以为没反应
                apply({ limit });
              }}
              options={LIMIT_OPTIONS}
              allowClear={false}
              width={130}
            />
            <CimButton type="primary" size="small" onClick={() => apply(draft)}>
              {t('rms.common.search')}
            </CimButton>
          </>
        }
      />

      <DataTable<AuditRow>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={listRes.loading}
        error={listRes.error?.message}
        onRetry={listRes.refresh}
        onRefresh={() => {
          listRes.refresh();
          summaryRes.refresh();
        }}
        settingsKey="rms.audit"
        scrollX="max-content"
        emptyTitleKey="rms.audit.empty"
        emptyHintKey="rms.audit.emptyHint"
      />

      {/* 配方对象历史（FR-H3 / Req 19） */}
      <Panel titleKey="rms.audit.history.title" flush>
        <Toolbar
          left={
            <>
              <CimInput
                value={hCode}
                onChange={setHCode}
                placeholderKey="rms.audit.history.codePh"
                width={200}
                mono
                onEnter={queryHistory}
              />
              <CimDatePicker
                value={hFrom}
                onChange={(v) => setHFrom(v ?? '')}
                placeholderKey="rms.audit.filterFrom"
                width={145}
              />
              <CimDatePicker
                value={hTo}
                onChange={(v) => setHTo(v ?? '')}
                placeholderKey="rms.audit.filterTo"
                width={145}
              />
              <CimButton type="primary" size="small" onClick={queryHistory}>
                {t('rms.audit.history.query')}
              </CimButton>
            </>
          }
        />

        {hError ? (
          <InlineAlert tone="err" style={{ margin: '0 14px 12px' }}>{hError}</InlineAlert>
        ) : null}

        {hQuery ? (
          <DataTable<AuditRow>
            flat
            columns={historyColumns}
            rows={historyRes.data ?? NO_ROWS}
            rowKey={(r) => r.id}
            loading={historyRes.loading}
            error={historyRes.error?.message}
            onRetry={historyRes.refresh}
            scrollX="max-content"
            emptyTitleKey="rms.audit.history.empty"
          />
        ) : null}
      </Panel>

      <CimDrawer
        open={!!detail}
        onClose={() => setDetail(null)}
        title={t('rms.audit.detail')}
        width={560}
      >
        {detail ? <CimDescriptions record={detail} items={detailItems} column={1} /> : null}
      </CimDrawer>
    </Page>
  );
}
