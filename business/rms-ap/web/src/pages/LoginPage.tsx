import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';

/**
 * 统一登录入口（SSO 中继页）。
 *
 * <p><b>自动跳转（默认）</b>：未登录时本页挂载即整页跳转至 IAM 统一登录页（{@code VITE_IAM_WEB_BASE}，
 * 缺省 http://localhost:5171，即 IAM 前端开发服务器；生产填 IAM 前端域名）。此处指向 IAM <b>前端</b>
 * （承载 {@code /login} 页面），不是 IAM 后端（8081 只有 {@code /api/v1/login} 接口，无 GET 页面）。
 * 跳转携带 `app=rms-ap` 与回跳地址；IAM 完成认证后重定向回 `{origin}/sso?token=...&refreshToken=...`，
 * 由 {@link SsoCallback} 接管写库。</p>
 *
 * <p><b>为何自动而非「点击按钮」</b>：用户访问任意受保护路由 → {@code ProtectedRoute} 无 token 即
 * 跳到这里；若再让用户点一次「前往登录」才跳转，等于多一次无意义点击。故本页默认直接中继到 IAM，
 * 不再停留等点击。这是<b>所有接入 ap（client-ap）的统一范式</b>，MDS/MES 等同理。</p>
 *
 * <p><b>开发回退</b>：仅 DEV 模式提供「粘贴令牌」入口，便于 IAM 不可达时本地预览 UI；
 * 该入口不出现在生产构建。若 IAM 不可达且需留在当前页手动操作，可设
 * {@code VITE_SSO_AUTO_REDIRECT=false} 关闭自动跳转（此时渲染手动按钮）。</p>
 */
const IAM_BASE = import.meta.env.VITE_IAM_WEB_BASE || import.meta.env.VITE_IAM_BASE || 'http://localhost:5171';
// 默认开启自动跳转：未登录直接中继到 IAM 统一登录，避免多一次点击。
const AUTO_REDIRECT = (import.meta.env.VITE_SSO_AUTO_REDIRECT ?? 'true') !== 'false';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [devToken, setDevToken] = useState('');
  const isDev = import.meta.env.DEV;

  useEffect(() => {
    // 已登录直接进系统
    if (useAuthStore.getState().token) {
      navigate('/', { replace: true });
      return;
    }
    // 未登录：自动中继到 IAM 统一登录（生产/开发默认开启）。
    // 仅当显式 VITE_SSO_AUTO_REDIRECT=false（如 IAM 不可达的纯本地预览）才停留在当前页。
    if (AUTO_REDIRECT) {
      const redirect = encodeURIComponent(window.location.origin + '/sso');
      window.location.href = `${IAM_BASE}/login?app=rms-ap&redirect=${redirect}`;
    }
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

        {AUTO_REDIRECT ? (
          <p className="login-hint-text">{t('rms.login.redirecting')}</p>
        ) : (
          <button className="btn btn-primary btn-block" onClick={goSso}>
            {t('rms.login.redirecting')}
          </button>
        )}

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
            {!AUTO_REDIRECT && (
              <button className="btn btn-ghost btn-block" onClick={goSso}>
                改用 IAM 登录
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
