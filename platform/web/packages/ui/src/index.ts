// @cim/ui 组件入口。
//
// 分层依赖（只允许自上而下）：
//   theme ← ui ← { data, i18n } ← shell
// 本包只依赖 @cim/theme 与 antd，**不得** import @cim/data 或 @cim/i18n：
//   - 翻译由 UiLocaleProvider 注入（见 locale.tsx），组件不知道 i18n 存在；
//   - 权限由 UiPermissionProvider 注入（perm.tsx），组件不读各 ap 的 store；
//   - 字典由 UiDictProvider 注入（dict.tsx），组件不直接调后端接口；
//   - 取数由调用方完成，组件只收 rows / loading / error。
//
// 样式入口：应用 `import '@cim/ui/index.css'`（须早于本应用自己的 styles.css）。
// 设计说明：docs/platform/web/component-platform-design.md
//
// 命名约定：组件一律 `Cim` 前缀（参考项目用 Goat 前缀，本项目不沿用）。

// ── 应用上下文注入器 ─────────────────────────────────────────────
export { CimProvider, useAntdTheme } from './provider';
export {
  UiLocaleProvider,
  useUiLabel,
  useUiLocale,
  useUiT,
  type UiLocaleValue,
  type UiTranslate,
} from './locale';
export { UiPermissionProvider, useCan, usePermissionContext, Can } from './perm';
export {
  UiDictProvider,
  useDict,
  useDictContext,
  EnumTag,
  type DictItem,
  type UiDictValue,
} from './dict';

// ── basic 基础 ──────────────────────────────────────────────────
export { CimButton, type CimButtonProps } from './basic/Button';
export { CimSpin, type CimSpinProps } from './basic/Spin';
export { CimTag, CimCheckTag, type CimTagProps, type CimCheckTagProps } from './basic/Tag';

// ── content 容器 ────────────────────────────────────────────────
export { EmptyState, Page, PageHead, Panel, Toolbar } from './content/Page';
export { CimDivider, type CimDividerProps } from './content/Divider';
export { CimDrawer, type CimDrawerProps } from './content/Drawer';
export { CimScrollArea, type CimScrollAreaProps } from './content/ScrollArea';

// ── data 数据展示 ───────────────────────────────────────────────
export { DataTable, type DataTableColumn, type DataTableProps, type SortDir } from './data/DataTable';
export { StatusTag, ToneTag } from './data/StatusTag';
export { KpiCard, KpiGrid, type KpiCardProps, type KpiTone } from './data/KpiCard';
export { useColumns, type ColumnMeta, type ColumnState } from './data/useColumns';
export { CimDescriptions, type DescItem, type CimDescriptionsProps } from './data/Descriptions';
export { CimInfoTable, type InfoItem, type CimInfoTableProps } from './data/InfoTable';
export { CimSteps, type StepItem, type CimStepsProps } from './data/Steps';
export { CimTabs, type TabItem, type CimTabsProps } from './data/Tabs';
export { CimTree, type CimTreeNode, type CimTreeProps } from './data/Tree';
export { CimCollapse, type CollapseItem, type CimCollapseProps } from './data/Collapse';

// ── form 表单 ──────────────────────────────────────────────────
export { FormGrid, type FormField, type FormGridProps, type FormOption } from './form/FormGrid';
export { CimField, type CimFieldProps } from './form/Field';
export { CimInput, type CimInputProps } from './form/Input';
export { CimInputNumber, type CimInputNumberProps } from './form/InputNumber';
export { CimSelect, type CimOption, type CimSelectProps } from './form/Select';
export {
  CimDatePicker,
  CimDateRangePicker,
  CimTimePicker,
  type CimDatePickerProps,
  type CimTimePickerProps,
} from './form/DatePicker';
export { CimSwitch, type CimSwitchProps } from './form/Switch';
export { CimChoice, type CimChoiceProps, type ChoiceMode } from './form/Choice';
export { CimUpload, type CimUploadProps } from './form/Upload';

// ── feedback 反馈 ──────────────────────────────────────────────
export { CimModal, type CimModalProps } from './feedback/CimModal';
export {
  UiToastProvider,
  useToast,
  useConfirm,
  type CimToast,
  type CimConfirm,
  type CimConfirmOptions,
} from './feedback/message';
export { InlineAlert, type AlertTone, type InlineAlertProps } from './feedback/InlineAlert';
export { CimTooltip, type CimTooltipProps } from './feedback/Tooltip';
export { CimPopconfirm, type CimPopconfirmProps } from './feedback/Popconfirm';
export { CimPopover, type CimPopoverProps } from './feedback/Popover';

// ── layout 布局 ────────────────────────────────────────────────
export {
  CimSplitter,
  type CimSplitterProps,
  type SplitterPanelConfig,
  type SplitterPanelSize,
} from './layout/Splitter';

// ── 通用图标（内联 SVG，跨应用共用）─────────────────────────────
export {
  IconGauge,
  IconApps,
  IconKick,
  IconUser,
  IconSidebar,
  IconSun,
  IconMoon,
  IconLogout,
  IconShield,
  IconKey,
  IconAlert,
  IconCheckCircle,
  IconInbox,
  IconRefresh,
  IconEye,
  IconEyeOff,
  IconSearch,
  IconUsers,
  IconLock,
  IconLink,
  IconMonitor,
  IconList,
  IconSliders,
  IconPlus,
  IconTrash,
  IconBan,
  IconPower,
  IconTree,
  IconOrgGrant,
  IconChevron,
  IconX,
  IconEdit,
  IconSync,
  IconSave,
} from './icons';
