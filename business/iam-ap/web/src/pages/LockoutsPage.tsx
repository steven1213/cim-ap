import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { LockRow } from '@/types';
import { fmtDateTime } from '@/lib/format';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { USER_UNLOCK } from '@/lib/permCodes';
import { IconAlert, IconCheckCircle, IconKey, IconLock, IconRefresh } from '@/components/Icons';

/** 登录锁定管理：列出被锁账号并支持手动解锁（解锁按钮按 `iam:user:unlock` 显隐）。 */
export default function LockoutsPage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<LockRow[]>([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      setRows(await api.get<LockRow[]>('/admin/lockouts'));
    } catch (e: any) {
      setErr(e?.msg || t('iam.lockouts.errLoad'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function onUnlock(username: string) {
    setErr('');
    try {
      const cleared = await api.del<boolean>(`/admin/lockouts/${encodeURIComponent(username)}`);
      setMsg(cleared ? t('iam.lockouts.msgUnlocked', { user: username }) : t('iam.lockouts.msgNoLock', { user: username }));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.lockouts.errUnlock'));
    }
  }

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {msg && (
        <div className="alert ok">
          <IconCheckCircle width={15} height={15} />
          <span>{msg}</span>
        </div>
      )}

      <Panel
        title={t('iam.lockouts.title')}
        sub={t('iam.lockouts.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.lockouts.count', { n: rows.length })}</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.lockouts.refreshing') : t('iam.lockouts.refresh')}
            </button>
          </>
        }
      >
        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('iam.lockouts.th.username')}</th>
                  <th className="num" style={{ width: 110 }}>
                    {t('iam.lockouts.th.failures')}
                  </th>
                  <th style={{ width: 180 }}>{t('iam.lockouts.th.first')}</th>
                  <th style={{ width: 180 }}>{t('iam.lockouts.th.last')}</th>
                  <th style={{ width: 180 }}>{t('iam.lockouts.th.until')}</th>
                  <th style={{ width: 110 }}>{t('iam.lockouts.th.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.username}>
                    <td className="mono">{r.username}</td>
                    <td className="num">{r.failCount}</td>
                    <td className="mono dim">{fmtDateTime(r.firstFailAt)}</td>
                    <td className="mono dim">{fmtDateTime(r.lastFailAt)}</td>
                    <td className="mono">{fmtDateTime(r.lockedUntil)}</td>
                    <td>
                      <Perms code={USER_UNLOCK}>
                        <button className="btn-ghost btn-sm" onClick={() => onUnlock(r.username)}>
                          <IconKey width={13} height={13} />
                          {t('iam.lockouts.unlock')}
                        </button>
                      </Perms>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconLock width={22} height={22} />
            <b>{t('iam.lockouts.emptyTitle')}</b>
            <span>{t('iam.lockouts.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Panel title={t('iam.lockouts.policyTitle')} sub={t('iam.lockouts.policySub')}>
        <ul className="note-list">
          <li>{t('iam.lockouts.policy1')}</li>
          <li>{t('iam.lockouts.policy2')}</li>
          <li>{t('iam.lockouts.policy3')}</li>
          <li>{t('iam.lockouts.policy4')}</li>
        </ul>
      </Panel>
    </div>
  );
}
