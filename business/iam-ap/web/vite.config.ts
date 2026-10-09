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
    },
  },
  server: {
    port: 5171,
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
