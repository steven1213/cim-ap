import { useEffect, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AdminUserRow } from '@/types';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { fmtDateTime } from '@/lib/format';
import Panel from '@/components/Panel';
import {
  IconAlert,
  IconBan,
  IconCheckCircle,
  IconKey,
  IconPlus,
  IconPower,
  IconRefresh,
  IconTrash,
  IconUsers,
} from '@/components/Icons';

/**
 * 用户账号管理（需 iam-ap:ADMIN）。
 *
 * <p>口令相关操作（创建 / 重置）沿用与登录一致的两层派生：浏览器内先做第一层 PBKDF2，
 * 仅上传 clientHash + 随机盐，明文口令不出浏览器。</p>
 */
export default function UsersPage() {
  const [rows, setRows] = useState<AdminUserRow[]>([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [nu, setNu] = useState('');
  const [np, setNp] = useState('');

  const [resetTarget, setResetTarget] = useState<string | null>(null);
  const [rp, setRp] = useState('');

  async function load() {
    setLoading(true);
    setErr('');
    try {
      setRows(await api.get<AdminUserRow[]>('/admin/users'));
    } catch (e: any) {
      setErr(e?.msg || '加载用户列表失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function flash(text: string) {
    setMsg(text);
    setTimeout(() => setMsg(''), 3000);
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      const clientSalt = randomClientSalt();
      const credential = await deriveClientHash(np, clientSalt);
      await api.post('/admin/users', { username: nu.trim(), credential, clientSalt });
      flash(`已创建账号 ${nu}`);
      setNu('');
      setNp('');
      setShowCreate(false);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '创建失败');
    } finally {
      setBusy(false);
    }
  }

  async function onToggleStatus(r: AdminUserRow) {
    setErr('');
    try {
      await api.put(`/admin/users/${encodeURIComponent(r.userId)}/status`, { enabled: !r.enabled });
      flash(`${r.username} 已${r.enabled ? '禁用（已强制下线）' : '启用'}`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '操作失败');
    }
  }

  async function onUnlock(r: AdminUserRow) {
    setErr('');
    try {
      const cleared = await api.post<boolean>(`/admin/users/${encodeURIComponent(r.userId)}/unlock`);
      flash(cleared ? `${r.username} 已解锁` : `${r.username} 无锁定记录`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '解锁失败');
    }
  }

  async function onReset(e: FormEvent) {
    e.preventDefault();
    if (!resetTarget) return;
    setErr('');
    setBusy(true);
    try {
      const clientSalt = randomClientSalt();
      const credential = await deriveClientHash(rp, clientSalt);
      await api.put(`/admin/users/${encodeURIComponent(resetTarget)}/password`, { credential, clientSalt });
      flash(`已重置 ${resetTarget} 的口令（该用户已强制下线）`);
      setResetTarget(null);
      setRp('');
      await load();
    } catch (e: any) {
      setErr(e?.msg || '重置失败');
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(r: AdminUserRow) {
    if (!window.confirm(`确认删除账号 ${r.username}？该操作不可恢复，其存量令牌将立即失效。`)) return;
    setErr('');
    try {
      await api.del(`/admin/users/${encodeURIComponent(r.userId)}`);
      flash(`已删除账号 ${r.username}`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '删除失败');
    }
  }

  const enabled = rows.filter((r) => r.enabled).length;
  const locked = rows.filter((r) => r.locked).length;

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
        title="账号清单"
        sub="本地账号（员工主身份由 AD/LDAP 托管；此处为无目录环境的本地账号与服务账号）"
        flush
        actions={
          <>
            <span className="tag">{rows.length} 条</span>
            <span className="tag ok">{enabled} 启用</span>
            {locked > 0 && <span className="tag err">{locked} 锁定</span>}
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
            <button className="btn-sm" onClick={() => setShowCreate((v) => !v)}>
              <IconPlus width={13} height={13} />
              新建账号
            </button>
          </>
        }
      >
        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>用户名</th>
                  <th style={{ width: 96 }}>状态</th>
                  <th style={{ width: 150 }}>登录锁定</th>
                  <th className="num" style={{ width: 88 }}>
                    准入
                  </th>
                  <th>角色组</th>
                  <th style={{ width: 300 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.userId}>
                    <td className="mono">{r.username}</td>
                    <td>
                      <span className="status">
                        <i className={'led ' + (r.enabled ? 'ok' : 'err')} />
                        <b>{r.enabled ? '启用' : '禁用'}</b>
                      </span>
                    </td>
                    <td>
                      {r.locked ? (
                        <span className="tag err" title={`锁定至 ${fmtDateTime(r.lockedUntil)}`}>
                          锁定至 {fmtDateTime(r.lockedUntil)}
                        </span>
                      ) : (
                        <span className="dim">正常</span>
                      )}
                    </td>
                    <td className="num">{r.apps?.length ?? 0}</td>
                    <td>
                      <div className="tag-list">
                        {Object.entries(r.roles ?? {})
                          .flatMap(([app, roles]) => roles.map((x) => `${app}:${x}`))
                          .map((x) => (
                            <span key={x} className="tag pri">
                              {x}
                            </span>
                          ))}
                        {!Object.keys(r.roles ?? {}).length && <span className="dim">—</span>}
                      </div>
                    </td>
                    <td>
                      <div className="cell-actions">
                        <button className="btn-ghost btn-sm" onClick={() => onToggleStatus(r)}>
                          {r.enabled ? <IconBan width={13} height={13} /> : <IconPower width={13} height={13} />}
                          {r.enabled ? '禁用' : '启用'}
                        </button>
                        {r.locked && (
                          <button className="btn-ghost btn-sm" onClick={() => onUnlock(r)}>
                            <IconKey width={13} height={13} />
                            解锁
                          </button>
                        )}
                        <button
                          className="btn-ghost btn-sm"
                          onClick={() => {
                            setResetTarget(r.userId);
                            setRp('');
                          }}
                        >
                          <IconKey width={13} height={13} />
                          重置口令
                        </button>
                        <button className="btn-danger btn-sm" onClick={() => onDelete(r)}>
                          <IconTrash width={13} height={13} />
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconUsers width={22} height={22} />
            <b>暂无本地账号</b>
            <span>点击右上「新建账号」创建第一个本地账号</span>
          </div>
        )}
      </Panel>

      {showCreate && (
        <Panel title="新建本地账号" sub="口令在浏览器内完成第一层 PBKDF2 派生后上传">
          <form onSubmit={onCreate}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="nu">
                  用户名
                </label>
                <input id="nu" className="mono" placeholder="operator01" value={nu} onChange={(e) => setNu(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="np">
                  初始口令
                </label>
                <input id="np" type="password" value={np} onChange={(e) => setNp(e.target.value)} autoComplete="new-password" />
              </div>
              <button type="submit" disabled={busy || !nu.trim() || !np}>
                {busy ? '创建中…' : '创建'}
              </button>
            </div>
          </form>
          <p className="hint">新建账号默认无任何准入，请在「准入授权」中为其分配应用与角色组。</p>
        </Panel>
      )}

      {resetTarget && (
        <Panel title={`重置口令 · ${resetTarget}`} sub="重置成功后该用户所有会话立即失效，需以新口令重登">
          <form onSubmit={onReset}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="rp">
                  新口令
                </label>
                <input id="rp" type="password" value={rp} onChange={(e) => setRp(e.target.value)} autoComplete="new-password" />
              </div>
              <button type="submit" disabled={busy || !rp}>
                {busy ? '提交中…' : '确认重置'}
              </button>
              <button type="button" className="btn-ghost" onClick={() => setResetTarget(null)}>
                取消
              </button>
            </div>
          </form>
        </Panel>
      )}

      <Panel title="使用说明" sub="账号生命周期">
        <ul className="note-list">
          <li>
            <b>入职</b>：新建账号 → 在「准入授权」分配可进入的 ap 与角色组 → 通知用户登录。
          </li>
          <li>
            <b>停用/离职</b>：禁用账号会自动 bump 令牌版本，其所有存量令牌立即失效（等同强制下线）。
          </li>
          <li>
            <b>口令遗忘</b>：重置口令（同样自动强制下线），或用户自助在「我的」中改密。
          </li>
          <li>
            <b>登录被锁</b>：连续失败达阈值会触发临时锁定，可在「登录锁定」页手动解锁。
          </li>
        </ul>
      </Panel>
    </div>
  );
}
