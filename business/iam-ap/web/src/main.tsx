import React from 'react';
import ReactDOM from 'react-dom/client';
import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { CimProvider, UiLocaleProvider, UiToastProvider } from '@cim/ui';
import { bootstrapI18n } from './lib/i18n';
import '@cim/ui/index.css';
import './styles.css';

/** 平台组件的文案由应用注入（@cim/ui 不依赖 i18n），locale 变化时随之重渲染。 */
function Root() {
  const { t, i18n } = useTranslation();
  const uiT = useCallback((key: string) => String(t(key)), [t]);
  // 「键是否存在」交给 i18n 判断：各组件的内置文案兜底只在真的缺键时才生效
  const uiHas = useCallback((key: string) => i18n.exists(key), [i18n]);
  return (
    <UiLocaleProvider t={uiT} locale={i18n.language} hasKey={uiHas}>
      <CimProvider>
        <UiToastProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </UiToastProvider>
      </CimProvider>
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
