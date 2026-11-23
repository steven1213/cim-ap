import { Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, useShellWiring } from '@cim/shell';
import { resolveIcon } from '@cim/admin';
import { useAuthStore } from '@/store/authStore';
import { useTabStore } from '@/store/tabStore';
import { applyTheme, persistTheme, readTheme } from '@/lib/theme';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import NotificationBell from '@/components/NotificationBell';

/**
 * RMS 控制台外壳——薄接线层。
 *
 * <p>外壳框架与接线逻辑来自共享包（{@link ConsoleShell} + {@link useShellWiring}，
 * 与 IAM / MDS / approval 同一份）。本文件只保留 RMS 自己的差异：菜单树来源、
 * 品牌文案、超管徽标、顶栏插槽（通知铃）、footer 文案与登出流转。</p>
 *
 * <p>侧栏数据源是<b>后端菜单树</b>（`/rms/me/menus`，authStore 启动时拉取、按角色过滤）
 * ——菜单与按钮全部在 {@code sys_menu} 里在线维护（菜单管理页 `/menus`）。三级结构：
 * 分组 DIR → 模块 DIR → 页面 MENU，由共享的 buildNavGroups 统一转换。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const menus = useAuthStore((s) => s.menus);
  const logout = useAuthStore((s) => s.logout);

  // 超管判定：后端 DbLocalAuthorityLoader 在解析超管角色时会把 SUPER_ADMIN 展开进 authorities
  // （与 cim-system 的精确字符串匹配约定一致，见 lib/permissions.ts 注释），故以 SUPER_ADMIN 为准。
  const admin = permissions.includes('SUPER_ADMIN');

  const wiring = useShellWiring({
    app: 'rms',
    labelNs: 'rms',
    menus,
    resolveIcon: (key) => resolveIcon(key),
    useTabStore,
    themeIo: { read: readTheme, persist: persistTheme, apply: applyTheme },
  });

  return (
    <ConsoleShell
      storageKey="rms"
      brand={{ logo: 'RM', title: 'CIM RMS', subtitle: t('rms.shell.brandSub') }}
      groups={wiring.groups}
      labels={wiring.labels}
      collapsibleGroups
      sidebarNote={t('rms.shell.crypto')}
      user={{ name: user?.username ?? t('rms.common.dash'), meta: user?.tenantId ? `tenant:${user.tenantId}` : 'local' }}
      roleBadge={
        admin ? (
          <span className="role-chip" title={t('rms.shell.adminHint')}>
            {t('rms.shell.adminBadge')}
          </span>
        ) : undefined
      }
      env={wiring.env}
      topbarExtra={
        <>
          <NotificationBell />
          <LanguageSwitcher />
        </>
      }
      footerApp="CIM-AP / RMS-AP"
      footerTagline={t('rms.shell.tagline')}
      theme={wiring.theme}
      onThemeChange={wiring.onThemeChange}
      onLogout={async () => {
        await logout();
        wiring.clearTabs(); // 下一个登录者不应看到上一个人的页签残留
        navigate('/login');
      }}
      tabs={wiring.tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
