// 渲染契约测试的运行器：用 esbuild 把 test/render-contract.tsx 打成单文件 ESM 再交给 Node 跑。
//
// 为什么不用 vitest/jest：本工程刻意保持零测试框架依赖（见 README §3.1 的测试口径）。
// 这条链路只用到已有依赖（vite 自带的 esbuild + 已在 node_modules 的 react/react-dom），
// 不新增任何 devDependency，也不需要 DOM 环境（页面被渲染成静态 HTML 字符串）。
import { build } from 'esbuild';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');

const outfile = path.join(root, 'node_modules', '.cache', 'iam-render-contract.mjs');
mkdirSync(path.dirname(outfile), { recursive: true });

await build({
  entryPoints: [path.join(here, 'render-contract.tsx')],
  outfile,
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node20',
  jsx: 'automatic',
  sourcemap: 'inline',
  logLevel: 'warning',
  // react-dom 等 CJS 依赖在运行期 `require('stream')`。esbuild 的 ESM 产物只提供
  // 「调用即抛」的 `__require` 占位，而它内部写了
  // `typeof require !== 'undefined' ? require : <占位>` —— 故只要在**同一模块作用域**
  // 先行定义真实的 `require`（由 createRequire 取得），CJS 依赖即可正常解析内置模块。
  banner: {
    js: [
      "import { createRequire as __cimCreateRequire } from 'node:module';",
      'const require = __cimCreateRequire(import.meta.url);',
    ].join('\n'),
  },
  // 与 vite/vitest 的 `@` 别名保持一致（vite.config.ts 的 resolve.alias）
  alias: { '@': path.join(root, 'src') },
  // 源码里引用了 vite 注入的 `import.meta.env`（lib/i18n.ts、zustand 内部）
  define: {
    'import.meta.env.DEV': 'false',
    'import.meta.env.PROD': 'true',
    'import.meta.env.MODE': '"test"',
  },
});

await import(pathToFileURL(outfile).href);
