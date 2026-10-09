// 统一 API 客户端：axios 实例 + 请求/响应拦截器。
//
// - 请求拦截：自动附带 Bearer 令牌（来自 authStore）。
// - 响应拦截：解包 Result<T>（code===0 视为成功，取 data；否则抛 {code,msg,errors}）；
//   401 时清态并跳登录（登录页本身不跳，避免循环）。
import axios, { type AxiosInstance } from 'axios';
import { useAuthStore } from '@/store/authStore';

/** 与 server 端 Result<T> 对齐。 */
export interface Result<T> {
  code: number;
  msg: string;
  data: T;
  errors?: Record<string, string>;
}

const http: AxiosInstance = axios.create({ baseURL: '/api' });

http.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
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

function assertOk<T>(body: Result<T>): void {
  if (body.code !== 0) {
    const e: any = new Error(body.msg || '请求失败');
    e.code = body.code;
    e.errors = body.errors;
    throw e;
  }
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const r = await http.get<Result<T>>(url, { params });
  assertOk(r.data);
  return r.data.data;
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const r = await http.post<Result<T>>(url, body);
  assertOk(r.data);
  return r.data.data;
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const r = await http.put<Result<T>>(url, body);
  assertOk(r.data);
  return r.data.data;
}

export async function del<T>(url: string): Promise<T> {
  const r = await http.delete<Result<T>>(url);
  assertOk(r.data);
  return r.data.data;
}

/** 透传原始响应体（不按 Result 解包），用于返回裸对象 / 非 Result 的端点（如 token-version bump）。 */
export async function postRaw<T>(url: string, body?: unknown): Promise<T> {
  const r = await http.post<T>(url, body);
  return r.data;
}

export default { get, post, put, del, postRaw };
