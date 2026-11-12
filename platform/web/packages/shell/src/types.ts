import type { ComponentType, ReactNode } from 'react';

/**
 * 外壳的公共类型。
 *
 * <p><b>为什么菜单是数据模型而不是插槽</b>：三个 ap 的菜单来源完全不同
 * （IAM 走 `sys_menu` 后端下发、RMS 走前端静态配置、approval 也是静态配置），
 * 但<b>渲染形态完全一致</b>——分组标题 + 图标 + 文案 + 可选副标题 + 权限过滤。
 * 差异只在「怎么得到这份列表」，所以边界画在 `ShellNavGroup[]`：
 * 各 ap 各写一个十几行的映射函数，外壳只负责渲染。这样加一个菜单/改一个图标
 * 在外壳里改一次，而不是三份 Layout 各改一次。</p>
 *
 * <p><b>字符串 vs key</b>：`label` / `cap` 收的是<b>已翻译</b>的字符串，不是 key。
 * 这与 `@cim/ui` 的 `titleKey` 约定看似相反，理由是渲染时机不同：
 * 数据组件的 columns 会被 `useMemo` 缓存，传译文会「切语言表头不变」；
 * 外壳的 label 在 Layout 每次 render 时重建（Layout 本身订阅了 i18n），
 * 切语言必然重算。反过来把 key 塞进外壳，就得让外壳认识五个 ap 的键空间。</p>
 */

/**
 * 图标组件签名。
 *
 * <p>用 `ComponentType<any>` 而不是 `{ width?: number }`：三个 ap 的图标集各自声明了
 * 不同宽窄的 props 类型——IAM 是 `ComponentType<SVGProps<SVGSVGElement>>`（width 为
 * `string | number`）、RMS 是 `(p: { width?: number }) => JSX.Element`、approval 是两者交叉。
 * 它们互不可赋值，唯一共同上界是 `any`。收窄只会逼调用方写 `as unknown as`，
 * 而外壳对图标的用法只有一种：传两个数字进去。类型在这里的收益小于它的摩擦。</p>
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type ShellIconCmp = ComponentType<any>;

/**
 * 一个侧栏条目。
 *
 * <p><b>树形 vs 平铺</b>：`children` 有值即为「上级节点」——渲染成可展开行，点击只展开/收起、
 * 不导航；叶子节点的 `path` 必填。这条模型是从后端菜单树（`sys_menu.parent_id` +
 * `MenuType.DIR`）照抄下来的：后端一直支持任意层级，只有外壳以前硬编码成
 * 「分组标题 + 平铺条目」两层，多出来的 DIR 到了前端就没地方放。</p>
 *
 * <p>`path` 因此是可选的：上级节点没有自己的页面。叶子缺 `path` 属于配置错误，
 * 渲染时打 warning 并跳过，不让它变成一个点了没反应的死链。</p>
 */
export interface ShellNavItem {
  /** 唯一键（菜单 id 或路由路径），用于 React key 与展开状态持久化。 */
  key: string;
  /** 路由路径，交给 NavLink。上级节点不传。 */
  path?: string;
  /** 已翻译的菜单名。 */
  label: string;
  /** 已翻译的副标题（IAM 用，其余 ap 不传即不渲染）。 */
  desc?: string;
  Icon: ShellIconCmp;
  /** 子菜单。有值即渲染为可展开节点（二级），其子项为三级。 */
  children?: ShellNavItem[];
}

/** 一个侧栏分组；`cap` 为空表示顶级菜单自成一档、不显示标题。 */
export interface ShellNavGroup {
  key: string;
  cap?: string;
  items: ShellNavItem[];
}

/** 品牌区：字标 + 产品名 + 副标题。 */
export interface ShellBrand {
  /** 方形字标里的 1–2 个字符，如 `IA` / `RM` / `AP`。 */
  logo: string;
  title: string;
  subtitle: string;
}

/** 右上角用户块；`null` 表示该 ap 无登录态（approval 的 dev 形态）。 */
export interface ShellUser {
  name: string;
  /** 第二行说明，如 `tenant:t1` / `local`。 */
  meta?: string;
}

/** 环境标识（构建模式）。文案由应用算好，外壳不读 `import.meta.env`。 */
export interface ShellEnv {
  label: string;
  tone: 'ok' | 'warn';
  title?: string;
}

/** 外壳文案。全部必填：缺一个 TS 就报错，不允许运行期出现空按钮。 */
export interface ShellLabels {
  toggleNav: string;
  toggleGroup: string;
  toggleTheme: string;
  /** 当前是浅色主题 → 按钮提示「切到暗色」。 */
  themeToDark: string;
  /** 当前是暗色主题 → 按钮提示「切到浅色」。 */
  themeToLight: string;
  logout: string;
  tabs: ShellTabLabels;
}

/** 页签条文案。 */
export interface ShellTabLabels {
  label: string;
  scrollLeft: string;
  scrollRight: string;
  close: string;
  closeThis: string;
  closeOthers: string;
  closeRight: string;
  closeAll: string;
}

/** 一个页签：标题由应用解析好（菜单库/导航配置优先，回退开签快照）。 */
export interface ShellTab {
  path: string;
  label: string;
  /** 固定签（首页）：不可关闭，右键「关闭标签页」禁用。 */
  pinned?: boolean;
}

/** 页签条控制器：应用把自己的 store 适配成这组回调，外壳不碰状态。 */
export interface ShellTabs {
  tabs: ShellTab[];
  activePath: string;
  /**
   * 额外重算几何的触发值（路由 `location.key` / 语言 / 滚动票证）。
   * 值变化即重测指示器与滚入位置。
   */
  syncToken?: unknown;
  onSelect: (path: string) => void;
  onClose: (path: string) => void;
  onCloseOthers: (path: string) => void;
  onCloseRight: (path: string) => void;
  onCloseAll: () => void;
}

/** 顶栏右侧插槽能放什么——只做类型说明，运行时就是 ReactNode。 */
export type ShellSlot = ReactNode;
