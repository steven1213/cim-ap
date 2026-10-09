import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { bootstrapI18n } from './lib/i18n';
import './styles.css';

// 先初始化 i18n（用本地缓存的译文包同步渲染，避免中文/英文切换时首屏闪烁），
// 再挂载应用；后端的最新译文包在后台异步比对版本（见 lib/i18n.ts）。
bootstrapI18n().finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </React.StrictMode>,
  );
});
