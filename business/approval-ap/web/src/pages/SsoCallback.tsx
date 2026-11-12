import { useTranslation } from 'react-i18next';
import { SsoCallbackPage } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';

/**
 * SSO 回跳接收：IAM 认证后将 token / refreshToken 经查询参数带回，
 * 此处写入 authStore 并跳首页。逻辑与四个 ap 共用（`@cim/shell` 的 {@link SsoCallbackPage}）。
 *
 * <p>档案与身份不在这里拉：`BootGate` 会在渲染外壳前统一拉 `/me` 并写入 `identityStore`，
 * 两处都拉会多一次往返（首屏正是最敏感的时候）。</p>
 */
export default function SsoCallback() {
  const { t } = useTranslation();
  return (
    <SsoCallbackPage
      mark="AP"
      brand="CIM Approval"
      label={t('login.redirecting')}
      setSession={(token, refreshToken) => useAuthStore.getState().setSession(token, refreshToken)}
    />
  );
}
