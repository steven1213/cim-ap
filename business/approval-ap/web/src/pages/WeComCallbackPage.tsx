import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { verifyWeComToken } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { IconWeCom } from '@/components/Icons';

/**
 * 企微免登回调页（G8 / D9）：企微内置浏览器携带签名令牌打开本页，
 * 后端核销通过后免登录直接进入办理（身份落入 identityStore，dev 态即生效）。
 */
export default function WeComCallbackPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const setIdentity = useIdentityStore((s) => s.setIdentity);
  const [state, setState] = useState<'loading' | 'ok' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setState('error');
      setMessage(t('wecom.invalidToken'));
      return;
    }
    verifyWeComToken(token)
      .then((r) => {
        setIdentity(r.userId, 'default');
        setState('ok');
        // 免登进入待办列表（后续可深化为直达办理弹窗）
        setTimeout(() => navigate('/todo'), 600);
      })
      .catch((e: { message?: string }) => {
        setState('error');
        setMessage(e?.message || t('wecom.invalidToken'));
      });
  }, [params, setIdentity, navigate, t]);

  return (
    <div style={{ maxWidth: 420, margin: '12vh auto', textAlign: 'center', padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 12, color: 'var(--primary)' }}>
        <IconWeCom width={40} height={40} />
      </div>
      {state === 'loading' && <p>{t('wecom.verifying')}</p>}
      {state === 'ok' && <p style={{ color: 'var(--ok)' }}>{t('wecom.loginOk')}</p>}
      {state === 'error' && (
        <div>
          <p style={{ color: 'var(--err)' }}>{t('wecom.failed')}</p>
          <p className="muted" style={{ fontSize: 12 }}>{message}</p>
          <button className="btn btn-sm" style={{ marginTop: 10 }} onClick={() => navigate('/')}>
            {t('common.back')}
          </button>
        </div>
      )}
    </div>
  );
}
