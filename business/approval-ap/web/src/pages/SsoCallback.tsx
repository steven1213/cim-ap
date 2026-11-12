import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { SsoRelay } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';

/**
 * SSO 回跳接收：IAM 认证后将 token/refreshToken 经查询参数带回，
 * 此处写入 authStore 并跳首页。任何异常都回落到登录页重新发起。
 *
 * <p>档案与身份不在这里拉：`BootGate` 会在渲染外壳前统一拉 `/me` 并写入 `identityStore`，
 * 两处都拉会多一次往返（首屏正是最敏感的时候）。</p>
 */
export default function SsoCallback() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [params] = useSearchParams();

  useEffect(() => {
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');
    if (token) {
      setSession(token, refreshToken || token);
      navigate('/', { replace: true });
    } else {
      navigate('/login', { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <SsoRelay mark="AP" brand="CIM Approval" label={t('login.redirecting')} />;
}
