import BrandIcon from '@/components/BrandIcon';
import { Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellWiring } from '@cim/shell';
import { resolveIcon } from '@cim/admin';
import { useAuthStore } from '@/store/authStore';
import { useTabStore } from '@/store/tabStore';
import { useIdentityStore, SHARED_DOMAIN } from '@/store/identityStore';
import { applyTheme, persistTheme, readTheme } from '@/lib/theme';
import IdentitySwitcher from '@/components/IdentitySwitcher';
import NotificationBell from '@/components/NotificationBell';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * 审批流平台外壳——薄接线层。
 *
 * <p>外壳框架与接线逻辑来自共享包（ConsoleShell + useShellWiring，与 IAM / MDS /
 * RMS 同一份）。本文件只保留 approval 自己的差异：i18n 键无应用前缀（`shell.*`）、
 * 身份区按通道二选一（统一登录态用户块 vs dev 态身份切换）、双通道登出流转。</p>
 *
 * <p>侧栏数据源是<b>后端菜单树</b>（`/me/menus`：sso 通道按角色过滤，dev 通道为全量树，
 * authStore 内已归一为 CimMenuNode）——菜单与按钮全部在 {@code sys_menu} 里在线维护。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { clear: clearIdentity } = useIdentityStore();
  const mode = useAuthStore((s) => s.mode);
  const me = useAuthStore((s) => s.user);
  const menus = useAuthStore((s) => s.menus);
  const logout = useAuthStore((s) => s.logout);
  const isDevChannel = mode === 'dev';

  const wiring = useShellWiring({
    app: 'approval',
    labelNs: '', // approval 的 shell 文案键无应用前缀（`shell.*`）
    menus,
    resolveIcon: (key) => resolveIcon(key),
    useTabStore,
    themeIo: { read: readTheme, persist: persistTheme, apply: applyTheme },
    // dev 通道无登录身份（用户块为 null），不渲染超管徽标；sso 通道取令牌权限集
    permissions: isDevChannel ? [] : (me?.permissions ?? []),
  });

  return (
    <ConsoleShell
      storageKey="approval"
      brand={{ logo: <BrandIcon />, title: 'CIM Approval', subtitle: t('shell.brandSub') }}
      groups={wiring.groups}
      labels={wiring.labels}
      collapsibleGroups
      sidebarNote={t('shell.crypto')}
      env={wiring.env}
      topbarExtra={
        <>
          {isDevChannel && <IdentitySwitcher />}
          <NotificationBell />
          <LanguageSwitcher />
        </>
      }
      user={
        isDevChannel
          ? null
          : { name: me?.username ?? me?.userId ?? '—', meta: `domain:${me?.tenantId || SHARED_DOMAIN}` }
      }
      footerApp="CIM-AP / Approval-AP"
      footerTagline={t('shell.tagline')}
      theme={wiring.theme}
      onThemeChange={wiring.onThemeChange}
      onLogout={async () => {
        wiring.clearTabs();
        if (isDevChannel) {
          // dev 态无真实登出：清身份与页签残留，回到工作台。
          clearIdentity();
          navigate('/');
          return;
        }
        // 统一登录：先回 IdP 做服务端吊销（令牌入黑名单 + 清 SSO Cookie），再回登录中继页。
        await logout();
        navigate('/login', { replace: true });
      }}
      tabs={wiring.tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
