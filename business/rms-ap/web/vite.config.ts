import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// RMS 业务前端：开发态代理到本省后端（business/rms-ap/server，端口 8084）
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      // 平台共享包：源码直引（不经 dist）。见 docs/platform/web/component-platform-design.md §9。
      '@cim/theme': path.resolve(__dirname, '../../../platform/web/packages/theme/src'),
      '@cim/ui': path.resolve(__dirname, '../../../platform/web/packages/ui/src'),
      // antd 装在**本应用**的 node_modules：@cim/ui 是源码直引，其内部的 `import 'antd'`
      // 会从 shared 包所在目录向上查找，找不到业务的 node_modules，故显式指向本地副本。
      // 这样 antd 内部 `import 'react'` 也能解析到本应用的 React，不会出现两份 React。
      antd: path.resolve(__dirname, 'node_modules/antd'),
    },
    // 共享包源码在工程根之外，其 `import 'react'` 会从 platform/web 向上解析而落空；
    // dedupe 强制这些依赖一律用本应用的副本（也避免出现两份 React → invalid hook call）。
    dedupe: ['react', 'react-dom'],
  },
  server: {
    port: 5174,
    fs: {
      // dev server 默认只允许读工程根，共享包在根之外，需显式放行
      allow: ['.', path.resolve(__dirname, '../../../platform/web/packages')],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
      },
    },
  },
});
