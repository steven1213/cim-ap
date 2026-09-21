import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// MES 业务前端：开发态代理到本省后端（business/mes-ap/server，端口 8083）
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
});
