import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import Panel from '@/components/Panel';
import { IconInbox, IconOutbox, IconFlow, IconCheck, IconWebhook } from '@/components/Icons';
import {
  listDefinitions,
  listTodo,
  listDone,
  listInitiated,
  queryOutbox,
} from '@/lib/api';
import { fmtDate } from '@/lib/format';

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
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      const [{ length: todo }, { length: done }, { length: defs }, { length: init }, outbox] =
        await Promise.all([
          listTodo(),
          listDone(),
          listDefinitions(),
          listInitiated(),
          queryOutbox(undefined, undefined),
        ]);
      const pending = outbox.filter((o) => o.status === 'PENDING').length;
      setKpis([
        { label: t('dash.kpi.todo'), value: todo, icon: <IconInbox width={16} height={16} />, to: '/todo', tone: 'info' },
        { label: t('dash.kpi.initiated'), value: init, icon: <IconOutbox width={16} height={16} />, to: '/initiated' },
        { label: t('dash.kpi.definitions'), value: defs, icon: <IconFlow width={16} height={16} />, to: '/definitions' },
        { label: t('dash.kpi.done'), value: done, icon: <IconCheck width={16} height={16} />, to: '/done' },
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
