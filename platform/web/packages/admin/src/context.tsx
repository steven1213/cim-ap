// @cim/admin 运行时上下文 —— 宿主注入 http 与权限判定，包内零应用耦合。
//
// 六页组件全部经 useAdminHttp() / useAdminPerm() 取数与判权，不 import 任何
// 具体 ap 的 api/authStore。宿主在路由外层包一次 <AdminProvider>：
//   <AdminProvider http={api} hasAll={(codes) => hasAllPermissions(codes)}>
//     <MenusAdminPage />
//   </AdminProvider>
// 其中 http 只需 get/post/put/del 四个动词（baseURL 已含 /api/v1 的实例产物）。
import { createContext, useContext, type ReactNode } from 'react';

/** 宿主 http 实例的最小面（createHttpClient 产物的子集，路径已含 /api/v1 前缀）。 */
export interface AdminHttp {
  get<T>(path: string, params?: Record<string, unknown>, config?: { signal?: AbortSignal }): Promise<T | undefined>;
  post<T>(path: string, body?: unknown, config?: { signal?: AbortSignal }): Promise<T | undefined>;
  put<T>(path: string, body?: unknown, config?: { signal?: AbortSignal }): Promise<T | undefined>;
  del<T>(path: string, config?: { signal?: AbortSignal }): Promise<T | undefined>;
}

/** 权限判定：给定一组码，当前用户是否<b>全部</b>具备（空数组 = 无条件通过）。 */
export type AdminHasAll = (codes: string[]) => boolean;

interface AdminContextValue {
  http: AdminHttp;
  hasAll: AdminHasAll;
}

const AdminContext = createContext<AdminContextValue | null>(null);

export function AdminProvider(props: {
  http: AdminHttp;
  /** 缺省实现：空数组恒 true（即页面不做额外判权，仅靠菜单入口门禁）。 */
  hasAll?: AdminHasAll;
  children: ReactNode;
}) {
  const { http, hasAll, children } = props;
  return (
    <AdminContext.Provider value={{ http, hasAll: hasAll ?? ((codes) => !codes || codes.length === 0) }}>
      {children}
    </AdminContext.Provider>
  );
}

/** 取宿主 http 实例；未包 Provider 直接抛错（编程期错误，不留静默空实现）。 */
export function useAdminHttp(): AdminHttp {
  const ctx = useContext(AdminContext);
  if (!ctx) throw new Error('[cim/admin] 页面必须在 <AdminProvider> 内使用');
  return ctx.http;
}

/** 取权限判定函数。 */
export function useAdminHasAll(): AdminHasAll {
  const ctx = useContext(AdminContext);
  if (!ctx) throw new Error('[cim/admin] 页面必须在 <AdminProvider> 内使用');
  return ctx.hasAll;
}
