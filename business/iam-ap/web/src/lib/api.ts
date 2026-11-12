// IAM 的 HTTP 接线：传输层全在 `@cim/data` 的 `createHttpClient`，本文件只声明
// 属于 IAM 的三件事——资源前缀、令牌从哪来、401 之后清谁。
//
// 两个实例的由来（平台 `cim-system` 的 `/sys/**` 与 i18n 的 `/api/i18n/**` **没有**
// `/api/v1` 前缀，而 axios 只在 url 是绝对 URL 时才跳过 baseURL 拼接）在工厂里。
import { createHttpClient } from '@cim/data';
import { useAuthStore } from '@/store/authStore';

export { type PageResult, type RequestOptions, type Result } from '@cim/data';

/**
 * ⚠️ 与 `store/authStore.ts` 是**循环 import**（它要 `httpClient` 拉档案与权限码）。
 * 能成立是因为这里的 `getToken` / `onUnauthorized` 都是闭包，只在发请求时才求值——
 * 那时两个模块都已初始化完毕。若要改成模块顶层直接读 store，就会踩 TDZ。
 */
export const httpClient = createHttpClient({
  baseURL: '/api/v1',
  getToken: () => useAuthStore.getState().token,
  onUnauthorized: () => useAuthStore.getState().clear(),
  loginPath: '/login',
});

export const {
  get,
  post,
  put,
  del,
  rootGet,
  rootPost,
  rootPut,
  rootDel,
  setAcceptLanguage,
} = httpClient;

export default { get, post, put, del, rootGet, rootPost, rootPut, rootDel };
