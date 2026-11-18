import { Suspense, useEffect, useMemo, useState, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimButton, CimSpin } from '@cim/ui';
import { SsoRelay, createMenuRoutes, type PageRegistry } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';
import ProtectedRoute from '@/components/ProtectedRoute';
import RequirePerm from '@/components/RequirePerm';

// 登录/回调/403/404 保持同步加载：未登录用户首屏就落在这几条上，
// 再拆一个 chunk 只是把「白屏」换成「多一次往返」。
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import NotFoundPage from '@/pages/NotFoundPage';
import ForbiddenPage from '@/pages/ForbiddenPage';

/**
 * 页面注册表：Vite 构建期静态收集 `pages/` 下所有页面文件。
 *
 * <p>路由不再手写——后端菜单树（`/rms/me/menus`，按角色过滤）每条 MENU 节点
 * 经 {@link createMenuRoutes} 解析出组件（`sys_menu.component` 显式指定优先，
 * 缺省按「/reason-codes → ReasonCodePage」命名约定兜底）。**新增页面 = 建一个
 * tsx + 后端目录种子加一行菜单，本文件零改动**。</p>
 */
const PAGES = import.meta.glob('./pages/*.tsx') as PageRegistry;

/**
 * 菜单驱动的业务路由：后端菜单树（`/rms/me/menus`）经 {@link createMenuRoutes} 解析出
 * Route 元素。**注意必须把结果内联展开在 `<Routes>` 的子级**——react-router 要求
 * `<Routes>` 的孩子只能是 `<Route>`/Fragment，包一层自定义组件会在运行时抛
 * 「is not a Route component」。组件解析失败的菜单已在工厂里 warn + 跳过——
 * 直连其 URL 落 `*` 的 NotFound，不会白屏。每条按 `menu.permCode` 包一层
 * RequirePerm（纵深防御；Suspense 层级在 Layout 的 `<Outlet/>` 内部，切页不闪外壳）。
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
 * 启动闸门：已登录时先拉取用户档案与权限码（内存态，不落盘），
 * 再渲染受保护路由。避免「首帧权限为空 → 误判 403」。
 */
function BootGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const token = useAuthStore((s) => s.token);
  const loadMe = useAuthStore((s) => s.loadMe);
  const loadPermissions = useAuthStore((s) => s.loadPermissions);
  const loadMenus = useAuthStore((s) => s.loadMenus);
  const [ready, setReady] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [failure, setFailure] = useState<{ kind: 'admission' | 'unavailable' | 'auth'; detail: string } | null>(null);

  useEffect(() => {
    if (!token) {
      setReady(true);
      return;
    }
    let alive = true;
    setReady(false);
    setFailure(null);
    Promise.all([loadMe(), loadPermissions(), loadMenus()])
      .then(() => {
        if (alive) setReady(true);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        // ⚠️ 三类失败必须分开，原来一律当「准入失败」处理，后果是页面无限跳动（见下）：
        //   auth        401 —— api.ts 已清态并整页跳 /login，这里只需交出去；
        //   admission   403 / 1403 —— 令牌 apps 不含本 ap，清态后停在登录页讲原因；
        //   unavailable 连不上 / 5xx —— **保持登录态**，就地给「重试」。
        // 把第三种也当准入失败去清态，token 一清外层 ProtectedRoute 立刻渲染
        // `<Navigate to="/login">`（不带 error），error 参数随之丢失 → 登录页自动中继回 IAM
        // → IAM 侧 SSO 会话还在 → 立刻带同一枚令牌弹回 /sso → 再失败 → 再跳……整页往返，
        // 表现为「页面一直在跳」且后端日志只有一串 ECONNREFUSED。
        const status = (err as { response?: { status?: number } })?.response?.status;
        const bizCode = (err as { code?: unknown })?.code;
        const kind: 'admission' | 'unavailable' | 'auth' =
          status === 401
            ? 'auth'
            : status === 403 || bizCode === 1403
              ? 'admission'
              : 'unavailable';
        console.error('[BootGate] boot failed', { kind, status, err });
        if (kind === 'admission' || kind === 'auth') {
          // 准入失败必须清态：否则 LoginPage 见 token 仍在会把用户弹回首页，形成两端往返
          useAuthStore.getState().clear();
        }
        setFailure({ kind, detail: (err as { message?: string })?.message || String(err) });
      });
    return () => {
      alive = false;
    };
    // attempt：点「重试」时 +1，重新拉一次
  }, [token, attempt, loadMe, loadPermissions, loadMenus]);

  // 准入失败：带 error=admission 落回 /login，登录页据此**停住**不再自动中继
  // （否则 IAM 侧会话仍在，会拿同一枚令牌再弹回 /sso，两端无限往返）。
  if (failure?.kind === 'admission') {
    return <Navigate to="/login?error=admission" replace />;
  }
  if (failure?.kind === 'auth') {
    return <Navigate to="/login" replace />;
  }

  // 后端不可达 / 5xx：不是登录问题，清态与跳转都只会把「服务没起」演成「一直在登录」。
  // 就地停住，把真实原因和重试按钮摆出来。
  if (failure?.kind === 'unavailable') {
    return (
      <SsoRelay
        mark="RM"
        brand="CIM RMS"
        label={t('rms.boot.backendDown')}
        hint={t('rms.boot.backendDownHint')}
        busy={false}
      >
        <CimButton block onClick={() => setAttempt((v) => v + 1)}>
          {t('rms.common.retry')}
        </CimButton>
        <p className="sso-relay-hint">{failure.detail}</p>
      </SsoRelay>
    );
  }

  if (!ready) {
    return <SsoRelay mark="RM" brand="CIM RMS" label={t('rms.common.loading')} />;
  }
  return <>{children}</>;
}

export default function App() {
  const menuRoutes = useMenuRoutes();
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/sso" element={<SsoCallback />} />
        <Route path="/403" element={<ForbiddenPage />} />

        <Route
          element={
            // ⚠️ BootGate 在 ProtectedRoute **外层**：BootGate 判断「引导失败」时要跳到
            // `/login?error=admission`，若它在内层，清态后外层守卫会先行渲染
            // `<Navigate to="/login">`（不带 error）把原因吞掉，登录页便自动中继回 IAM → 往返。
            <BootGate>
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            </BootGate>
          }
        >
          {menuRoutes.map((r) => (
            <Route key={r.path} path={r.path} element={r.element} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
