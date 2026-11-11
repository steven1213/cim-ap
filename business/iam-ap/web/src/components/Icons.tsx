// 图标出口（薄壳）。
//
// 34 个通用图标的实现已上提到 `@cim/ui`（`platform/web/packages/ui/src/icons.tsx`）：
// 本文件与 RMS 的同名文件此前各存一份，`<svg>` 子节点逐字节相同，净重复 ~700 行。
// 这里保留同名出口，页面里的 `from '@/components/Icons'` 无需改动（迁移面为零）。
//
// 新加图标前先判断：通用语义（用户/新增/删除/刷新…）→ 加到 `@cim/ui`；
// 只有本应用懂的领域图标 → 才在本文件本地实现并 export。
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
} from '@cim/ui';
