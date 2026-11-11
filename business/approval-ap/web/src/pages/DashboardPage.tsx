import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useResource } from '@cim/data';
import { KpiCard, KpiGrid, InlineAlert, DataTable, Page, Panel, type DataTableColumn, CimButton } from '@cim/ui';
import { IconCheck, IconFlow, IconInbox, IconOutbox, IconWebhook } from '@/components/Icons';
import {
  getStats,
  listDefinitions,
  listDonePaged,
  listInitiatedPaged,
  listTodoPaged,
  queryOutbox,
} from '@/lib/api';
import { fmtDate } from '@/lib/format';
import type { ProcessDefinitionView, StatsView } from '@/types';

interface Overview {
  todo: number | null;
  initiated: number | null;
  definitions: number | null;
  done: number | null;
  outboxPending: number | null;
  stats: StatsView | null;
  /** 失败的路数（6 路里几路没回来）。0 表示全部成功。 */
  failed: number;
}

interface Kpi {
  key: string;
  labelKey: string;
  value: number | null;
  icon: ReactNode;
  to: string;
  tone?: 'ok' | 'warn' | 'err' | 'run' | 'idle';
}

const TOTAL_ROUTES = 6;

export default function DashboardPage() {
  const { t } = useTranslation();

  /**
   * 六路请求合成**一个**资源。
   *
   * <p>改造前是 `Promise.all` + `try/catch` 吞掉全部错误：任意一路失败（比如回调服务没起）
   * 整页 KPI 全停在 0，看不出是「真的没有」还是「没取到」。这里用 `allSettled`，
   * 单路失败只让那一格显示 `—`，并统计失败路数；全挂才整页报错。</p>
   */
  const overviewRes = useResource<Overview>(async ({ signal }) => {
    const opts = { signal };
    const settled = await Promise.allSettled([
      listTodoPaged(1, 1, opts),
      listDonePaged(1, 1, opts),
      listInitiatedPaged(1, 1, opts),
      listDefinitions(opts),
      queryOutbox(undefined, undefined, opts),
      getStats(30, opts),
    ]);
    const pick = <T,>(i: number): T | null => {
      const r = settled[i];
      return r.status === 'fulfilled' ? (r.value as T) : null;
    };
    const completed = settled.filter((r) => r.status === 'fulfilled').length;
    if (completed === 0) {
      const first = settled.find((r) => r.status === 'rejected') as PromiseRejectedResult | undefined;
      throw first?.reason ?? new Error('load failed');
    }
    const todoP = pick<{ total: number }>(0);
    const doneP = pick<{ total: number }>(1);
    const initP = pick<{ total: number }>(2);
    const defs = pick<ProcessDefinitionView[]>(3);
    const outbox = pick<{ status: string }[]>(4);
    return {
      todo: todoP?.total ?? null,
      done: doneP?.total ?? null,
      initiated: initP?.total ?? null,
      definitions: defs?.length ?? null,
      outboxPending: outbox ? outbox.filter((o) => o.status === 'PENDING').length : null,
      stats: pick<StatsView>(5),
      failed: TOTAL_ROUTES - completed,
    };
  });

  const ov = overviewRes.data;
  const stats = ov?.stats ?? null;

  /** KPI 值：`null` = 这一路没取到，显示 `—` 而不是 0。 */
  const kpis: Kpi[] = [
    { key: 'todo', labelKey: 'dash.kpi.todo', value: ov?.todo ?? null, icon: <IconInbox width={16} height={16} />, to: '/todo', tone: 'run' },
    { key: 'initiated', labelKey: 'dash.kpi.initiated', value: ov?.initiated ?? null, icon: <IconOutbox width={16} height={16} />, to: '/initiated' },
    { key: 'definitions', labelKey: 'dash.kpi.definitions', value: ov?.definitions ?? null, icon: <IconFlow width={16} height={16} />, to: '/definitions' },
    { key: 'done', labelKey: 'dash.kpi.done', value: ov?.done ?? null, icon: <IconCheck width={16} height={16} />, to: '/done' },
    { key: 'outbox', labelKey: 'dash.kpi.outboxPending', value: ov?.outboxPending ?? null, icon: <IconWebhook width={16} height={16} />, to: '/callbacks', tone: 'warn' },
  ];

  const maxTrend = stats ? Math.max(1, ...stats.trend.map((d) => Math.max(d.started, d.completed))) : 1;

  const workloadColumns: Array<DataTableColumn<StatsView['workloads'][number]>> = [
    { key: 'assignee', titleKey: 'dash.stats.assignee', width: 160 },
    { key: 'done', titleKey: 'dash.stats.done', width: 120, align: 'right' },
    { key: 'todo', titleKey: 'dash.stats.todo', width: 120, align: 'right' },
  ];

  return (
    <Page>
      <Panel
        titleKey="dash.title"
        subKey="dash.sub"
        actions={
          <CimButton size="small" onClick={overviewRes.refresh} loading={overviewRes.loading}>
            {t('common.refresh')}
          </CimButton>
        }
      >
        {overviewRes.error ? (
          <InlineAlert tone="err">{overviewRes.error.message}</InlineAlert>
        ) : (
          <>
            <KpiGrid>
              {kpis.map((k) => (
                <Link key={k.key} to={k.to} className={'kpi' + (k.tone ? ' ' + k.tone : '')} style={{ textDecoration: 'none' }}>
                  {/* 整卡是链接：外壳与跳转归 <Link>，卡片内容由 KpiCard bare 输出 */}
                  <KpiCard
                    bare
                    icon={k.icon}
                    label={t(k.labelKey)}
                    value={overviewRes.loading && !ov ? '…' : (k.value ?? '—')}
                  />
                </Link>
              ))}
            </KpiGrid>
            {ov && ov.failed > 0 && (
              <p className="dim" style={{ marginTop: 8, fontSize: 11 }}>
                {t('dash.partial', { n: ov.failed, total: TOTAL_ROUTES })}
              </p>
            )}
          </>
        )}
      </Panel>

      {stats && (
        <Panel titleKey="dash.stats.title" subKey="dash.stats.sub">
          <KpiGrid>
            <KpiCard icon={<IconFlow width={16} height={16} />} label={t('dash.stats.avgHours')} value={stats.avgCompleteHours != null ? stats.avgCompleteHours.toFixed(1) + ' h' : '—'} />
            <div className={'kpi' + (stats.overdueCount > 0 ? ' warn' : '')}>
              <KpiCard
                bare
                icon={<IconInbox width={16} height={16} />}
                label={t('dash.stats.overdue')}
                value={stats.overdueCount}
              />
            </div>
            <KpiCard icon={<IconCheck width={16} height={16} />} label={t('dash.stats.rejectRate')} value={stats.rejectRate != null ? (stats.rejectRate * 100).toFixed(0) + '%' : '—'} />
          </KpiGrid>

          {/* 近 30 天发起/完成趋势（纯 CSS 条形；不引图表库，一面板里两根线不值得 200 KB） */}
          <div className="stats-trend">
            {stats.trend.map((d) => (
              <div
                key={d.date}
                className="stats-trend-col"
                title={`${d.date} · ${t('dash.stats.started')} ${d.started} / ${t('dash.stats.completed')} ${d.completed}`}
              >
                <div className="stats-bars">
                  <div className="bar started" style={{ height: `${(d.started / maxTrend) * 48 + 1}px` }} />
                  <div className="bar completed" style={{ height: `${(d.completed / maxTrend) * 48 + 1}px` }} />
                </div>
                <span className="stats-day">{d.date.slice(8)}</span>
              </div>
            ))}
          </div>
          <div className="stats-legend">
            <span><i className="dot started" /> {t('dash.stats.started')}</span>
            <span><i className="dot completed" /> {t('dash.stats.completed')}</span>
          </div>

          {stats.workloads.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <DataTable<StatsView['workloads'][number]>
                flat
                columns={workloadColumns}
                rows={stats.workloads}
                rowKey={(w) => w.assignee}
                emptyTitleKey="common.empty"
              />
            </div>
          )}
        </Panel>
      )}

      <Panel titleKey="dash.quick">
        <div className="grid-3">
          <Link to="/todo" className="btn btn-ghost">
            <IconInbox width={15} height={15} /> {t('dash.gotoTodo')}
          </Link>
          <Link to="/definitions" className="btn btn-ghost">
            <IconFlow width={15} height={15} /> {t('dash.gotoStart')}
          </Link>
          <Link to="/callbacks" className="btn btn-ghost">
            <IconWebhook width={15} height={15} /> {t('dash.gotoCallbacks')}
          </Link>
        </div>
        <p className="hint">{fmtDate(new Date().toISOString())}</p>
      </Panel>
    </Page>
  );
}
