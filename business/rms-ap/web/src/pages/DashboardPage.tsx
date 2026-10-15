import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '@/lib/rmsApi';
import { fmtDateTime } from '@/lib/format';
import { IconAlert, IconInbox, IconBan, IconCheckCircle } from '@/components/Icons';
import type { AlarmView, InventoryRow, Recipe, Signoff, SignoffSummary, InventorySummary } from '@/types';

/**
 * 概览 · 任务中心（P2 重组）：进系统第一屏回答「现在有什么要处理」——
 * 待批签核 / 机台配方漂移 / 孤儿配方 / 未确认报警，每条可点击直达处理现场；
 * 数量型统计（配方/机台总数）降级为次要信息。
 */
export default function DashboardPage() {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [summary, setSummary] = useState<SignoffSummary | null>(null);
  const [inv, setInv] = useState<InventorySummary | null>(null);
  const [pending, setPending] = useState<Signoff[]>([]);
  const [drift, setDrift] = useState<InventoryRow[]>([]);
  const [orphan, setOrphan] = useState<InventoryRow[]>([]);
  const [alarms, setAlarms] = useState<AlarmView[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([
      api.getSignoffSummary(),
      api.toolInventorySummary(),
      api.listSignoffs({ status: 'PENDING' }),
      api.listToolInventory({ result: 'DRIFT' }),
      api.listToolInventory({ result: 'ORPHAN' }),
      api.listAlarms({ unackOnly: true }),
      api.listRecipes(),
    ]).then(([a, b, c, d, e, f, g]) => {
      if (!alive) return;
      if (a.status === 'fulfilled') setSummary(a.value);
      if (b.status === 'fulfilled') setInv(b.value);
      if (c.status === 'fulfilled') setPending(c.value);
      if (d.status === 'fulfilled') setDrift(d.value);
      if (e.status === 'fulfilled') setOrphan(e.value);
      if (f.status === 'fulfilled') setAlarms(f.value);
      if (g.status === 'fulfilled') setRecipes(g.value);
      setLoading(false);
    });
    return () => {
      alive = false;
    };
  }, []);

  const cards = [
    {
      n: summary?.pending ?? 0,
      label: t('rms.dashboard.todoSignoff'),
      to: '/signoff',
      Icon: IconInbox,
      tone: (summary?.pending ?? 0) > 0 ? 'warn' : 'ok',
    },
    {
      n: inv?.drift ?? 0,
      label: t('rms.dashboard.todoDrift'),
      to: '/device-recipes',
      Icon: IconAlert,
      tone: (inv?.drift ?? 0) > 0 ? 'err' : 'ok',
    },
    {
      n: inv?.orphan ?? 0,
      label: t('rms.dashboard.todoOrphan'),
      to: '/device-recipes',
      Icon: IconBan,
      tone: (inv?.orphan ?? 0) > 0 ? 'err' : 'ok',
    },
    {
      n: alarms.length,
      label: t('rms.dashboard.todoAlarm'),
      to: '/integration',
      Icon: IconAlert,
      tone: alarms.length > 0 ? 'warn' : 'ok',
    },
    {
      n: summary?.tempActive ?? 0,
      label: t('rms.dashboard.todoTemp'),
      to: '/signoff',
      Icon: IconCheckCircle,
      tone: (summary?.tempActive ?? 0) > 0 ? 'warn' : 'ok',
    },
    {
      n: summary?.expiringSoon ?? 0,
      label: t('rms.dashboard.todoExpiring'),
      to: '/signoff',
      Icon: IconCheckCircle,
      tone: (summary?.expiringSoon ?? 0) > 0 ? 'warn' : 'ok',
    },
  ];

  const todos = loading ? 0 : pending.length + drift.length + orphan.length;

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.dashboard.title')}</h1>
        <p className="dim" style={{ margin: '2px 0 0' }}>
          {loading ? '' : todos > 0 ? t('rms.dashboard.todoBanner', { n: todos }) : t('rms.dashboard.allClear')}
        </p>
      </div>

      {/* 行动卡片：点卡片直达处理现场 */}
      <div className="kpi-grid">
        {cards.map((k) => (
          <Link key={k.label} to={k.to} className="kpi">
            <div className={'tag ' + k.tone} style={{ marginBottom: 6 }}>
              {loading ? '—' : k.n}
            </div>
            <div className="kpi-label">{k.label}</div>
          </Link>
        ))}
      </div>

      <div className="grid-2">
        {/* 待批签核清单 */}
        <div className="panel">
          <div className="panel-head">
            <h3>{t('rms.dashboard.pendingSignoffs')}</h3>
            <Link to="/signoff" className="btn btn-ghost btn-sm">
              {t('rms.nav.signoff')}
            </Link>
          </div>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('rms.recipe.code')}</th>
                  <th>{t('rms.recipe.version')}</th>
                  <th>{t('rms.signoff.reason')}</th>
                  <th>{t('rms.common.time')}</th>
                </tr>
              </thead>
              <tbody>
                {pending.slice(0, 8).map((s) => (
                  <tr key={s.id} className="row-link" onClick={() => nav('/signoff')}>
                    <td className="mono">{s.recipeCode ?? '—'}</td>
                    <td className="mono">{s.versionNo != null ? `v${s.versionNo}` : '—'}</td>
                    <td>{s.reason ?? '—'}</td>
                    <td className="muted">{fmtDateTime(s.createTime)}</td>
                  </tr>
                ))}
                {!loading && pending.length === 0 && (
                  <tr>
                    <td colSpan={4} className="empty-cell">
                      {t('rms.dashboard.nonePending')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* 机台配方异常清单（漂移 + 孤儿） */}
        <div className="panel">
          <div className="panel-head">
            <h3>{t('rms.dashboard.deviceIssues')}</h3>
            <Link to="/device-recipes" className="btn btn-ghost btn-sm">
              {t('rms.nav.deviceRecipes')}
            </Link>
          </div>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('rms.device.code')}</th>
                  <th>PPID</th>
                  <th>{t('rms.common.status')}</th>
                  <th>{t('rms.common.time')}</th>
                </tr>
              </thead>
              <tbody>
                {[...drift, ...orphan].slice(0, 8).map((r) => (
                  <tr key={r.id} className="row-link" onClick={() => nav('/device-recipes')}>
                    <td className="mono">{r.deviceCode ?? '—'}</td>
                    <td className="mono">{r.ppidOnTool}</td>
                    <td>
                      <span className={'tag ' + (r.reconcileResult === 'DRIFT' ? 'err' : 'warn')}>
                        {r.reconcileResult === 'DRIFT'
                          ? t('rms.dashboard.drift')
                          : t('rms.dashboard.orphan')}
                      </span>
                    </td>
                    <td className="muted">{fmtDateTime(r.lastSeenAt)}</td>
                  </tr>
                ))}
                {!loading && drift.length + orphan.length === 0 && (
                  <tr>
                    <td colSpan={4} className="empty-cell">
                      {t('rms.dashboard.noneIssues')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* 次要：最近配方（保留原有上下文） */}
      <div className="panel">
        <div className="panel-head">
          <h3>{t('rms.dashboard.recent')}</h3>
          <Link to="/recipes" className="btn btn-ghost btn-sm">
            {t('rms.nav.recipes')}
          </Link>
        </div>
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.recipe.code')}</th>
              <th>{t('rms.recipe.name')}</th>
              <th>{t('rms.recipe.activeVersion')}</th>
              <th>{t('rms.common.status')}</th>
            </tr>
          </thead>
          <tbody>
            {recipes.slice(0, 8).map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.code}</td>
                <td>{r.name}</td>
                <td className="mono">{r.activeVersionNo ?? '—'}</td>
                <td>{fmtDateTime(r.updateTime)}</td>
              </tr>
            ))}
            {!loading && recipes.length === 0 && (
              <tr>
                <td colSpan={4} className="empty-cell">
                  {t('rms.common.noData')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
