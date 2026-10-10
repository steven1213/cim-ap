import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { KickResult, SessionRow, TokenVersionBump } from '@/types';
import { fmtDateTime } from '@/lib/format';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { SESSION_KICK } from '@/lib/permCodes';
import {
  IconAlert,
  IconCheckCircle,
  IconKick,
  IconMonitor,
  IconRefresh,
} from '@/components/Icons';

/** 在线会话：活跃会话列表 + 强制下线（bump 令牌版本）。下线按钮按 `iam:session:kick` 显隐。 */
export default function SessionsPage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<SessionRow[]>([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [loading, setLoading] = useState(false);

  const [uid, setUid] = useState('');
  const [bump, setBump] = useState<TokenVersionBump | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      setRows(await api.get<SessionRow[]>('/admin/sessions'));
    } catch (e: any) {
      setErr(e?.msg || t('iam.sessions.errLoad'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  async function kick(userId: string, username: string) {
    if (!window.confirm(t('iam.sessions.confirm', { username }))) return;
    setErr('');
    try {
      const r = await api.del<KickResult>(`/admin/sessions/${encodeURIComponent(userId)}`);
      flash(t('iam.sessions.msgKicked', { username, version: r.version }));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.sessions.errKick'));
    }
  }

  async function onManualBump(e: FormEvent) {
    e.preventDefault();
    setBump(null);
    setErr('');
    setBusy(true);
    try {
      const r = await api.postRaw<TokenVersionBump>(`/internal/token-version/bump?uid=${encodeURIComponent(uid)}`);
      setBump(r);
      await load();
    } catch (e: any) {
      setErr(e?.msg || e?.message || t('iam.sessions.errOp'));
    } finally {
      setBusy(false);
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
        title={t('iam.sessions.title')}
        sub={t('iam.sessions.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.sessions.count', { n: rows.length })}</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.sessions.refreshing') : t('iam.sessions.refresh')}
            </button>
          </>
        }
      >
        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('iam.sessions.th.user')}</th>
                  <th style={{ width: 180 }}>{t('iam.sessions.th.userId')}</th>
                  <th style={{ width: 260 }}>{t('iam.sessions.th.jti')}</th>
                  <th style={{ width: 190 }}>{t('iam.sessions.th.expire')}</th>
                  <th style={{ width: 130 }}>{t('iam.sessions.th.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, i) => (
                  <tr key={`${r.userId}-${i}`}>
                    <td className="mono">{r.username}</td>
                    <td className="mono dim">{r.userId}</td>
                    <td className="mono dim">{r.accessTokenJti ?? '—'}</td>
                    <td className="mono">{fmtDateTime(r.expiresAt)}</td>
                    <td>
                      <Perms code={SESSION_KICK}>
                        <button className="btn-danger btn-sm" onClick={() => kick(r.userId, r.username)}>
                          <IconKick width={13} height={13} />
                          {t('iam.sessions.kick')}
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
            <IconMonitor width={22} height={22} />
            <b>{t('iam.sessions.emptyTitle')}</b>
            <span>{t('iam.sessions.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Perms code={SESSION_KICK}>
        <Panel
          title={t('iam.sessions.kickTitle')}
          sub={t('iam.sessions.kickSub')}
          actions={
            <span className="tag warn">
              <IconKick width={12} height={12} />
              {t('iam.sessions.highRisk')}
            </span>
          }
        >
          <form className="row" onSubmit={onManualBump}>
            <div className="field">
              <label className="field-label" htmlFor="kick-uid">
                {t('iam.sessions.f.userId')}
              </label>
              <input
                id="kick-uid"
                className="mono"
                placeholder={t('iam.sessions.userIdPH')}
                value={uid}
                onChange={(e) => setUid(e.target.value)}
              />
            </div>
            <button className="btn-danger" type="submit" disabled={busy || !uid}>
              {busy ? t('iam.sessions.processing') : t('iam.sessions.process')}
            </button>
          </form>


        {bump && (
          <div className="alert ok" style={{ marginTop: 12 }}>
            <IconCheckCircle width={15} height={15} />
            <span>
              {t('iam.sessions.bumpDone', { uid: bump.uid, version: bump.version })}
            </span>
          </div>
        )}

        <ul className="note-list" style={{ marginTop: 12 }}>
          <li>{t('iam.sessions.note1')}</li>
          <li>{t('iam.sessions.note2')}</li>
          <li>{t('iam.sessions.note3')}</li>
        </ul>
        </Panel>
      </Perms>
    </div>
  );
}
