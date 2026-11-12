import { useTranslation } from 'react-i18next';
import { SsoCallbackPage } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';

/**
 * SSO 回跳接收（IAM 控制台自身也走统一登录页）。
 *
 * <p>IAM 控制台 `/login` 会整页中继到 IAM 后端（8081）的 `/login`；登录成功后后端把 token 经
 * 查询参数带回本页（{@code <origin>/sso-callback}）。逻辑与另外三端共用
 * （`@cim/shell` 的 {@link SsoCallbackPage}），差别只有回跳路径——IAM 是 `/sso-callback`，
 * 其余三端是 `/sso`。</p>
 */
export default function SsoCallback() {
  const { t } = useTranslation();
  return (
    <SsoCallbackPage
      mark="IA"
      brand="CIM IAM"
      label={t('iam.login.submitting')}
      setSession={(token, refreshToken) => useAuthStore.getState().setSession(token, refreshToken)}
    />
  );
}
