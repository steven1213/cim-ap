import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import { useAuthStore } from '@/store/authStore';
import * as api from '@/lib/api';
import type { LoginResult, SaltResponse } from '@/types';
import LanguageSwitcher from '@/components/LanguageSwitcher';
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
 * <p>交互按常用登录习惯设计：记住用户名（仅本地保存用户名，不保存口令）、口令可见性切换、
 * Caps Lock 提示、回车提交、自动聚焦（已记住用户名时聚焦口令框）。</p>
 *
 * <p>文案全部走 i18n，且顶栏提供语言切换——登录页是**登录前**唯一的界面，
 * 译文包端点 `/api/i18n/**` 属公开端点（未认证可访问），故语言切换在这里同样可用。</p>
 */
export default function LoginPage() {
  const { t } = useTranslation();
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
  // SSO 回跳地址：RMS 等接入 ap 通过 /login?redirect=<ap>/sso 传入，
  // 登录成功后携带 token 跳回该地址（由接入 ap 的 /sso 回调落地）。
  const ssoRedirect = new URLSearchParams(location.search).get('redirect');
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
      // 接入 ap 发起的 SSO：携带 token 回跳其 /sso 回调（跨域，必须整页跳转）。
      if (ssoRedirect) {
        const sep = ssoRedirect.includes('?') ? '&' : '?';
        window.location.assign(
          `${ssoRedirect}${sep}token=${encodeURIComponent(res.token)}&refreshToken=${encodeURIComponent(res.refreshToken)}`,
        );
        return;
      }
      navigate('/');
    } catch (err: any) {
      setError(err?.msg || err?.message || t('iam.login.failed'));
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
          <span>{t('iam.login.brand')}</span>
        </div>

        <div className="login-hero">
          <h2>{t('iam.login.hero.title')}</h2>
          <p>{t('iam.login.hero.desc')}</p>
          <ul className="login-points">
            <li>
              <IconShield width={16} height={16} />
              {t('iam.login.point.rs256')}
            </li>
            <li>
              <IconKey width={16} height={16} />
              {t('iam.login.point.pbkdf2')}
            </li>
            <li>
              <IconApps width={16} height={16} />
              {t('iam.login.point.admission')}
            </li>
          </ul>
        </div>

        <div className="lg-foot">CIM-AP / IAM-AP · © 2026</div>
      </section>

      <section className="login-panel">
        <div className="login-lang">
          <LanguageSwitcher />
        </div>
        <div className="login-card">
          <h1>{t('iam.login.title')}</h1>
          <p className="lead">{t('iam.login.lead')}</p>

          <form onSubmit={onSubmit}>
            <div className="field">
              <label className="field-label" htmlFor="login-user">
                {t('iam.login.username')}
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
                {t('iam.login.password')}
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
                  placeholder={t('iam.login.passwordPlaceholder')}
                />
                <button
                  type="button"
                  className="pwd-toggle"
                  onClick={() => setShowPwd((v) => !v)}
                  title={showPwd ? t('iam.login.hidePwd') : t('iam.login.showPwd')}
                  aria-label={showPwd ? t('iam.login.hidePwd') : t('iam.login.showPwd')}
                  tabIndex={-1}
                >
                  {showPwd ? <IconEyeOff width={16} height={16} /> : <IconEye width={16} height={16} />}
                </button>
              </div>
              {capsOn && <p className="caps-hint">{t('iam.login.capsOn')}</p>}
            </div>

            <div className="login-row">
              <label className="check">
                <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
                {t('iam.login.remember')}
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
              {busy ? t('iam.login.submitting') : t('iam.login.submit')}
            </button>
          </form>

          <p className="hint">{t('iam.login.hint')}</p>
        </div>
      </section>
    </div>
  );
}
