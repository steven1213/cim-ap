import { useState, type FormEvent } from 'react';
import { useAuthStore } from '@/store/authStore';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import * as api from '@/lib/api';
import type { ChangePasswordRequest, SaltResponse } from '@/types';

/**
 * 自助改密：与登录一致，浏览器内先对旧/新口令做第一层 PBKDF2 派生，仅传 clientHash
 * （明文口令不出浏览器）。旧口令派生需先取服务端盐，新口令由客户端生成随机盐后派生。
 */
export default function ProfilePage() {
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const username = useAuthStore((s) => s.user?.username);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setMsg('');
    setErr('');
    if (!username) {
      setErr('当前会话缺失用户名，请重新登录');
      return;
    }
    setBusy(true);
    try {
      // 1) 取服务端盐（旧口令第一层派生所需）
      const salt = await api.get<SaltResponse>('/login/salt', { username });
      const oldClientSalt = salt.clientSalt || randomClientSalt();
      const oldCredential = await deriveClientHash(oldPassword, oldClientSalt);
      // 2) 新口令：客户端生成随机盐后做第一层派生
      const newClientSalt = randomClientSalt();
      const newCredential = await deriveClientHash(newPassword, newClientSalt);
      // 3) 提交（仅传 clientHash，明文口令不出浏览器）
      const body: ChangePasswordRequest = { oldCredential, newCredential, newClientSalt };
      await api.post<void>('/me/password', body);
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
          口令在浏览器内做第一层 PBKDF2 派生，明文不上传（与登录一致）。
        </p>
      </form>
    </div>
  );
}
