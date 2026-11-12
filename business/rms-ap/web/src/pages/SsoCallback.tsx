import { useTranslation } from 'react-i18next';
import { SsoCallbackPage } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';

/**
 * SSO 回跳接收：IAM 认证后将 token / refreshToken 经查询参数带回，
 * 此处写入 authStore 并跳首页。逻辑与四个 ap 共用（`@cim/shell` 的 {@link SsoCallbackPage}）。
 */
export default function SsoCallback() {
  const { t } = useTranslation();
  return (
    <SsoCallbackPage
      mark="RM"
      brand="CIM RMS"
      label={t('rms.login.redirecting')}
      setSession={(token, refreshToken) => useAuthStore.getState().setSession(token, refreshToken)}
    />
  );
}
