// 全局分页默认配置：每页条数候选（sizeOptions）与应用级默认每页条数（defaultSize）。
//
// 为什么是模块级可写配置而不是 Provider：DataTable 是纯渲染层（铁律：不读 store），
// 分页选项属于「应用启动时定一次、全局不变」的静态约定，走 Provider 徒增层级；
// 入口 main.tsx 调一次 configurePagination，所有 DataTable 的分页器随之生效，
// 页面仍可通过 `pagination.sizeOptions` 逐页覆盖（如词典管理页固定 50）。

export interface PaginationDefaults {
  /** 每页条数候选（分页器 sizeChanger 的选项）。 */
  sizeOptions: number[];
  /** 页面未显式指定时的默认每页条数（应取 sizeOptions 之一）。 */
  defaultSize: number;
}

const DEFAULTS: PaginationDefaults = {
  sizeOptions: [10, 30, 50],
  defaultSize: 30,
};

let current: PaginationDefaults = { ...DEFAULTS };

/** 应用入口统一配置（main.tsx 调一次；部分覆盖，未指定的键保持现值）。 */
export function configurePagination(partial: Partial<PaginationDefaults>): void {
  current = { ...current, ...partial };
  if (!current.sizeOptions.includes(current.defaultSize)) {
    current.defaultSize = current.sizeOptions[0];
  }
}

/** 当前全局默认（DataTable 兜底 / 页面初始 size 取值用）。 */
export function getPaginationDefaults(): PaginationDefaults {
  return current;
}

/** 测试隔离用：恢复出厂默认。 */
export function resetPaginationDefaults(): void {
  current = { ...DEFAULTS };
}
