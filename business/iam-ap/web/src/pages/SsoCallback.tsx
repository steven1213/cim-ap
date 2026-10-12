import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

/**
 * SSO 回跳接收（IAM 控制台自身也走统一登录页）。
 *
 * <p>IAM 控制台 /login 会整页中继到 IAM 后端（8081）的 /login；登录成功后后端把 token 经
 * 查询参数带回本页（{@code <origin>/sso-callback}），此处写入 authStore 并跳首页。
 * 与 RMS 的 /sso 回调同构，确保「所有客户端共用同一登录页」。</p>
 */
export default function SsoCallback() {
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
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ color: 'var(--muted, #7d8899)', fontSize: 13 }}>正在登录…</div>
    </div>
  );
}
