import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// RMS 业务前端：开发态代理到本省后端（business/rms-ap/server，端口 8084）
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
      },
    },
  },
});
