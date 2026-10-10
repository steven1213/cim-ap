import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';

/**
 * 统一登录入口。
 *
 * <p><b>生产路径</b>：跳转至 IAM 统一认证中心（{@code VITE_IAM_BASE}，缺省 http://localhost:8081），
 * 携带 `app=rms-ap` 与回跳地址；IAM 完成认证后重定向回 `{origin}/sso?token=...&refreshToken=...`，
 * 由 {@link SsoCallback} 接管写库。</p>
 *
 * <p><b>开发回退</b>：仅 DEV 模式提供「粘贴令牌」入口，便于未拉起 IAM 时本地预览 UI；
 * 该入口不出现在生产构建。</p>
 */
const IAM_BASE = import.meta.env.VITE_IAM_BASE || 'http://localhost:8081';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [devToken, setDevToken] = useState('');
  const isDev = import.meta.env.DEV;

  useEffect(() => {
    // 已登录直接进系统
    if (useAuthStore.getState().token) navigate('/', { replace: true });
  }, [navigate]);

  function goSso() {
    const redirect = encodeURIComponent(window.location.origin + '/sso');
    window.location.href = `${IAM_BASE}/login?app=rms-ap&redirect=${redirect}`;
  }

  function applyDevToken() {
    if (!devToken.trim()) return;
    // 开发态：直接以传入的 JWT 作为 access/refresh（仅本地预览用）
    setSession(devToken.trim(), devToken.trim());
    navigate('/', { replace: true });
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-brand">
          <div className="logo">RM</div>
          <div>
            <div className="brand-title">CIM RMS</div>
            <div className="brand-sub">{t('rms.shell.brandSub')}</div>
          </div>
        </div>
        <h1>{t('rms.login.title')}</h1>
        <button className="btn btn-primary btn-block" onClick={goSso}>
          {t('rms.login.redirecting')}
        </button>

        {isDev && (
          <div className="login-dev">
            <div className="login-dev-cap">DEV 预览（粘贴 IAM 签发的 JWT）</div>
            <textarea
              className="text-input"
              rows={3}
              placeholder="eyJ..."
              value={devToken}
              onChange={(e) => setDevToken(e.target.value)}
            />
            <button className="btn btn-ghost btn-block" onClick={applyDevToken}>
              以本地令牌进入
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
