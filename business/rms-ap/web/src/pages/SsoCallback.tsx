import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';

/**
 * SSO 回跳接收：IAM 认证后将 token/refreshToken 经查询参数带回，
 * 此处写入 authStore 并跳首页。任何异常都回落到登录页重新发起。
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

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-brand">
          <div className="logo">RM</div>
          <div className="brand-title">CIM RMS</div>
        </div>
        <p>{t('rms.login.redirecting')}</p>
      </div>
    </div>
  );
}
