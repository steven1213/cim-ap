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
      // 平台共享包：源码直引（不经 dist）。见 docs/platform/web/component-platform-design.md §9。
      '@cim/theme': path.resolve(__dirname, '../../../platform/web/packages/theme/src'),
      '@cim/ui': path.resolve(__dirname, '../../../platform/web/packages/ui/src'),
      '@cim/data': path.resolve(__dirname, '../../../platform/web/packages/data/src'),
      '@cim/shell': path.resolve(__dirname, '../../../platform/web/packages/shell/src'),
      // antd 装在**本应用**的 node_modules：@cim/ui 是源码直引，其内部的 `import 'antd'`
      // 会从 shared 包所在目录向上查找，找不到业务的 node_modules，故显式指向本地副本。
      // 这样 antd 内部 `import 'react'` 也能解析到本应用的 React，不会出现两份 React。
      antd: path.resolve(__dirname, 'node_modules/antd'),
      // dayjs 同理：@cim/ui 日期组件内部 import dayjs（antd 的传递依赖，已在本应用 node_modules）
      dayjs: path.resolve(__dirname, 'node_modules/dayjs'),
      // @cim/shell 同理：它 import react-router-dom / zustand，从 shared 包目录向上查找会落空
      'react-router-dom': path.resolve(__dirname, 'node_modules/react-router-dom'),
      zustand: path.resolve(__dirname, 'node_modules/zustand'),
    },
    // 共享包源码在工程根之外，其 `import 'react'` 会从 platform/web 向上解析而落空；
    // dedupe 强制这些依赖一律用本应用的副本（也避免出现两份 React → invalid hook call）。
    dedupe: ['react', 'react-dom'],
  },
  server: {
    port: 5175,
    fs: {
      // dev server 默认只允许读工程根，共享包在根之外，需显式放行
      allow: ['.', path.resolve(__dirname, '../../../platform/web/packages')],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 分组理由与 RMS 一致（见 business/rms-ap/web/vite.config.ts 注释）。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return id.includes('platform/web/packages') ? 'cim-ui' : undefined;
          }
          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/react-router') ||
            // react-router 6.4+ 把核心挪到 @remix-run/router；不一起归组会出现
            // 「vendor → vendor-react → vendor」循环 chunk 警告
            id.includes('/@remix-run/') ||
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
