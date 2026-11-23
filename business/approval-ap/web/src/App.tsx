import BrandIcon from '@/components/BrandIcon';
import { Suspense, lazy, useEffect, useMemo, useState, type ComponentType, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimButton, CimSpin } from '@cim/ui';
import { ProtectedRoute, SsoRelay, createMenuRoutes, type PageRegistry } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';
import RequirePerm from '@/components/RequirePerm';

// 登录中继/回调保持同步加载：未登录用户首屏就落在这两条上，再拆 chunk 只是多一次往返。
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import NotFoundPage from '@/pages/NotFoundPage';

/**
 * 页面注册表：Vite 构建期静态收集 `pages/` 下所有页面文件。
 *
 * <p>路由不再手写——后端菜单树（`/me/menus`，按角色过滤；dev 通道为全量树）每条 MENU 节点
 * 经 {@link createMenuRoutes} 解析出组件（`sys_menu.component` 显式指定优先，缺省按
 * 「/todo → TodoPage」命名约定兜底）。**新增页面 = 建一个 tsx + 后端目录种子加一行菜单，
 * 本文件零改动**。</p>
 */
const PAGES = import.meta.glob('./pages/*.tsx') as PageRegistry;

/**
 * 菜单驱动的业务路由：后端菜单树（`/me/menus`）经 {@link createMenuRoutes} 解析出
 * Route 元素。**注意必须把结果内联展开在 `<Routes>` 的子级**——react-router 要求
 * `<Routes>` 的孩子只能是 `<Route>`/Fragment，包一层自定义组件会在运行时抛
 * 「is not a Route component」。组件解析失败的菜单已在工厂里 warn + 跳过——
 * 直连其 URL 落 `*` 的 NotFound，不会白屏。每条按 `menu.permCode` 包一层
 * RequirePerm（纵深防御；dev 演示通道恒放行。Suspense 层级在 Layout 的
 * `<Outlet/>` 内部，切页不闪外壳）。
 */
function useMenuRoutes(): Array<{ path: string; element: ReactNode }> {
  const menus = useAuthStore((s) => s.menus);
  return useMemo(
    () =>
      createMenuRoutes(menus, PAGES).map((r) => ({
        path: r.path,
        element: (
          <Suspense fallback={<CimSpin block height={320} />}>
            {r.menu.permCode ? (
              <RequirePerm code={r.menu.permCode}>
                <r.Component />
              </RequirePerm>
            ) : (
              <r.Component />
            )}
          </Suspense>
        ),
      })),
    [menus],
  );
}

/**
 * 启动闸门：统一登录态下先把 `/me` 的档案（灌 `identityStore`）与菜单树（路由数据源）
 * 拉齐，再渲染外壳。dev 通道在 `bootstrap`（main.tsx 挂载前）已拉菜单，直接放行。
 */
function BootGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const mode = useAuthStore((s) => s.mode);
  const user = useAuthStore((s) => s.user);
  const menusLoaded = useAuthStore((s) => s.menusLoaded);
  const loadMe = useAuthStore((s) => s.loadMe);
  const loadMenus = useAuthStore((s) => s.loadMenus);
  const [booted, setBooted] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [failure, setFailure] = useState<{ kind: 'admission' | 'unavailable' | 'auth'; detail: string } | null>(null);

  useEffect(() => {
    if (mode !== 'sso') {
      // dev / unknown：无启动依赖（dev 的菜单已在 bootstrap 拉）
      setBooted(true);
      return;
    }
    // `/sso` 回调页已灌档案 + 菜单时不再重复拉
    if (user && menusLoaded) {
      setBooted(true);
      return;
    }
    let alive = true;
    setFailure(null);
    Promise.all([loadMe(), loadMenus()])
      .then(() => {
        if (alive) setBooted(true);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        // ⚠️ 三类失败必须分开。原来一律当「准入失败」，于是「后端没起」也会被清态 + 跳登录，
        // 而登录页会自动中继回 IAM、IAM 侧会话仍在 → 带同一枚令牌弹回 /sso → 再失败，
        // 整页往返（表现为页面一直在跳）。见 RMS 同款修复与 AUDIT §44。
        const status = (err as { response?: { status?: number } })?.response?.status;
        const bizCode = (err as { code?: unknown })?.code;
        const kind: 'admission' | 'unavailable' | 'auth' =
          status === 401
            ? 'auth'
            : status === 403 || bizCode === 1403
              ? 'admission'
              : 'unavailable';
        console.error('[BootGate] /me 失败', { kind, status, err });
        if (kind === 'admission' || kind === 'auth') {
          // 准入失败必须清态：否则 LoginPage 见仍有令牌会把用户弹回首页，形成两端往返
          useAuthStore.getState().clear();
        }
        setFailure({ kind, detail: (err as { message?: string })?.message || String(err) });
      });
    return () => {
      alive = false;
    };
    // attempt：点「重试」时 +1，重新拉一次
  }, [mode, user, menusLoaded, attempt, loadMe, loadMenus]);

  // 准入失败：带 error=admission 落回 /login，登录页据此停住不再自动中继
  if (failure?.kind === 'admission') return <Navigate to="/login?error=admission" replace />;
  if (failure?.kind === 'auth') return <Navigate to="/login" replace />;

  // 后端不可达 / 5xx：不是登录问题。清态与跳转只会把「服务没起」演成「一直在登录」。
  if (failure?.kind === 'unavailable') {
    return (
      <SsoRelay
        mark={<BrandIcon />}
        brand="CIM Approval"
        label={t('common.backendDown')}
        hint={t('common.backendDownHint')}
        busy={false}
      >
        <CimButton block onClick={() => setAttempt((v) => v + 1)}>
          {t('common.retry')}
        </CimButton>
        <p className="sso-relay-hint">{failure.detail}</p>
      </SsoRelay>
    );
  }

  if (mode === 'sso' && !booted) {
    return <SsoRelay mark={<BrandIcon />} brand="CIM Approval" label={t('common.loading')} />;
  }
  return <>{children}</>;
}

export default function App() {
  const mode = useAuthStore((s) => s.mode);
  const token = useAuthStore((s) => s.token);
  const menuRoutes = useMenuRoutes();
  // dev 通道不设登录门；统一登录要求令牌（档案与菜单由 BootGate 补齐）
  const authenticated = mode === 'dev' || Boolean(token);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/sso" element={<SsoCallback />} />

        <Route
          element={
            // ⚠️ BootGate 在 ProtectedRoute **外层**：准入失败时要跳到 `/login?error=admission`，
            // 若它在内层，清态后外层守卫会先行渲染 `<Navigate to="/login">`（不带 error）把原因吞掉。
            <BootGate>
              <ProtectedRoute authenticated={authenticated}>
                <Layout />
              </ProtectedRoute>
            </BootGate>
          }
        >
          {menuRoutes.map((r) => (
            <Route key={r.path} path={r.path} element={r.element} />
          ))}
          {/* 企微免登回调：链接由企微内置浏览器打开、本身不带 JWT，不在菜单树里，保留显式路由。
              该链路目前只在 dev 通道可用（见 AUDIT §36.5「已知边界」）。 */}
          <Route path="/wecom/callback" element={<Suspense fallback={<CimSpin block height={320} />}><WeComCallback /></Suspense>} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

const WeComCallback = lazyPage('./pages/WeComCallbackPage.tsx');

/** 单页 lazy 化（菜单外显式路由专用；菜单内页面由 createMenuRoutes 统一 lazy）。 */
function lazyPage(key: string) {
  const loader = PAGES[key];
  if (!loader) throw new Error(`[App] 页面注册表缺少 ${key}`);
  const C = lazy(() => loader().then((m) => ({ default: (m as { default?: ComponentType }).default ?? (m as ComponentType) })));
  return C;
}
