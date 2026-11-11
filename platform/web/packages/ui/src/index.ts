// @cim/ui 组件入口。
//
// P0 阶段只迁入样式（令牌 + 基础 + 外壳 + 通用组件样式），应用侧按
// `import '@cim/ui/index.css'`（须早于本应用自己的 styles.css）使用。
// 组件自 P1 起落地，首个是 DataTable（虚拟滚动 + 服务端分页 + 空/错/载三态），
// 契约见 docs/platform/web/component-platform-design.md §7。
//
// 铁律：本包不得 import @cim/data 或 @cim/i18n——组件保持纯展示。
export {};
