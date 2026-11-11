import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// IAM 业务前端：开发态代理到本省后端（business/iam-ap/server，端口 8081）
//
// 代理前缀有两个（见 src/lib/api.ts 文件头说明）：
// - `/api` → IAM 自己的 `/api/v1/**` 与 i18n starter 的公开端点 `/api/i18n/**`
// - `/sys` → 平台 `cim-system` 的端点（**无** `/api/v1` 前缀，如 `/sys/me/menus`）
//
// ⚠️ 生产部署（nginx 等）必须同样把 `/api` 与 `/sys` 反代到 iam-ap/server，否则菜单与译文取不到。
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      // 平台共享包：源码直引（不经 dist），改组件即热更。见
      // docs/platform/web/component-platform-design.md §9。
      '@cim/theme': path.resolve(__dirname, '../../../platform/web/packages/theme/src'),
      '@cim/ui': path.resolve(__dirname, '../../../platform/web/packages/ui/src'),
      // antd 装在**本应用**的 node_modules：@cim/ui 是源码直引，其内部的 `import 'antd'`
      // 会从 shared 包所在目录向上查找，找不到业务的 node_modules，故显式指向本地副本。
      // 这样 antd 内部 `import 'react'` 也能解析到本应用的 React，不会出现两份 React。
      antd: path.resolve(__dirname, 'node_modules/antd'),
      // dayjs 同理：@cim/ui 日期组件内部 import dayjs（antd 的传递依赖，已在本应用 node_modules）
      dayjs: path.resolve(__dirname, 'node_modules/dayjs'),
    },
    // 共享包源码在工程根之外，其 `import 'react'` 会从 platform/web 向上解析而落空；
    // dedupe 强制这些依赖一律用本应用的副本（也避免出现两份 React → invalid hook call）。
    dedupe: ['react', 'react-dom'],
  },
  server: {
    port: 5171,
    fs: {
      // dev server 默认只允许读工程根，共享包在根之外，需显式放行
      allow: ['.', path.resolve(__dirname, '../../../platform/web/packages')],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/sys': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 分组理由与 RMS 一致（见 business/rms-ap/web/vite.config.ts 注释）：
        // 页面已按路由 lazy 拆开，这里给共享依赖指定归属，避免它们被「首个用到它们的页面」占有。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return id.includes('platform/web/packages') ? 'cim-ui' : undefined;
          }
          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/react-router') ||
            id.includes('/scheduler/')
          ) {
            return 'vendor-react';
          }
          if (
            id.includes('/antd/') ||
            id.includes('/@ant-design/') ||
            id.includes('/@rc-component/') ||
            id.includes('/rc-') ||
            id.includes('/dayjs/')
          ) {
            return 'vendor-antd';
          }
          return 'vendor';
        },
      },
    },
  },
});
