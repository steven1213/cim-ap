import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// MDS 业务前端：开发态代理到本省后端（business/mds-ap/server，端口 8082）
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5172,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
});
