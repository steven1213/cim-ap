// MDS 的 HTTP 接线：传输层全在 `@cim/data` 的 `createHttpClient`，本文件只声明
// 属于 MDS 的三件事——资源前缀、令牌从哪来、401 之后清谁。
//
// 拦截器语义（Bearer / Accept-Language / `Result<T>` 解包 / 401 清态回登录页）在平台包里，
// 四个 ap 共用一份。业务端点仍写在本目录的 `mdsApi.ts`——统一传输层不等于统一业务 API。
import { createHttpClient } from '@cim/data';
import { useAuthStore } from '@/store/authStore';
import type { MeDto } from '@/types';

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

export const { get, post, put, del, rootGet, rootPost, rootPut, rootDel, setAcceptLanguage } = httpClient;

/**
 * 启动探针：拉当前用户档案。
 *
 * <p>走裸 axios（见 `HttpClient#fetchMe`）：不带令牌时被调用是正常路径，不该触发
 * 401 拦截器的「清态 + 跳登录」；同时必须自己带 Bearer，否则已登录用户刷新即掉登录。</p>
 */
export function fetchMe(): Promise<MeDto | null> {
  return httpClient.fetchMe<MeDto>('/api/v1/mds/me');
}

/**
 * SSE 通知流地址（平台前缀 `/sys/**`）。
 *
 * <p>EventSource 无法自定义请求头，故用 `?token=`；后端 `BearerTokenQueryParamFilter`
 * 会把它还原成 Bearer 头再走同一套 JWT 过滤器。</p>
 */
export function notificationsStreamUrl(): string {
  return httpClient.streamUrl('/sys/notify/stream');
}

export default { fetchMe, get, post, put, del, notificationsStreamUrl, setAcceptLanguage };
