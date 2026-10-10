// 统一 API 客户端：axios 实例 + 请求/响应拦截器。
//
// 本工程同时对接**两类后端路径前缀**，故有两个实例：
//
// - `api`     → baseURL `/api/v1`：IAM 自己的管理面（`/api/v1/admin/**`、`/login`、`/me` 等）。
// - `rootApi` → baseURL ''      ：平台 `cim-system` 的端点**没有** `/api/v1` 前缀
//   （`/sys/me/menus`、`/sys/menus` 等），以及 `cim-i18n-starter` 的公开端点 `/api/i18n/**`。
//   axios 的 `buildFullPath` 只在 url 是**绝对 URL**（带 scheme）时才跳过 baseURL 拼接，
//   光写 `/sys/...` 仍会被拼成 `/api/v1/sys/...`（实测踩坑），因此必须用独立实例而非改调用写法。
//
// 两者共用同一套拦截器语义（附带 Bearer、解包 Result<T>、401 清态跳登录）。
import axios, { type AxiosInstance } from 'axios';
import { useAuthStore } from '@/store/authStore';

/** 与 server 端 Result<T> 对齐。 */
export interface Result<T> {
  code: number;
  msg: string;
  data: T;
  errors?: Record<string, string>;
}

/**
 * 当前语言（供 `Accept-Language` 请求头）。
 *
 * <p>由 `lib/i18n.ts` 在切换语言时写入。刻意用「可写变量 + setter」而非从 i18n 模块 import，
 * 以切断 `api ⇄ i18n` 的循环依赖（i18n 需要 api 拉译文包）。</p>
 */
let acceptLanguage: string | null = null;

/** 设置后续请求的 `Accept-Language`（由 i18n 模块调用）。 */
export function setAcceptLanguage(lang: string | null): void {
  acceptLanguage = lang;
}

function attachInterceptors(instance: AxiosInstance): void {
  instance.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    if (acceptLanguage) {
      config.headers['Accept-Language'] = acceptLanguage;
    }
    return config;
  });

  instance.interceptors.response.use(
    (resp) => resp,
    (error) => {
      const status = error?.response?.status;
      if (status === 401) {
        useAuthStore.getState().clear();
        if (window.location.pathname !== '/login') {
          window.location.assign('/login');
        }
      }
      const data = error?.response?.data;
      if (data && typeof data === 'object' && 'code' in data) {
        const e: any = new Error(data.msg || '请求失败');
        e.code = data.code;
        e.errors = data.errors;
        return Promise.reject(e);
      }
      return Promise.reject(error);
    },
  );
}

// 后端所有 /api/v1 端点（见 server 各 Controller 的 @RequestMapping），
// 故 baseURL 取 /api/v1；页面内调用只写资源段（如 '/login/salt'、'/admin/users'）。
const http: AxiosInstance = axios.create({ baseURL: '/api/v1' });

// 平台端点全局前缀实例（见文件头说明）。
const rootHttp: AxiosInstance = axios.create({ baseURL: '' });

attachInterceptors(http);
attachInterceptors(rootHttp);

function assertOk<T>(body: Result<T>): void {
  if (body.code !== 0) {
    const e: any = new Error(body.msg || '请求失败');
    e.code = body.code;
    e.errors = body.errors;
    throw e;
  }
}

async function request<T>(instance: AxiosInstance, method: string, url: string,
                          payload?: unknown): Promise<T> {
  const r = await instance.request<Result<T>>({
    method,
    url,
    ...(method === 'GET' ? { params: payload } : { data: payload }),
  });
  assertOk(r.data);
  return r.data.data;
}

// ----------------------------------------------------------------- /api/v1

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>(http, 'GET', url, params);
}

export function post<T>(url: string, body?: unknown): Promise<T> {
  return request<T>(http, 'POST', url, body);
}

export function put<T>(url: string, body?: unknown): Promise<T> {
  return request<T>(http, 'PUT', url, body);
}

export function del<T>(url: string): Promise<T> {
  return request<T>(http, 'DELETE', url);
}

/** 透传原始响应体（不按 Result 解包），用于返回裸对象 / 非 Result 的端点（如 token-version bump）。 */
export async function postRaw<T>(url: string, body?: unknown): Promise<T> {
  const r = await http.post<T>(url, body);
  return r.data;
}

// ------------------------------------------------------------ 平台路径（无 /api/v1）

/** 平台端点 GET（`/sys/**`、`/api/i18n/**`）。 */
export function rootGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>(rootHttp, 'GET', url, params);
}

/** 平台端点 POST。 */
export function rootPost<T>(url: string, body?: unknown): Promise<T> {
  return request<T>(rootHttp, 'POST', url, body);
}

/** 平台端点 PUT。 */
export function rootPut<T>(url: string, body?: unknown): Promise<T> {
  return request<T>(rootHttp, 'PUT', url, body);
}

/** 平台端点 DELETE。 */
export function rootDel<T>(url: string): Promise<T> {
  return request<T>(rootHttp, 'DELETE', url);
}

export default { get, post, put, del, postRaw, rootGet, rootPost, rootPut, rootDel };
