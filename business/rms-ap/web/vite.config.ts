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
      '@cim/shell': path.resolve(__dirname, '../../../platform/web/packages/shell/src'),
      // antd 装在**本应用**的 node_modules：@cim/ui 是源码直引，其内部的 `import 'antd'`
      // 会从 shared 包所在目录向上查找，找不到业务的 node_modules，故显式指向本地副本。
      // 这样 antd 内部 `import 'react'` 也能解析到本应用的 React，不会出现两份 React。
      antd: path.resolve(__dirname, 'node_modules/antd'),
      // dayjs 同理：@cim/ui 日期组件内部 import dayjs（antd 的传递依赖，已在本应用 node_modules）
      dayjs: path.resolve(__dirname, 'node_modules/dayjs'),
      // @cim/shell 同理：它 import react-router-dom / zustand，若从 shared 包目录向上查找会落空
      'react-router-dom': path.resolve(__dirname, 'node_modules/react-router-dom'),
      zustand: path.resolve(__dirname, 'node_modules/zustand'),
    },
    // 共享包源码在工程根之外，其 `import 'react'` 会从 platform/web 向上解析而落空；
    // dedupe 强制这些依赖一律用本应用的副本（也避免出现两份 React → invalid hook call）。
    dedupe: ['react', 'react-dom'],
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
  build: {
    rollupOptions: {
      output: {
        // 路由级 code-split（App.tsx 的 lazy()）会把页面切成小 chunk，但**共享依赖**要有归属：
        // 不显式分组时，rollup 会把「第一个用到某依赖的页面」当成它的家
        // （实测 antd 的 Table/Modal/Form 全被塞进 DeviceAreaPage 的 chunk，613 KB）。
        // 分组后：react / antd / 平台组件 各成一块，页面 chunk 只剩自己的业务代码。
        //
        // 注意：这不是"让首屏更小"——antd 在根上被 CimProvider 用到，注定属于首屏；
        // 收益是**缓存粒度**：业务发版只改页面 chunk，vendor 三块命中浏览器缓存。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            // 平台共享包（源码直引，不经 node_modules）单独成块，避免被首个页面「占有」
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
