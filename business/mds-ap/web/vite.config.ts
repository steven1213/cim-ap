import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { createRequire } from 'node:module';
import path from 'node:path';

/**
 * 某个第三方包**实际**的安装目录。
 *
 * <p>不写 `path.resolve(__dirname, 'node_modules/antd')`：本应用是 npm workspace 成员
 * （根 `package.json` 的 workspaces 含 `business/* /web`），依赖会被提升到仓库根
 * `node_modules`，应用目录下**没有** `node_modules`——拼死路径会得到一个不存在的路径，
 * 报错信息是 `Could not load .../node_modules/react-i18next`，看着像「没装依赖」，
 * 其实是路径写错了（rms / approval / iam 各有本地 node_modules 是历史遗留，不能照抄）。</p>
 *
 * <p>用 Node 自己的解析：无论提升到根、还是将来改成在应用目录内安装，都能找到真身。</p>
 */
const req = createRequire(import.meta.url);
const dep = (id: string) => path.dirname(req.resolve(`${id}/package.json`));

// MDS 主数据前端：开发态代理到本后端（business/mds-ap/server，端口 8082）
// 后端默认 `cim.auth.enabled=true`：接口只认 JWT，前端经 IAM（8081）统一登录拿令牌。
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
      '@cim/i18n': path.resolve(__dirname, '../../../platform/web/packages/i18n/src'),
      // @cim/ui 是源码直引，其内部的 `import 'antd'` 会从共享包所在目录向上查找，
      // 落不到业务的 node_modules，故显式指向真实安装位置。
      // 这样 antd 内部 `import 'react'` 也能解析到同一份 React，不会出现两份实例。
      antd: dep('antd'),
      dayjs: dep('dayjs'),
      'react-router-dom': dep('react-router-dom'),
      // @cim/i18n 同理：必须指向同一份副本，否则会出两个 i18next 实例（切语言只刷新其中一个）
      i18next: dep('i18next'),
      'react-i18next': dep('react-i18next'),
      zustand: dep('zustand'),
    },
    // 共享包源码在工程根之外，其 `import 'react'` 会从 platform/web 向上解析而落空；
    // dedupe 强制这些依赖一律用本应用的副本（也避免两份 React → invalid hook call）。
    dedupe: ['react', 'react-dom'],
  },
  server: {
    port: 5172,
    fs: {
      // dev server 默认只允许读工程根，共享包在根之外，需显式放行
      allow: ['.', path.resolve(__dirname, '../../../platform/web/packages')],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      // 通知流走平台的 /sys/notify/**（不在 /api 前缀下）。漏了这条则 EventSource 打到
      // vite 自身 → 404，而 EventSource 只静默重连不报错，表现为「通知永远不来」。
      '/sys': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 共享依赖分组：不分组时 rollup 会把「第一个用到某依赖的页面」当成它的家，
        // 收益是缓存粒度——业务发版只改页面 chunk，vendor 三块命中浏览器缓存。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return id.includes('platform/web/packages') ? 'cim-ui' : undefined;
          }
          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/react-router') ||
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
