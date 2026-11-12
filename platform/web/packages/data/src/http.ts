// 统一 HTTP 传输层：axios 实例 + 拦截器——四个 ap 共用的那一层。
//
// 收在这里的都是「抄到第四遍仍然会抄错」的部分：
//
//   1. 每个请求附 `Bearer`（令牌来源由应用注入，本包不认识任何 store）；
//   2. `Accept-Language` 由 i18n 适配层经 `setAcceptLanguage` 写入。刻意用「可写变量 +
//      setter」而不是 import i18n 模块——i18n 需要 api 拉译文包，直接互相 import 会成环；
//   3. 解包平台 `Result<T>`：**HTTP 200 也可能是业务错误**（`code !== 0`），页面不该逐个判；
//   4. `401` → 清本地态 + 回登录页。这条踩过两次：「登录成功后刷新又回登录页」是裸 axios
//      没带令牌；「页面一直跳」是清态后外层守卫丢掉了 error 参数。
//
// **不收业务端点**。`listSites` / `listFabs` / `listTodo` 这些仍写在各 ap 自己的
// `lib/xxApi.ts` 里——统一传输出错的处理方式，不等于统一业务 API 层。

import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios';
import type { ApiError } from './types';

/** 与 server 端 `Result<T>` 对齐。 */
export interface Result<T> {
  code: number;
  msg: string;
  data: T;
  errors?: Record<string, string>;
}

/** 平台分页结构（`cim-core` 的 `PageResult`：records/total/page/size）。 */
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface RequestOptions {
  /** 让 `useResource` 在 deps 变化 / 卸载时真能中止在途请求（不传则 abort 只是丢弃回包）。 */
  signal?: AbortSignal;
  /** 额外请求头，如 multipart 的 `Content-Type`。 */
  headers?: Record<string, string>;
  /**
   * 查询参数。
   *
   * <p>为什么 `get`/`del` 已经有个 `params` 参数还要这个：这两个方法的第二参就是查询参数，
   * 签名必须与既有调用面一致；而 `post`/`put` 的第二参是 body，查询参数只能从这里进
   * （approval 的每个请求还要带 `userId`/`tenantId` 身份参数）。</p>
   */
  params?: Record<string, unknown>;
}

export interface HttpClientOptions {
  /** 业务端点前缀，默认 `/api/v1`（approval 用 `/api/v1/approval`）。 */
  baseURL?: string;
  /**
   * 平台端点前缀，默认 `''`。
   *
   * <p>平台 `cim-system` 的端点**没有** `/api/v1`（`/sys/me/menus`、`/sys/me/permissions`），
   * `cim-i18n-starter` 的公开端点也在 `/api/i18n/**`。axios 的 `buildFullPath` 只在 url 是
   * **绝对 URL**（带 scheme）时才跳过 baseURL 拼接——光写 `/sys/...` 仍会被拼成
   * `/api/v1/sys/...`（实测踩坑），所以必须用独立实例，而不是改调用写法。</p>
   */
  platformBaseURL?: string;
  /** 当前 access token（各 ap 从自己的 store 读）。 */
  getToken: () => string | null;
  /** 收到 401 时清本地会话（默认什么都不做——approval 的 dev 通道没有令牌，不该被清）。 */
  onUnauthorized?: () => void;
  /** 401 后整页跳转到哪，默认 `/login`；传 `null` 关闭跳转。 */
  loginPath?: string | null;
}

export interface HttpClient {
  /** 原始实例，给需要完全自定义请求的场景（如 SSE 经 EventSource 时取 baseURL）。 */
  instance: AxiosInstance;
  get<T>(url: string, params?: Record<string, unknown>, opts?: RequestOptions): Promise<T>;
  post<T>(url: string, body?: unknown, opts?: RequestOptions): Promise<T>;
  put<T>(url: string, body?: unknown, opts?: RequestOptions): Promise<T>;
  del<T>(url: string, params?: Record<string, unknown>, opts?: RequestOptions): Promise<T>;
  /** 平台端点（无 `/api/v1` 前缀）。 */
  rootGet<T>(url: string, params?: Record<string, unknown>, opts?: RequestOptions): Promise<T>;
  rootPost<T>(url: string, body?: unknown, opts?: RequestOptions): Promise<T>;
  rootPut<T>(url: string, body?: unknown, opts?: RequestOptions): Promise<T>;
  rootDel<T>(url: string, params?: Record<string, unknown>, opts?: RequestOptions): Promise<T>;
  /** 二进制下载（后端的 DOWNLOAD 类端点不经 `Result` 包装）。 */
  downloadBlob(url: string, params?: Record<string, unknown>, opts?: RequestOptions): Promise<Blob>;
  /** 设置后续请求的 `Accept-Language`（由 i18n 适配层调用）。 */
  setAcceptLanguage(lang: string | null): void;
  /**
   * 启动探针：拉当前用户档案，`401` 返回 `null` 而不是抛错。
   *
   * <p>走**裸 axios** 而非带拦截器的实例：探针在「没有令牌」时被调用是正常路径，走实例会被
   * 401 拦截器当成会话失效直接跳登录页，反而分不清「未登录」与「后端挂了」。</p>
   *
   * <p>⚠️ 裸 axios 不会自动附 `Bearer`，所以这里**必须自己带上落盘的令牌**：否则已登录用户
   * 每次启动都会收到 401、被判成未登录并清掉会话——表现为「登录成功后刷新页面又回到登录页」。</p>
   *
   * @param path 完整路径（含 baseURL 前缀，因为裸 axios 没有 baseURL）
   */
  fetchMe<T>(path: string): Promise<T | null>;
  /**
   * SSE 通知流地址：`EventSource` 无法自定义请求头，故用 `?token=`，
   * 后端 `BearerTokenQueryParamFilter` 会把它还原成 Bearer 头再走同一套 JWT 过滤器。
   */
  streamUrl(path: string): string;
}

function makeApiError(
  message: string,
  extra: { code?: number; errors?: Record<string, string>; status?: number },
): ApiError {
  const e = new Error(message) as ApiError;
  if (extra.code !== undefined) e.code = extra.code;
  if (extra.errors) e.errors = extra.errors;
  if (extra.status !== undefined) e.status = extra.status;
  return e;
}

/** 从各种错误响应体里抠出可读文案与业务码。 */
function normalizeError(error: unknown): ApiError {
  const err = error as { message?: string; response?: { status?: number; data?: unknown } };
  const status = err?.response?.status;
  const data = err?.response?.data;

  let message = err?.message || '请求失败';
  let code: number | undefined;
  let errors: Record<string, string> | undefined;

  if (data && typeof data === 'object') {
    const body = data as { code?: number; msg?: string; message?: string; errors?: Record<string, string> };
    // 平台 `Result`：{code,msg}；Spring 默认错误体：{message}。两种都要认。
    if (typeof body.msg === 'string' && body.msg) message = body.msg;
    else if (typeof body.message === 'string' && body.message) message = body.message;
    if (typeof body.code === 'number') code = body.code;
    if (body.errors) errors = body.errors;
  }

  return makeApiError(message, { code, errors, status });
}

export function createHttpClient(options: HttpClientOptions): HttpClient {
  const {
    baseURL = '/api/v1',
    platformBaseURL = '',
    getToken,
    onUnauthorized,
    loginPath = '/login',
  } = options;

  /** 当前语言（供 `Accept-Language`）。见文件头第 2 条：刻意用可写变量切断循环依赖。 */
  let acceptLanguage: string | null = null;

  function attachInterceptors(instance: AxiosInstance): void {
    instance.interceptors.request.use((config) => {
      const token = getToken();
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
        // 401：令牌过期 / 被踢出。清态并回登录页（`/login` 会自动中继到 IAM）。
        // 已是登录页时不跳——`ProtectedRoute` 自己会停在原地，再 assign 一次只是白刷一屏。
        if (error?.response?.status === 401) {
          onUnauthorized?.();
          if (loginPath && window.location.pathname !== loginPath) {
            window.location.assign(loginPath);
          }
        }
        return Promise.reject(normalizeError(error));
      },
    );
  }

  const http: AxiosInstance = axios.create({ baseURL });
  const rootHttp: AxiosInstance = axios.create({ baseURL: platformBaseURL });
  attachInterceptors(http);
  attachInterceptors(rootHttp);

  async function request<T>(
    instance: AxiosInstance,
    method: string,
    url: string,
    payload?: unknown,
    opts?: RequestOptions,
  ): Promise<T> {
    const isQuery = method === 'GET' || method === 'DELETE';
    const config: AxiosRequestConfig = {
      method,
      url,
      signal: opts?.signal,
      ...(opts?.headers ? { headers: opts.headers } : {}),
      ...(isQuery
        ? { params: payload ?? opts?.params }
        : { data: payload, ...(opts?.params ? { params: opts.params } : {}) }),
    };
    const r = await instance.request<Result<T>>(config);
    const body = r.data;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        throw makeApiError(body.msg || '请求失败', { code: body.code, errors: body.errors });
      }
      return body.data;
    }
    // 非 Result 包装（纯文本 / 老接口）原样返回，别把 `body.data` 当契约硬套
    return body as T;
  }

  const client: HttpClient = {
    instance: http,

    get: <T,>(url: string, params?: Record<string, unknown>, opts?: RequestOptions) =>
      request<T>(http, 'GET', url, params, opts),
    post: <T,>(url: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(http, 'POST', url, body, opts),
    put: <T,>(url: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(http, 'PUT', url, body, opts),
    del: <T,>(url: string, params?: Record<string, unknown>, opts?: RequestOptions) =>
      request<T>(http, 'DELETE', url, params, opts),

    rootGet: <T,>(url: string, params?: Record<string, unknown>, opts?: RequestOptions) =>
      request<T>(rootHttp, 'GET', url, params, opts),
    rootPost: <T,>(url: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(rootHttp, 'POST', url, body, opts),
    rootPut: <T,>(url: string, body?: unknown, opts?: RequestOptions) =>
      request<T>(rootHttp, 'PUT', url, body, opts),
    rootDel: <T,>(url: string, params?: Record<string, unknown>, opts?: RequestOptions) =>
      request<T>(rootHttp, 'DELETE', url, params, opts),

    async downloadBlob(url, params, opts) {
      const r = await http.request<Blob>({
        method: 'GET',
        url,
        params,
        signal: opts?.signal,
        responseType: 'blob',
      });
      return r.data;
    },

    setAcceptLanguage(lang) {
      acceptLanguage = lang;
    },

    async fetchMe<T>(path: string): Promise<T | null> {
      try {
        const token = getToken();
        const r = await axios.get(path, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        const body = r.data;
        if (body && typeof body === 'object' && 'code' in body) {
          return body.code === 0 ? ((body as Result<T>).data ?? null) : null;
        }
        return (body as T) ?? null;
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 401) return null;
        throw normalizeError(error);
      }
    },

    streamUrl(path: string): string {
      const token = getToken();
      return token ? `${path}?token=${encodeURIComponent(token)}` : path;
    },
  };

  return client;
}
