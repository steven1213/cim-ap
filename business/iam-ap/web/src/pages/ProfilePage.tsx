import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import * as api from '@/lib/api';
import type { ChangePasswordRequest, SaltResponse } from '@/types';

/**
 * 自助改密：与登录一致，浏览器内先对旧/新口令做第一层 PBKDF2 派生，仅传 clientHash
 * （明文口令不出浏览器）。旧口令派生需先取服务端盐，新口令由客户端生成随机盐后派生。
 *
 * 提交成功后，服务端已 bump 该用户的令牌版本，当前会话令牌随即失效（下次请求将被验证端判 401）。
 * 因此此处主动清空本地会话并跳转登录页，强制以新口令重新登录，避免停留在已失效的会话里。
 */
export default function ProfilePage() {
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const username = useAuthStore((s) => s.user?.username);
  const clearSession = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
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
      // 服务端已 bump 令牌版本 → 当前令牌失效，主动清态并重登
      clearSession();
      navigate('/login', { state: { notice: '口令已更新，请重新登录' } });
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
