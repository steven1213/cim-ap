import { useEffect, useState } from 'react';
import * as api from '@/lib/api';
import type { LockRow } from '@/types';
import { fmtDateTime } from '@/lib/format';
import Panel from '@/components/Panel';
import { IconAlert, IconCheckCircle, IconKey, IconLock, IconRefresh } from '@/components/Icons';

/** 登录锁定管理（需 iam-ap:ADMIN）：列出被锁账号并支持手动解锁。 */
export default function LockoutsPage() {
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
      setErr(e?.msg || '加载锁定列表失败');
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
      setMsg(cleared ? `已解锁 ${username}` : `${username} 无锁定记录`);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '解锁失败');
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
        title="锁定中的账号"
        sub="连续登录失败达阈值触发的临时锁定；到期自动解除，也可手动解锁"
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
                  <th>用户名</th>
                  <th className="num" style={{ width: 110 }}>
                    失败次数
                  </th>
                  <th style={{ width: 180 }}>首次失败</th>
                  <th style={{ width: 180 }}>最近失败</th>
                  <th style={{ width: 180 }}>锁定至</th>
                  <th style={{ width: 110 }}>操作</th>
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
                      <button className="btn-ghost btn-sm" onClick={() => onUnlock(r.username)}>
                        <IconKey width={13} height={13} />
                        解锁
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconLock width={22} height={22} />
            <b>当前无锁定账号</b>
            <span>登录失败达阈值后会自动出现在此处</span>
          </div>
        )}
      </Panel>

      <Panel title="锁定策略" sub="滑动窗口内累计失败触发">
        <ul className="note-list">
          <li>窗口内连续失败达阈值即锁定；窗口过后历史失败清零（滑动窗口）。</li>
          <li>登录成功会立即清零计数，不会因成功登录而长期锁定。</li>
          <li>锁定期间即使口令正确也会被拒（不区分用户是否存在，防账号枚举）。</li>
          <li>策略参数（阈值 / 锁定时长 / 窗口）见「系统设置」。</li>
        </ul>
      </Panel>
    </div>
  );
}
