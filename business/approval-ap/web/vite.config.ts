import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 审批流平台前端：开发态代理到本省后端（business/approval-ap/server，端口 8085）。
// 后端当前为 dev 态（cim.auth.enabled=false），所有接口以 userId / tenantId 查询参数标识身份，
// 无需 IAM 登录；生产接入 IAM 后由后端从 JWT 解析身份，前端仅需附 Bearer。
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
    },
  },
});
