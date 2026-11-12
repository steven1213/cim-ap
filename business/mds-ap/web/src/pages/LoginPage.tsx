import { useTranslation } from 'react-i18next';
import { SsoLoginPage } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { sso } from '@/lib/sso';

/**
 * 登录入口 —— 页面与全部判断在 `@cim/shell` 的 {@link SsoLoginPage}，本文件只提供 MDS 的品牌与文案。
 *
 * <p><b>本页不是登录表单</b>：表单在 IAM（`8081/login`，由 IAM 后端直出的静态页），全仓只有那一处。
 * 页面里那三条容易漏的判断——「准入失败必须停住，否则 IAM 两端无限往返」「换账号前先清 SSO
 * Cookie」「已登录直接进首页」——都收在共用组件里，四个 ap 共用一份。</p>
 */
export default function LoginPage() {
  const { t } = useTranslation();
  return (
    <SsoLoginPage
      mark="MD"
      brand="CIM MDS"
      brandSub={t('mds.shell.brandSub')}
      getToken={() => useAuthStore.getState().token}
      setSession={(token, refreshToken) => useAuthStore.getState().setSession(token, refreshToken)}
      redirect={sso.redirect}
      idpLogout={() => sso.idpLogout(null, null)}
      labels={{
        title: t('mds.login.title'),
        redirecting: t('mds.login.redirecting'),
        admissionDenied: t('mds.login.admissionDenied'),
        ssoFailed: t('mds.login.ssoFailed'),
        switchAccount: t('mds.login.switchAccount'),
        devPreview: t('mds.login.devPreview'),
        devApply: t('mds.login.devApply'),
      }}
    />
  );
}
