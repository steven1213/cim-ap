// @cim/ui 组件入口。
//
// 分层依赖（只允许自上而下）：
//   theme ← ui ← { data, i18n } ← shell
// 本包只依赖 @cim/theme 与 antd，**不得** import @cim/data 或 @cim/i18n：
//   - 翻译由 UiLocaleProvider 注入（见 locale.tsx），组件不知道 i18n 存在；
//   - 取数由调用方完成，组件只收 rows / loading / error。
//
// 样式入口：应用 `import '@cim/ui/index.css'`（须早于本应用自己的 styles.css）。
// 设计说明：docs/platform/web/component-platform-design.md

// 语言注入与 antd 主题适配
export { CimProvider, useAntdTheme } from './provider';
export {
  UiLocaleProvider,
  useUiLabel,
  useUiLocale,
  useUiT,
  type UiLocaleValue,
  type UiTranslate,
} from './locale';

// 页面骨架
export { EmptyState, Page, PageHead, Panel, Toolbar } from './content/Page';

// 数据展示
export { DataTable, type DataTableColumn, type DataTableProps, type SortDir } from './data/DataTable';
export { StatusTag, ToneTag } from './data/StatusTag';
export { useColumns, type ColumnMeta, type ColumnState } from './data/useColumns';

// 表单
export { FormGrid, type FormField, type FormGridProps, type FormOption } from './form/FormGrid';

// 反馈
export { CimModal, type CimModalProps } from './feedback/CimModal';
