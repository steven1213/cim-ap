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
    },
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
});
