import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { bootstrapI18n } from '@/lib/i18n';

// 先灌入 i18n 资源（本地缓存优先防闪白），再挂载应用。
bootstrapI18n().then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
});
