import { Suspense, lazy, useEffect, useState, type ComponentType, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CimButton, CimSpin } from '@cim/ui';
import { SsoRelay } from '@cim/shell';
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
 * 业务页面一律按路由拆包。
 *
 * <p>不拆的话整个应用（含 antd、22 个页面、xlsx 之类的工具）打成一个 1.47 MB 的 chunk，
 * 用户打开「设备区域」也要先下载「配方比对」的代码。拆开后首屏只拉布局与登录态，
 * 页面各自成 chunk，按需加载。</p>
 *
 * <p>{@link lazyRoute} 把「Suspense + 权限守卫 + 页面」包成一个路由 element：
 * Suspense 层级在 Layout 的 `<Outlet/>` **内部**，切页时外壳不卸载，
 * 只有内容区显示加载态（放在 Routes 外层写会连侧边栏一起闪掉）。</p>
 */
function lazyRoute(Page: ComponentType, permCode?: string) {
  return (
    <Suspense fallback={<CimSpin block height={320} />}>
      {permCode ? (
        <RequirePerm code={permCode}>
          <Page />
        </RequirePerm>
      ) : (
        <Page />
      )}
    </Suspense>
  );
}

const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const RecipeLibraryPage = lazy(() => import('@/pages/RecipeLibraryPage'));
const DeviceRecipePage = lazy(() => import('@/pages/DeviceRecipePage'));
const DeviceTypePage = lazy(() => import('@/pages/DeviceTypePage'));
const DeviceAreaPage = lazy(() => import('@/pages/DeviceAreaPage'));
const DevicePage = lazy(() => import('@/pages/DevicePage'));
const SignoffPage = lazy(() => import('@/pages/SignoffPage'));
const ComparePage = lazy(() => import('@/pages/ComparePage'));
const AuditLogPage = lazy(() => import('@/pages/AuditLogPage'));
const SpecPage = lazy(() => import('@/pages/SpecPage'));
const SpecTemplatePage = lazy(() => import('@/pages/SpecTemplatePage'));
const EcSvPage = lazy(() => import('@/pages/EcSvPage'));
const DeviceAuthPage = lazy(() => import('@/pages/DeviceAuthPage'));
const BypassPage = lazy(() => import('@/pages/BypassPage'));
const QualificationPage = lazy(() => import('@/pages/QualificationPage'));
const GoldenPage = lazy(() => import('@/pages/GoldenPage'));
const IntegrationPage = lazy(() => import('@/pages/IntegrationPage'));
const BodyTemplatePage = lazy(() => import('@/pages/BodyTemplatePage'));
const ExperimentPage = lazy(() => import('@/pages/ExperimentPage'));

/**
 * 启动闸门：已登录时先拉取用户档案与权限码（内存态，不落盘），
 * 再渲染受保护路由。避免「首帧权限为空 → 误判 403」。
 */
function BootGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const token = useAuthStore((s) => s.token);
  const loadMe = useAuthStore((s) => s.loadMe);
  const loadPermissions = useAuthStore((s) => s.loadPermissions);
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
    Promise.all([loadMe(), loadPermissions()])
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
          <Route path="/" element={lazyRoute(DashboardPage)} />
          <Route path="/recipes" element={lazyRoute(RecipeLibraryPage, 'rms:recipe:view')} />
          <Route path="/device-types" element={lazyRoute(DeviceTypePage, 'rms:device-type:view')} />
          <Route path="/device-areas" element={lazyRoute(DeviceAreaPage, 'rms:device-area:view')} />
          <Route path="/device-recipes" element={lazyRoute(DeviceRecipePage, 'rms:recipe:view')} />
          <Route path="/devices" element={lazyRoute(DevicePage, 'rms:device:view')} />
          <Route path="/signoff" element={lazyRoute(SignoffPage, 'rms:signoff:view')} />
          <Route path="/compare" element={lazyRoute(ComparePage, 'rms:compare:view')} />
          <Route path="/audit" element={lazyRoute(AuditLogPage, 'rms:audit:view')} />
          <Route path="/specs" element={lazyRoute(SpecPage, 'rms:spec:view')} />
          <Route path="/spec-templates" element={lazyRoute(SpecTemplatePage, 'rms:spec:view')} />
          <Route path="/ecsv" element={lazyRoute(EcSvPage, 'rms:ecsv:view')} />
          <Route path="/device-auth" element={lazyRoute(DeviceAuthPage, 'rms:device-auth:view')} />
          <Route path="/bypasses" element={lazyRoute(BypassPage, 'rms:bypass:view')} />
          <Route path="/qualifications" element={lazyRoute(QualificationPage, 'rms:qualification:view')} />
          <Route path="/golden" element={lazyRoute(GoldenPage, 'rms:golden:view')} />
          <Route path="/integration" element={lazyRoute(IntegrationPage, 'rms:integration:view')} />
          <Route path="/body-templates" element={lazyRoute(BodyTemplatePage, 'rms:btemplate:view')} />
          <Route path="/experiments" element={lazyRoute(ExperimentPage, 'rms:experiment:view')} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
