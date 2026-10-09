import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { useAuthStore } from '@/store/authStore';
import * as api from '@/lib/api';
import type { LoginResult, SaltResponse } from '@/types';

/** 统一登录：取服务端盐 → 浏览器内第一层 PBKDF2 派生 → 提交 clientHash（明文口令不出浏览器）。 */
export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const loadMe = useAuthStore((s) => s.loadMe);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      // 1) 取服务端盐（防账号枚举：不存在返回空盐）
      const salt = await api.get<SaltResponse>('/login/salt', { username });
      const clientSalt = salt.clientSalt || randomClientSalt();
      // 2) 第一层派生（明文口令不出浏览器）
      const clientHash = await deriveClientHash(password, clientSalt);
      // 3) 登录
      const res = await api.post<LoginResult>('/login', {
        username,
        credential: clientHash,
        clientSalt,
      });
      setSession(res.token, res.refreshToken);
      await loadMe();
      navigate('/');
    } catch (err: any) {
      setError(err?.msg || err?.message || '登录失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={onSubmit}>
        <h2>IAM 统一登录</h2>
        <label>
          用户名
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          口令
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="err">{error}</div>}
        <button type="submit" disabled={busy || !username || !password}>
          {busy ? '登录中…' : '登录'}
        </button>
        <p className="hint">口令在浏览器内做第一层 PBKDF2 派生，明文不上传。</p>
      </form>
    </div>
  );
}
