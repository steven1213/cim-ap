import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { SsoRelay } from '@cim/shell';
import { CimButton, CimInput, InlineAlert } from '@cim/ui';
import { useAuthStore } from '@/store/authStore';
import { idpLogout, ssoLoginRedirect } from '@/lib/sso';

/**
 * 登录入口（SSO 中继页）。
 *
 * <p><b>本页不是登录表单</b>。表单在 IAM（`8081/login`，由 IAM 后端直出的静态页），
 * 全仓只有那一处。这里只做两件事：未登录时整页中继过去；把 IAM 的拒绝原因讲清楚。</p>
 *
 * <p><b>为何自动跳转</b>：用户访问任意受保护路由 → `ProtectedRoute` 无令牌即跳到这里；
 * 若再让用户点一次「前往登录」，等于多一次无意义点击。</p>
 *
 * <p><b>准入失败（`?error=admission`）必须停住</b>：此时 IAM 侧会话仍在，若继续自动中继，
 * IAM 会拿同一枚令牌再弹回 `/sso`，两端无限往返。故 `error` 存在时不跳转。</p>
 *
 * <p><b>为什么这里没有「重试」按钮</b>：准入没开通，重试必然拿到同一枚令牌、同一个 403。
 * 唯一的出路是换账号——而 IAM 的 SSO 会话还挂着，直接换会秒登回原账号，所以先
 * {@link idpLogout} 清掉 SSO Cookie 再回登录页。</p>
 */
const AUTO_REDIRECT = (import.meta.env.VITE_SSO_AUTO_REDIRECT ?? 'true') !== 'false';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const error = params.get('error');
  const setSession = useAuthStore((s) => s.setSession);
  const [devToken, setDevToken] = useState('');
  const [switching, setSwitching] = useState(false);
  const isDev = import.meta.env.DEV;
  const autoRelay = AUTO_REDIRECT && !error;

  useEffect(() => {
    // 已登录直接进系统
    if (useAuthStore.getState().token) {
      navigate('/', { replace: true });
      return;
    }
    if (autoRelay) ssoLoginRedirect('APPROVAL');
  }, [navigate, autoRelay]);

  function applyDevToken() {
    if (!devToken.trim()) return;
    // 开发态：直接以传入的 JWT 作为 access/refresh（仅本地预览用）
    setSession(devToken.trim(), devToken.trim());
    navigate('/', { replace: true });
  }

  async function switchAccount() {
    setSwitching(true);
    // 准入失败时令牌已被 BootGate 清掉，这里没有可吊销的令牌；
    // 该请求的作用只有一个：让 IAM 清掉 SSO 会话 Cookie。
    await idpLogout(null, null);
    ssoLoginRedirect('APPROVAL');
  }

  return (
    <SsoRelay
      mark="AP"
      brand="CIM Approval"
      brandSub={t('shell.brandSub')}
      label={error ? t('login.title') : t('login.redirecting')}
      busy={!error}
    >
      {error && (
        <>
          <InlineAlert tone="err">
            {error === 'admission' ? t('login.admissionDenied') : t('login.ssoFailed')}
          </InlineAlert>
          {error === 'admission' && (
            <CimButton block disabled={switching} onClick={switchAccount}>
              {t('login.switchAccount')}
            </CimButton>
          )}
        </>
      )}

      {isDev && (
        <div className="sso-relay-dev">
          <div className="sso-relay-dev-cap">DEV 预览（粘贴 IAM 签发的 JWT）</div>
          <CimInput rows={3} placeholder="eyJ..." value={devToken} onChange={setDevToken} />
          <CimButton block onClick={applyDevToken}>
            以本地令牌进入
          </CimButton>
        </div>
      )}
    </SsoRelay>
  );
}
