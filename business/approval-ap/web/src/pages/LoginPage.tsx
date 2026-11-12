import { useTranslation } from 'react-i18next';
import { SsoLoginPage } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { sso } from '@/lib/sso';

/**
 * 登录入口 —— 页面与全部判断在 `@cim/shell` 的 {@link SsoLoginPage}，本文件只提供 approval 的品牌与文案。
 *
 * <p><b>本页不是登录表单</b>：表单在 IAM（`8081/login`，由 IAM 后端直出的静态页），全仓只有那一处。
 * 页面里那三条容易漏的判断——「准入失败必须停住，否则 IAM 两端无限往返」「换账号前先清 SSO
 * Cookie」「已登录直接进首页」——都收在共用组件里，四个 ap 共用一份。</p>
 *
 * <p>唯一「本页用不上」的能力是「已登录直接进首页」：approval 的 `dev` 通道没有令牌，
 * `getToken()` 恒为 null，判断自然落到「未登录 → 走中继」，而 dev 态的 `mode` 判定在
 * `BootGate` 里已经先一步拦住，不会走到这个页面。</p>
 */
export default function LoginPage() {
  const { t } = useTranslation();
  return (
    <SsoLoginPage
      mark="AP"
      brand="CIM Approval"
      brandSub={t('shell.brandSub')}
      getToken={() => useAuthStore.getState().token}
      setSession={(token, refreshToken) => useAuthStore.getState().setSession(token, refreshToken)}
      redirect={sso.redirect}
      idpLogout={() => sso.idpLogout(null, null)}
      labels={{
        title: t('login.title'),
        redirecting: t('login.redirecting'),
        admissionDenied: t('login.admissionDenied'),
        ssoFailed: t('login.ssoFailed'),
        switchAccount: t('login.switchAccount'),
        devPreview: t('login.devPreview'),
        devApply: t('login.devApply'),
      }}
    />
  );
}
