import { useState, type FormEvent } from 'react';
import * as api from '@/lib/api';

/** 自助改密。注意：当前 /me/password 按服务端契约接收明文口令（与登录的第一层派生不一致，待后端优化）。 */
export default function ProfilePage() {
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setMsg('');
    setErr('');
    setBusy(true);
    try {
      await api.post<void>('/me/password', { oldPassword, newPassword });
      setMsg('口令已更新');
      setOld('');
      setNew('');
    } catch (e: any) {
      setErr(e?.msg || e?.message || '改密失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card">
      <h2>修改口令</h2>
      <form onSubmit={onSubmit}>
        <label>
          原口令
          <input type="password" value={oldPassword} onChange={(e) => setOld(e.target.value)} />
        </label>
        <label>
          新口令
          <input type="password" value={newPassword} onChange={(e) => setNew(e.target.value)} />
        </label>
        {err && <div className="err">{err}</div>}
        {msg && <div className="ok">{msg}</div>}
        <button type="submit" disabled={busy || !oldPassword || !newPassword}>
          提交
        </button>
        <p className="hint">
          注意：当前改密接口按服务端契约接收明文口令（与登录的第一层派生不一致，已在后端列为待优化项）。
        </p>
      </form>
    </div>
  );
}
