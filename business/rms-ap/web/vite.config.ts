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
    },
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
