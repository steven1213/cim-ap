import { Suspense, lazy, useEffect, useState, type ComponentType, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimButton, CimSpin } from '@cim/ui';
import { ProtectedRoute, RequirePermission, SsoRelay } from '@cim/shell';
import { useAuthStore } from '@/store/authStore';
import { useHasPermission } from '@/lib/usePermission';
import Layout from '@/components/Layout';

// 登录/回调/403/404 保持同步加载：未登录用户首屏就落在这几条上，
// 再拆一个 chunk 只是把「白屏」换成「多一次往返」。
import LoginPage from '@/pages/LoginPage';
import SsoCallback from '@/pages/SsoCallback';
import NotFoundPage from '@/pages/NotFoundPage';
import ForbiddenPage from '@/pages/ForbiddenPage';

/**
 * 路由级权限门：未授权跳 `/403`（URL 变，可分享、可后退）。
 *
 * <p>与 RMS 的「就地渲染 403 页」不同是刻意的：主数据是后台系统，用户点进来发现无权时
 * 更希望看到地址变化并回退，而不是停在一个看似正常的空壳页上。</p>
 */
function PermGate({ code, children }: { code: string; children: ReactNode }) {
  const allowed = useHasPermission(code);
  return (
    <RequirePermission allowed={allowed} fallback={<Navigate to="/403" replace />}>
      {children}
    </RequirePermission>
  );
}

/**
 * 业务页面按路由拆包：不拆的话整个应用（含 antd、所有页面）打成一个 chunk，
 * 打开「区域」也要先下载「基地」的代码。Suspense 层级在 Layout 的 `<Outlet/>` 内部，
 * 切页时外壳不卸载，只有内容区显示加载态。
 */
function lazyRoute(Page: ComponentType, permCode?: string) {
  return (
    <Suspense fallback={<CimSpin block height={320} />}>
      {permCode ? (
        <PermGate code={permCode}>
          <Page />
        </PermGate>
      ) : (
        <Page />
      )}
    </Suspense>
  );
}

const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const SitePage = lazy(() => import('@/pages/SitePage'));
const FabPage = lazy(() => import('@/pages/FabPage'));
const AreaPage = lazy(() => import('@/pages/AreaPage'));

/**
 * 启动闸门：已登录时先拉取用户档案与权限码（内存态，不落盘），再渲染受保护路由，
 * 避免「首帧权限为空 → 误判 403」。
 */
function BootGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const token = useAuthStore((s) => s.token);
  const loadMe = useAuthStore((s) => s.loadMe);
  const loadPermissions = useAuthStore((s) => s.loadPermissions);
  const [ready, setReady] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [failure, setFailure] = useState<{ kind: 'admission' | 'unavailable' | 'auth'; detail: string } | null>(
    null,
  );

  useEffect(() => {
    if (!token) {
      setReady(true);
      return;
    }
    let alive = true;
    setReady(false);
    setFailure(null);
    Promise.all([loadMe(), loadPermissions()])
      .then(() => {
        if (alive) setReady(true);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        // ⚠️ 三类失败必须分开，一律当「准入失败」处理会让页面无限跳动：
        //   auth        401 —— api.ts 已清态并整页跳 /login，这里只需交出去；
        //   admission   403 / 1403 —— 令牌 apps 不含本 ap，清态后停在登录页讲原因；
        //   unavailable 连不上 / 5xx —— **保持登录态**，就地给「重试」。
        // 把第三种也当准入失败去清态，token 一清外层 ProtectedRoute 立刻渲染
        // `<Navigate to="/login">`（不带 error），error 参数随之丢失 → 登录页自动中继回 IAM
        // → IAM 侧 SSO 会话还在 → 立刻带同一枚令牌弹回 /sso → 再失败 → 再跳……整页往返。
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
  }, [token, attempt, loadMe, loadPermissions]);

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
        mark="MD"
        brand="CIM MDS"
        label={t('mds.boot.backendDown')}
        hint={t('mds.boot.backendDownHint')}
        busy={false}
      >
        <CimButton block onClick={() => setAttempt((v) => v + 1)}>
          {t('mds.common.retry')}
        </CimButton>
        <p className="sso-relay-hint">{failure.detail}</p>
      </SsoRelay>
    );
  }

  if (!ready) {
    return <SsoRelay mark="MD" brand="CIM MDS" label={t('mds.common.loading')} />;
  }
  return <>{children}</>;
}

export default function App() {
  const token = useAuthStore((s) => s.token);

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
              <ProtectedRoute authenticated={!!token}>
                <Layout />
              </ProtectedRoute>
            </BootGate>
          }
        >
          <Route path="/" element={lazyRoute(DashboardPage)} />
          <Route path="/sites" element={lazyRoute(SitePage, 'mds:site:view')} />
          <Route path="/fabs" element={lazyRoute(FabPage, 'mds:fab:view')} />
          <Route path="/areas" element={lazyRoute(AreaPage, 'mds:area:view')} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
