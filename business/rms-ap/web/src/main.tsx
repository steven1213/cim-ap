import ReactDOM from 'react-dom/client';
import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import App from './App';
import { CimProvider, UiLocaleProvider, UiPermissionProvider, UiToastProvider } from '@cim/ui';
import { usePermissions } from '@/lib/usePermission';
import { useAuthStore } from '@/store/authStore';
import { bootstrapI18n } from '@/lib/i18n';
import '@cim/ui/index.css';
import './styles.css';

/**
 * 平台组件的文案由应用注入，@cim/ui 本身不依赖 i18n（见 packages/ui/src/locale.tsx）。
 * locale 变化时 useTranslation 触发重渲染 → 表头、空态、按钮等组件文案随语言切换。
 *
 * UiToastProvider 必须在 CimProvider 内部：message/modal 要读 ConfigProvider 的主题。
 * UiPermissionProvider 在 CimProvider 外侧：它只做权限判定注入，不依赖主题；
 * 不挂它时 <Can> / <CimButton perm> 会走「一律放行」兜底，权限门静默失效。
 */
function Root() {
  const { t, i18n } = useTranslation();
  const uiT = useCallback((key: string) => String(t(key)), [t]);
  // 「键是否存在」交给 i18n 判断：各组件的内置文案兜底只在真的缺键时才生效
  const uiHas = useCallback((key: string) => i18n.exists(key), [i18n]);

  const perms = usePermissions();
  const permsReady = useAuthStore((s) => s.permissionsLoaded);
  const uiHasPerm = useCallback((code: string) => perms.includes(code), [perms]);

  return (
    <UiLocaleProvider t={uiT} locale={i18n.language} hasKey={uiHas}>
      <UiPermissionProvider has={uiHasPerm} ready={permsReady}>
        <CimProvider>
          <UiToastProvider>
            <App />
          </UiToastProvider>
        </CimProvider>
      </UiPermissionProvider>
    </UiLocaleProvider>
  );
}

// 先灌入 i18n 资源（本地缓存优先防闪白），再挂载应用。
bootstrapI18n().then(() => {
  // 不用 React.StrictMode：dev 模式下它会把组件挂载→卸载→再挂载，
  // useEffect 的数据加载执行两次 → 每次切换菜单同一请求发两遍（生产构建虽不受影响，
  // 但 dev 调试与 E2E 请求计数都被干扰）；页面数据加载均为幂等 GET，无需其严格检查。
  ReactDOM.createRoot(document.getElementById('root')!).render(<Root />);
});
