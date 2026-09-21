import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// IAM 业务前端：开发态代理到本省后端（business/iam-ap/server，端口 8081）
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
    },
  },
});
