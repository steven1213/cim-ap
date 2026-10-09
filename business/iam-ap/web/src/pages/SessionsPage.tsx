import { useEffect, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { KickResult, SessionRow, TokenVersionBump } from '@/types';
import { fmtDateTime } from '@/lib/format';
import Panel from '@/components/Panel';
import {
  IconAlert,
  IconCheckCircle,
  IconKick,
  IconMonitor,
  IconRefresh,
} from '@/components/Icons';

/** 在线会话（需 iam-ap:ADMIN）：活跃会话列表 + 强制下线（bump 令牌版本）。 */
export default function SessionsPage() {
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
      setErr(e?.msg || '加载会话失败');
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
    if (!window.confirm(`确认强制 ${username} 下线？其所有已签发令牌将立即失效。`)) return;
    setErr('');
    try {
      const r = await api.del<KickResult>(`/admin/sessions/${encodeURIComponent(userId)}`);
      flash(`已强制 ${username} 下线，令牌版本 → ${r.version}`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '强制下线失败');
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
      setErr(e?.msg || e?.message || '操作失败');
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
        title="活跃会话"
        sub="以未撤销且未过期的刷新令牌近似表示一个登录会话"
        flush
        actions={
          <>
            <span className="tag">{rows.length} 个</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
          </>
        }
      >
        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>用户</th>
                  <th style={{ width: 180 }}>用户 ID</th>
                  <th style={{ width: 260 }}>访问令牌 jti</th>
                  <th style={{ width: 190 }}>到期时间</th>
                  <th style={{ width: 130 }}>操作</th>
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
                      <button className="btn-danger btn-sm" onClick={() => kick(r.userId, r.username)}>
                        <IconKick width={13} height={13} />
                        强制下线
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconMonitor width={22} height={22} />
            <b>当前无活跃会话</b>
            <span>用户登录后会在此出现</span>
          </div>
        )}
      </Panel>

      <Panel
        title="按用户 ID 强制下线"
        sub="bump 令牌版本，验证端进程内即时校验，无缓存滞后"
        actions={
          <span className="tag warn">
            <IconKick width={12} height={12} />
            高危操作
          </span>
        }
      >
        <form className="row" onSubmit={onManualBump}>
          <div className="field">
            <label className="field-label" htmlFor="kick-uid">
              用户 ID
            </label>
            <input
              id="kick-uid"
              className="mono"
              placeholder="admin（本地账号即用户名）"
              value={uid}
              onChange={(e) => setUid(e.target.value)}
            />
          </div>
          <button className="btn-danger" type="submit" disabled={busy || !uid}>
            {busy ? '处理中…' : '踢下线'}
          </button>
        </form>

        {bump && (
          <div className="alert ok" style={{ marginTop: 12 }}>
            <IconCheckCircle width={15} height={15} />
            <span>
              已 bump 用户 <b>{bump.uid}</b>，当前令牌版本号 <b>{bump.version}</b>
            </span>
          </div>
        )}

        <ul className="note-list" style={{ marginTop: 12 }}>
          <li>作用对象：该用户已签发的全部访问令牌与刷新令牌。</li>
          <li>生效方式：验证端比对令牌 <code>ver</code> claim 与库内版本号，不一致即返回 401。</li>
          <li>后续影响：用户需重新登录；令牌版本号单调递增，不回滚。</li>
        </ul>
      </Panel>
    </div>
  );
}
