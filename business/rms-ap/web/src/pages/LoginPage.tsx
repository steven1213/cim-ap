import { useTranslation } from 'react-i18next';
import { SsoLoginPage } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { sso } from '@/lib/sso';

/**
 * 登录入口 —— 页面与全部判断在 `@cim/shell` 的 {@link SsoLoginPage}，本文件只提供 RMS 的品牌与文案。
 *
 * <p><b>本页不是登录表单</b>：表单在 IAM（`8081/login`，由 IAM 后端直出的静态页），全仓只有那一处。
 * 页面里那三条容易漏的判断——「准入失败必须停住，否则 IAM 两端无限往返」「换账号前先清 SSO
 * Cookie」「已登录直接进首页」——都收在共用组件里，四个 ap 共用一份。</p>
 *
 * <p><b>为什么没有「重试」按钮</b>：准入没开通，重试必然拿到同一枚令牌、同一个 403。
 * 唯一的出路是换账号——而 IAM 的 SSO 会话还挂着，直接换会秒登回原账号，所以先吊销再回登录页。</p>
 */
export default function LoginPage() {
  const { t } = useTranslation();
  return (
    <SsoLoginPage
      mark="RM"
      brand="CIM RMS"
      brandSub={t('rms.shell.brandSub')}
      getToken={() => useAuthStore.getState().token}
      setSession={(token, refreshToken) => useAuthStore.getState().setSession(token, refreshToken)}
      redirect={sso.redirect}
      idpLogout={() => sso.idpLogout(null, null)}
      labels={{
        title: t('rms.login.title'),
        redirecting: t('rms.login.redirecting'),
        admissionDenied: t('rms.login.admissionDenied'),
        ssoFailed: t('rms.login.ssoFailed'),
        switchAccount: t('rms.login.switchAccount'),
        devPreview: t('rms.login.devPreview'),
        devApply: t('rms.login.devApply'),
      }}
    />
  );
}
