import { Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellWiring } from '@cim/shell';
import { resolveIcon } from '@/lib/iconRegistry';
import { applyTheme, persistTheme, readTheme } from '@/lib/theme';
import { useTabStore } from '@/store/tabStore';
import { useAuthStore } from '@/store/authStore';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * MDS 控制台外壳——薄接线层。
 *
 * <p>外壳框架（顶栏/侧栏/页签/footer）与全部接线逻辑（主题、菜单模型、labels、
 * 页签、开签）来自共享包：框架是 {@link ConsoleShell}，接线是 {@link useShellWiring}
 * （与 IAM / RMS / approval 同一份）。本文件只保留 MDS 自己的差异：菜单树来源、
 * 品牌文案、顶栏插槽、footer 文案与登出流转。</p>
 *
 * <p>侧栏数据源是<b>后端菜单树</b>（`/mds/me/menus`，authStore 启动时拉取、按角色过滤）
 * ——菜单与按钮全部在 {@code sys_menu} 里在线维护（菜单管理页 `/menus`），前端不持有
 * 导航配置。图标经 {@link resolveIcon} 按 {@code sys_menu.icon} 键映射。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const me = useAuthStore((s) => s.user);
  const menus = useAuthStore((s) => s.menus);
  const logout = useAuthStore((s) => s.logout);

  const wiring = useShellWiring({
    app: 'mds',
    labelNs: 'mds',
    menus,
    resolveIcon: (key) => resolveIcon(key),
    useTabStore,
    themeIo: { read: readTheme, persist: persistTheme, apply: applyTheme },
  });

  return (
    <ConsoleShell
      storageKey="mds"
      brand={{ logo: 'MD', title: 'CIM MDS', subtitle: t('mds.shell.brandSub') }}
      groups={wiring.groups}
      labels={wiring.labels}
      collapsibleGroups
      env={wiring.env}
      topbarExtra={<LanguageSwitcher />}
      user={{ name: me?.username ?? me?.userId ?? '—', meta: me?.tenantId ? `tenant:${me.tenantId}` : 'local' }}
      footerApp="CIM-AP / MDS-AP"
      footerTagline={t('mds.shell.tagline')}
      theme={wiring.theme}
      onThemeChange={wiring.onThemeChange}
      onLogout={async () => {
        // 统一登录：先回 IdP 做服务端吊销（令牌入黑名单 + 清 SSO Cookie），再回登录中继页。
        wiring.clearTabs();
        await logout();
        navigate('/login', { replace: true });
      }}
      tabs={wiring.tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
