import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimButton, CimSpin, EmptyState, Page } from '@cim/ui';
import { IconWeCom } from '@/components/Icons';
import { verifyWeComToken } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';

/**
 * 企微免登回调页（G8 / D9）：企微内置浏览器携带签名令牌打开本页，
 * 后端核销通过后免登录直接进入办理（身份落入 identityStore，dev 态即生效）。
 *
 * <p>本页**不在外壳内容区**里正常浏览（用户是被企微直接甩进来的），所以只保留
 * 一个居中的状态卡：核验中 / 通过跳转 / 失败给原因与出口。</p>
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
    let timer: ReturnType<typeof setTimeout> | undefined;
    verifyWeComToken(token)
      .then((r) => {
        setIdentity(r.userId, 'default');
        setState('ok');
        // 免登进入待办列表（后续可深化为直达办理弹窗）
        timer = setTimeout(() => navigate('/todo'), 600);
      })
      .catch((e: { message?: string }) => {
        setState('error');
        setMessage(e?.message || t('wecom.invalidToken'));
      });
    // 卸载时清掉跳转定时器：用户已离开就不该再被导航走
    return () => {
      if (timer) clearTimeout(timer);
    };
  }, [params, setIdentity, navigate, t]);

  return (
    <Page className="wecom-callback">
      <div className="wecom-mark">
        <IconWeCom width={40} height={40} />
      </div>
      {state === 'loading' && (
        <div className="wecom-body">
          <CimSpin />
          <p className="wecom-hint">{t('wecom.verifying')}</p>
        </div>
      )}
      {state === 'ok' && (
        <div className="wecom-body">
          <p className="wecom-ok">{t('wecom.loginOk')}</p>
        </div>
      )}
      {state === 'error' && (
        <div className="wecom-body">
          <EmptyState title={t('wecom.failed')} hint={message} />
          <CimButton size="small" onClick={() => navigate('/')}>
            {t('common.back')}
          </CimButton>
        </div>
      )}
    </Page>
  );
}
