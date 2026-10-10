import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import * as api from '@/lib/rmsApi';
import type { Recipe, Device } from '@/types';
import { fmtDateTime } from '@/lib/format';

export default function DashboardPage() {
  const { t } = useTranslation();
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    Promise.all([api.listRecipes(), api.listDevices()])
      .then(([r, d]) => {
        if (!alive) return;
        setRecipes(r);
        setDevices(d);
      })
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, []);

  const activeCount = recipes.filter((r) => r.activeVersionId).length;
  const typeCount = new Set(devices.map((d) => d.deviceTypeId).filter(Boolean)).size;

  const kpis = [
    { label: t('rms.dashboard.recipeCount'), value: recipes.length, to: '/recipes' },
    { label: t('rms.dashboard.activeCount'), value: activeCount, to: '/recipes' },
    { label: t('rms.dashboard.deviceCount'), value: devices.length, to: '/devices' },
    { label: t('rms.dashboard.typeCount'), value: typeCount, to: '/device-types' },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.dashboard.title')}</h1>
      </div>

      <div className="kpi-grid">
        {kpis.map((k) => (
          <Link key={k.label} to={k.to} className="kpi">
            <div className="kpi-val">{loading ? '—' : k.value}</div>
            <div className="kpi-label">{k.label}</div>
          </Link>
        ))}
      </div>

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
              <th>{t('rms.recipe.deviceType')}</th>
              <th>{t('rms.recipe.status')}</th>
              <th>{t('rms.recipe.activeVersion')}</th>
              <th>{t('rms.common.status')}</th>
            </tr>
          </thead>
          <tbody>
            {recipes.slice(0, 8).map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.code}</td>
                <td>{r.name}</td>
                <td>{r.deviceTypeId ?? t('rms.common.dash')}</td>
                <td>{r.golden ? <span className="tag pri">{t('rms.recipe.golden')}</span> : t('rms.common.dash')}</td>
                <td className="mono">{r.activeVersionNo ?? t('rms.common.dash')}</td>
                <td>{fmtDateTime(r.updateTime)}</td>
              </tr>
            ))}
            {!loading && recipes.length === 0 && (
              <tr>
                <td colSpan={6} className="empty-cell">
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
