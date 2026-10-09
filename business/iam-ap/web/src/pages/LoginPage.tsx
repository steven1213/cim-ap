import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { useAuthStore } from '@/store/authStore';
import * as api from '@/lib/api';
import type { LoginResult, SaltResponse } from '@/types';
import {
  IconAlert,
  IconApps,
  IconCheckCircle,
  IconEye,
  IconEyeOff,
  IconKey,
  IconShield,
} from '@/components/Icons';

const REMEMBER_KEY = 'iam-last-user';

/**
 * 统一登录：取服务端盐 → 浏览器内第一层 PBKDF2 派生 → 提交 clientHash（明文口令不出浏览器）。
 *
 * 交互按常用登录习惯设计：
 * - 记住用户名（仅本地保存用户名，不保存口令）
 * - 口令可见性切换、Caps Lock 提示
 * - 回车提交、自动聚焦（已记住用户名时聚焦口令框）
 */
export default function LoginPage() {
  const [username, setUsername] = useState(() => localStorage.getItem(REMEMBER_KEY) ?? '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => !!localStorage.getItem(REMEMBER_KEY));
  const [showPwd, setShowPwd] = useState(false);
  const [capsOn, setCapsOn] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const pwdRef = useRef<HTMLInputElement>(null);
  const userRef = useRef<HTMLInputElement>(null);

  const navigate = useNavigate();
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;
  const setSession = useAuthStore((s) => s.setSession);
  const loadMe = useAuthStore((s) => s.loadMe);

  // 已记住用户名则直接聚焦口令框，省去一次点击
  useEffect(() => {
    if (localStorage.getItem(REMEMBER_KEY)) pwdRef.current?.focus();
    else userRef.current?.focus();
  }, []);

  function detectCaps(e: KeyboardEvent<HTMLInputElement>) {
    if (typeof e.getModifierState === 'function') setCapsOn(e.getModifierState('CapsLock'));
  }

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
      // 记住用户名（仅用户名；口令从不落盘）
      if (remember) localStorage.setItem(REMEMBER_KEY, username);
      else localStorage.removeItem(REMEMBER_KEY);
      setPassword('');
      setSession(res.token, res.refreshToken);
      await loadMe();
      navigate('/');
    } catch (err: any) {
      setError(err?.msg || err?.message || '登录失败');
      pwdRef.current?.focus();
      pwdRef.current?.select();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <section className="login-visual">
        <div className="lg-brand">
          <div className="logo">IA</div>
          <span>CIM · 统一身份与准入</span>
        </div>

        <div className="login-hero">
          <h2>一次登录，通达各业务系统</h2>
          <p>
            企业内统一身份认证与跨业务准入控制中枢。令牌由 IAM 签发，各业务系统本地验签并据此判定准入。
          </p>
          <ul className="login-points">
            <li>
              <IconShield width={16} height={16} />
              RS256 令牌签发 · JWKS 公钥分发 · 业务侧本地验签
            </li>
            <li>
              <IconKey width={16} height={16} />
              口令两层派生（PBKDF2），明文口令不出浏览器
            </li>
            <li>
              <IconApps width={16} height={16} />
              应用准入注册 · 用户角色分配 · 令牌即时踢下线
            </li>
          </ul>
        </div>

        <div className="lg-foot">CIM-AP / IAM-AP · © 2026</div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <h1>登录</h1>
          <p className="lead">请输入企业账号以进入 IAM 控制台</p>

          <form onSubmit={onSubmit}>
            <div className="field">
              <label className="field-label" htmlFor="login-user">
                用户名
              </label>
              <input
                id="login-user"
                ref={userRef}
                className="mono"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                placeholder="admin"
              />
            </div>

            <div className="field">
              <label className="field-label" htmlFor="login-pwd">
                口令
              </label>
              <div className="pwd-wrap">
                <input
                  id="login-pwd"
                  ref={pwdRef}
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onKeyUp={detectCaps}
                  onKeyDown={detectCaps}
                  autoComplete="current-password"
                  placeholder="请输入口令"
                />
                <button
                  type="button"
                  className="pwd-toggle"
                  onClick={() => setShowPwd((v) => !v)}
                  title={showPwd ? '隐藏口令' : '显示口令'}
                  aria-label={showPwd ? '隐藏口令' : '显示口令'}
                  tabIndex={-1}
                >
                  {showPwd ? <IconEyeOff width={16} height={16} /> : <IconEye width={16} height={16} />}
                </button>
              </div>
              {capsOn && <p className="caps-hint">大写锁定（Caps Lock）已开启</p>}
            </div>

            <div className="login-row">
              <label className="check">
                <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
                记住用户名
              </label>
            </div>

            {error && (
              <div className="alert err">
                <IconAlert width={15} height={15} />
                <span>{error}</span>
              </div>
            )}
            {!error && notice && (
              <div className="alert ok">
                <IconCheckCircle width={15} height={15} />
                <span>{notice}</span>
              </div>
            )}

            <button type="submit" disabled={busy || !username || !password}>
              {busy ? '登录中…' : '登录'}
            </button>
          </form>

          <p className="hint">口令在浏览器内完成第一层 PBKDF2 派生，明文不会上传到服务端。</p>
        </div>
      </section>
    </div>
  );
}
