// 数据层的公共类型。
//
// `ApiError` 刻意做成「Error 的可选扩展」而不是联合类型：各 ap 的 api.ts 各自构造错误
// （RMS/IAM 读 `{code,msg,errors}`，approval 还要兼容裸数据与 Spring 默认错误体），
// 前端只需保证「是 Error，且可能带 code/errors/status」——页面据此决定提示粒度。
// 若在这里定义成闭合的类，就要求所有 ap 改成继承同一个类，等于把业务 API 层统一了，
// 而统一业务 API 层是非目标（设计稿 §11）。

export interface ApiError extends Error {
  /** 后端业务码（`Result.code`），HTTP 200 也可能带非 0。 */
  code?: number;
  /** 字段级错误，键为字段名（后端 `Result.errors`）。 */
  errors?: Record<string, string>;
  /** HTTP 状态码（若有）。 */
  status?: number;
}

/** 把任意 throw 出来的东西收敛成 `ApiError`（页面不必写 `instanceof` 判断）。 */
export function toApiError(e: unknown): ApiError {
  if (e instanceof Error) return e as ApiError;
  if (typeof e === 'string') return new Error(e) as ApiError;
  return new Error('请求失败') as ApiError;
}

/**
 * 判断一个错误是否来自「主动取消」。
 *
 * <p>axios 取消抛 `CanceledError`（`code === 'ERR_CANCELED'`），fetch 抛 `DOMException`
 * （`name === 'AbortError'`）。两种都要识别，否则切筛选时会把「取消」当成「请求失败」，
 * 页面闪一下红色错误条。</p>
 */
export function isAbortError(e: unknown): boolean {
  const anyE = e as { name?: string; code?: string } | null;
  return anyE?.name === 'AbortError' || anyE?.code === 'ERR_CANCELED';
}
