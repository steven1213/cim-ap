import React from 'react';
import ReactDOM from 'react-dom/client';
import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { CimProvider, UiLocaleProvider, UiPermissionProvider, UiToastProvider } from '@cim/ui';
import { usePermissions } from '@/lib/usePermission';
import { useAuthStore } from '@/store/authStore';
import { bootstrapI18n } from './lib/i18n';
import '@cim/ui/index.css';
import './styles.css';

/** 平台组件的文案由应用注入（@cim/ui 不依赖 i18n），locale 变化时随之重渲染。 */
function Root() {
  const { t, i18n } = useTranslation();
  const uiT = useCallback((key: string) => String(t(key)), [t]);
  // 「键是否存在」交给 i18n 判断：各组件的内置文案兜底只在真的缺键时才生效
  const uiHas = useCallback((key: string) => i18n.exists(key), [i18n]);

  // 权限也由应用注入：@cim/ui 的 <Can> / <CimButton perm> 只问「能不能」，
  // 不读本应用的 zustand store。不挂这个 Provider 时它们会走「一律放行」兜底 ——
  // 界面看起来正常，权限门实际失效（这类静默失效比报错更难发现）。
  const perms = usePermissions();
  const permsReady = useAuthStore((s) => s.permissionsLoaded);
  const uiHasPerm = useCallback((code: string) => perms.includes(code), [perms]);

  return (
    <UiLocaleProvider t={uiT} locale={i18n.language} hasKey={uiHas}>
      <UiPermissionProvider has={uiHasPerm} ready={permsReady}>
        <CimProvider>
          <UiToastProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </UiToastProvider>
        </CimProvider>
      </UiPermissionProvider>
    </UiLocaleProvider>
  );
}

// 先初始化 i18n（用本地缓存的译文包同步渲染，避免中文/英文切换时首屏闪烁），
// 再挂载应用；后端的最新译文包在后台异步比对版本（见 lib/i18n.ts）。
bootstrapI18n().finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <Root />
    </React.StrictMode>,
  );
});
