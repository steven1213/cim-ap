import BrandIcon from '@/components/BrandIcon';
import { Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ConsoleShell, normalizeMenuTree, useShellWiring } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import { useTabStore } from '@/store/tabStore';
import { applyTheme, persistTheme, readTheme } from '@/lib/theme';
import { resolveIcon } from '@/lib/iconRegistry';
import LanguageSwitcher from '@/components/LanguageSwitcher';

/**
 * 控制台外壳——薄接线层。
 *
 * <p>外壳框架与接线逻辑来自共享包（ConsoleShell + useShellWiring，与 MDS / RMS /
 * approval 同一份）。本文件只保留 IAM 自己的差异：菜单从哪来（信封形树，归一后注入）、
 * 页面副标题（`{i18nCode}.desc`，IAM 菜单库逐页配置说明）、顶栏插槽与登出流转。
 * 超管徽标由接线统一判定（permissions 含平台通配码 SUPER_ADMIN）。</p>
 *
 * <p><b>菜单完全由后端驱动</b>：结构来自 {@link useMenuStore}（`GET /sys/me/menus`，
 * 已按角色过滤），文案来自 i18n（`t(i18nCode)`），图标来自 {@link resolveIcon}。
 * 前端没有任何「菜单清单」常量——改菜单/改文案/改图标都只需要动库（或管理端界面）。</p>
 */
export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const logout = useAuthStore((s) => s.logout);
  const menuTree = useMenuStore((s) => s.tree);
  const clearMenus = useMenuStore((s) => s.clear);
  const requestScroll = useTabStore((s) => s.requestScroll);

  // IAM 的菜单 store 持有后端信封形（{menu, children}）——统一归一为 CimMenuNode
  // 再交给共享接线（与 createAuthStore.menus 同一数据形态）。
  const menus = normalizeMenuTree(menuTree);

  const wiring = useShellWiring({
    app: 'iam',
    labelNs: 'iam',
    menus,
    resolveIcon: (key) => resolveIcon(key),
    useTabStore,
    themeIo: { read: readTheme, persist: persistTheme, apply: applyTheme },
    itemDesc: true, // 页面条目带 `{code}.desc` 副标题
    topMenuCap: false, // 顶级 MENU（概览/我的）自成一档不带分组标题
    permissions,
  });

  return (
    <ConsoleShell
      storageKey="iam"
      brand={{ logo: <BrandIcon />, title: 'CIM IAM', subtitle: t('iam.shell.brandSub') }}
      groups={wiring.groups}
      labels={wiring.labels}
      sidebarNote={t('iam.shell.crypto')}
      user={{
        name: user?.username ?? t('iam.common.notLoggedIn'),
        meta: user?.tenantId ? `tenant:${user.tenantId}` : 'local',
      }}
      roleBadge={wiring.roleBadge}
      env={wiring.env}
      topbarExtra={<LanguageSwitcher />}
      footerApp="CIM-AP / IAM-AP"
      footerTagline={t('iam.shell.tagline')}
      theme={wiring.theme}
      onThemeChange={wiring.onThemeChange}
      onLogout={async () => {
        await logout();
        clearMenus(); // 下一个登录者不应看到上一个人的菜单残留
        wiring.clearTabs(); // 页签同理
        navigate('/login');
      }}
      collapsibleGroups
      mobileBreakpoint={900}
      onNavSelect={() => requestScroll()}
      tabs={wiring.tabs}
    >
      <Outlet />
    </ConsoleShell>
  );
}
