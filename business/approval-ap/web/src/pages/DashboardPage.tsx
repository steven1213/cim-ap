import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import Panel from '@/components/Panel';
import { IconInbox, IconOutbox, IconFlow, IconCheck, IconWebhook } from '@/components/Icons';
import {
  listDefinitions,
  listTodoPaged,
  listDonePaged,
  listInitiatedPaged,
  queryOutbox,
  getStats,
} from '@/lib/api';
import { fmtDate } from '@/lib/format';
import type { StatsView } from '@/types';

interface Kpi {
  label: string;
  value: number;
  icon: ReactNode;
  to: string;
  tone?: string;
}

export default function DashboardPage() {
  const { t } = useTranslation();
  const [kpis, setKpis] = useState<Kpi[]>([]);
  const [stats, setStats] = useState<StatsView | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      const [todoP, doneP, initP, defs, outbox, st] = await Promise.all([
        listTodoPaged(1, 1),
        listDonePaged(1, 1),
        listInitiatedPaged(1, 1),
        listDefinitions(),
        queryOutbox(undefined, undefined),
        getStats(30).catch(() => null),
      ]);
      if (st) setStats(st);
      const pending = outbox.filter((o) => o.status === 'PENDING').length;
      setKpis([
        { label: t('dash.kpi.todo'), value: todoP.total, icon: <IconInbox width={16} height={16} />, to: '/todo', tone: 'info' },
        { label: t('dash.kpi.initiated'), value: initP.total, icon: <IconOutbox width={16} height={16} />, to: '/initiated' },
        { label: t('dash.kpi.definitions'), value: defs.length, icon: <IconFlow width={16} height={16} />, to: '/definitions' },
        { label: t('dash.kpi.done'), value: doneP.total, icon: <IconCheck width={16} height={16} />, to: '/done' },
        { label: t('dash.kpi.outboxPending'), value: pending, icon: <IconWebhook width={16} height={16} />, to: '/callbacks', tone: 'warn' },
      ]);
    } catch (e) {
      // 静默：页面会显示 0；错误由后端/网络提示兜底
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const maxTrend = stats ? Math.max(1, ...stats.trend.map((d) => Math.max(d.started, d.completed))) : 1;

  return (
    <div className="page">
      <Panel title={t('dash.title')} sub={t('dash.sub')}>
        <div className="kpi-grid">
          {kpis.map((k) => (
            <Link
              key={k.label}
              to={k.to}
              className={'kpi' + (k.tone ? ' ' + k.tone : '')}
              style={{ textDecoration: 'none' }}
            >
              <span className="kpi-ico">{k.icon}</span>
              <span className="kpi-main">
                <span className="kpi-label">{k.label}</span>
                <span className="kpi-val">{loading ? '…' : k.value}</span>
              </span>
            </Link>
          ))}
        </div>
      </Panel>

      {stats && (
        <Panel title={t('dash.stats.title')} sub={t('dash.stats.sub')}>
          <div className="kpi-grid">
            <div className="kpi">
              <span className="kpi-ico"><IconFlow width={16} height={16} /></span>
              <span className="kpi-main">
                <span className="kpi-label">{t('dash.stats.avgHours')}</span>
                <span className="kpi-val">
                  {stats.avgCompleteHours != null ? stats.avgCompleteHours.toFixed(1) + ' h' : '—'}
                </span>
              </span>
            </div>
            <div className={'kpi' + (stats.overdueCount > 0 ? ' warn' : '')}>
              <span className="kpi-ico"><IconInbox width={16} height={16} /></span>
              <span className="kpi-main">
                <span className="kpi-label">{t('dash.stats.overdue')}</span>
                <span className="kpi-val">{stats.overdueCount}</span>
              </span>
            </div>
            <div className="kpi">
              <span className="kpi-ico"><IconCheck width={16} height={16} /></span>
              <span className="kpi-main">
                <span className="kpi-label">{t('dash.stats.rejectRate')}</span>
                <span className="kpi-val">
                  {stats.rejectRate != null ? (stats.rejectRate * 100).toFixed(0) + '%' : '—'}
                </span>
              </span>
            </div>
          </div>

          {/* 近 30 天发起/完成趋势（纯 CSS 条形） */}
          <div className="stats-trend">
            {stats.trend.map((d) => (
              <div key={d.date} className="stats-trend-col" title={`${d.date} · ${t('dash.stats.started')} ${d.started} / ${t('dash.stats.completed')} ${d.completed}`}>
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

          {/* 审批人负载 TOP5 */}
          {stats.workloads.length > 0 && (
            <div className="table-wrap" style={{ marginTop: 12 }}>
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('dash.stats.assignee')}</th>
                    <th>{t('dash.stats.done')}</th>
                    <th>{t('dash.stats.todo')}</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.workloads.map((w) => (
                    <tr key={w.assignee}>
                      <td>{w.assignee}</td>
                      <td>{w.done}</td>
                      <td>{w.todo}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      )}

      <Panel title={t('dash.quick')}>
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
    </div>
  );
}
