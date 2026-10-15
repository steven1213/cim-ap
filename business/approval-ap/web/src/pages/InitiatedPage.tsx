import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import Panel from '@/components/Panel';
import DetailModal from '@/components/DetailModal';
import { IconOutbox, IconRefresh, IconShield, IconSearch, IconBell, IconUndo } from '@/components/Icons';
import { listInitiated, urgeInstance, withdrawInstance } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { toast } from '@/store/toastStore';
import { fmtDate, fmtOverdue } from '@/lib/format';
import type { ProcessInstanceView } from '@/types';

/** 实例状态 → 标签色。 */
const STATUS_TAG: Record<string, string> = {
  ACTIVE: 'info',
  COMPLETED: 'ok',
  WITHDRAWN: 'warn',
};

export default function InitiatedPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { userId } = useIdentityStore();
  const [list, setList] = useState<ProcessInstanceView[]>([]);
  const [loading, setLoading] = useState(true);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  /** 关键字过滤（标题/业务单号/流程 key） */
  const [q, setQ] = useState('');

  const filtered = list.filter((pi) => {
    if (!q.trim()) return true;
    const kw = q.trim().toLowerCase();
    return [pi.title, pi.businessKey, pi.processKey]
      .some((v) => v && v.toLowerCase().includes(kw));
  });

  async function load() {
    setLoading(true);
    try {
      setList(await listInitiated());
    } catch (e) {
      // 保留旧列表
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  async function onUrge(pi: ProcessInstanceView) {
    setBusyId(pi.processInstanceId);
    try {
      await urgeInstance(pi.processInstanceId);
      toast(t('init.urgeSent'), 'ok');
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusyId(null);
    }
  }

  async function onWithdraw(pi: ProcessInstanceView) {
    if (!window.confirm(t('init.withdrawConfirm', { bk: pi.businessKey }))) return;
    setBusyId(pi.processInstanceId);
    try {
      await withdrawInstance(pi.processInstanceId);
      toast(t('init.withdrawDone'), 'ok');
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="page">
      <Panel
        title={t('init.title')}
        sub={t('init.sub')}
        actions={
          <>
            <span className="search-inline">
              <IconSearch width={13} height={13} />
              <input
                className="text-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('common.searchPlaceholder')}
              />
            </span>
            <button className="icon-btn" onClick={load} title={t('common.refresh')} aria-label={t('common.refresh')}>
              <IconRefresh width={16} height={16} />
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('init.colBusinessKey')}</th>
                <th>{t('common.colTitle')}</th>
                <th>{t('init.colProcess')}</th>
                <th>{t('init.colStatus')}</th>
                <th>{t('init.colStarted')}</th>
                <th>{t('init.colEnded')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={7} className="dim" style={{ textAlign: 'center' }}>{t('common.loading')}</td>
                </tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={7}>
                    <div className="empty">
                      <IconOutbox width={26} height={26} />
                      <span>{t('common.empty')}</span>
                    </div>
                  </td>
                </tr>
              )}
              {filtered.map((pi) => (
                <tr key={pi.processInstanceId}>
                  <td className="mono">{pi.businessKey}</td>
                  <td>{pi.title || '—'}</td>
                  <td className="mono">{pi.processKey}</td>
                  <td>
                    <span className={'tag ' + (STATUS_TAG[pi.status] ?? '')}>
                      {t('init.status.' + pi.status)}
                    </span>
                    {pi.overdueMinutes != null && (
                      <span className="tag err" style={{ marginLeft: 6 }}>
                        {t('init.overdue')} {fmtOverdue(pi.overdueMinutes)}
                      </span>
                    )}
                  </td>
                  <td className="mono">{fmtDate(pi.startedAt)}</td>
                  <td className="mono">{fmtDate(pi.endedAt)}</td>
                  <td>
                    <div className="cell-actions">
                      <button className="btn btn-sm" onClick={() => setDetailId(pi.processInstanceId)}>
                        <IconSearch width={13} height={13} /> {t('common.detail')}
                      </button>
                      {pi.status === 'ACTIVE' && (
                        <>
                          <button
                            className="btn btn-ghost btn-sm"
                            disabled={busyId === pi.processInstanceId}
                            onClick={() => onUrge(pi)}
                          >
                            <IconBell width={13} height={13} /> {t('init.urge')}
                          </button>
                          <button
                            className="btn btn-ghost btn-sm btn-danger"
                            disabled={busyId === pi.processInstanceId}
                            onClick={() => onWithdraw(pi)}
                          >
                            <IconUndo width={13} height={13} /> {t('init.withdraw')}
                          </button>
                        </>
                      )}
                      <button className="btn btn-ghost btn-sm" onClick={() => navigate(`/audit?businessKey=${encodeURIComponent(pi.businessKey)}`)}>
                        <IconShield width={13} height={13} /> {t('init.viewAudit')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} />}
    </div>
  );
}
