# 前端组件中台设计（多 ap 共享组件 · 主题 · 数据 · 翻译）

> 状态：**P0–P4 全部落地（2026-11-11）**。实现细节与实测数据见 [§10](#10-落地批次与验收标准)：P0 主题/样式、P1 组件层、P2 消息层与拆包、P3 外壳层（`@cim/shell`）、P4 数据层（`@cim/data`）+ 首批页面迁移。
> 关系：[`README.md`](./README.md) 是**单应用**的前端基础框架设计（请求层/认证/权限/状态/i18n/主题的分章详述），本文是**多 ap 共享**视角的组件中台设计，两者不冲突：README 讲「一个应用内部怎么搭」，本文讲「五个应用的公共部分怎么抽、怎么发、怎么用」。
> 冲突时以本文为准的情形：包边界、依赖方向、跨应用契约。README 的 §1–§13 仍是请求层/i18n/权限的**实现细节权威源**。

## 目录

- [1. 为什么现在做（实测数据）](#1-为什么现在做实测数据)
- [2. 三条设计铁律](#2-三条设计铁律)
- [3. 包结构与依赖方向](#3-包结构与依赖方向)
- [4. @cim/theme：主题不是皮肤](#4-cimtheme主题不是皮肤)
- [5. @cim/data：数据加载的唯一入口](#5-cimdata数据加载的唯一入口)
- [6. @cim/i18n：文案、字典、缺键](#6-cimi18n文案字典缺键)
- [7. @cim/ui：组件清单与分批](#7-cimui组件清单与分批)
- [8. @cim/shell：外壳是最后一个](#8-cimshell外壳是最后一个)
- [9. 工程化：workspaces 源码直引](#9-工程化workspaces-源码直引)
- [10. 落地批次与验收标准](#10-落地批次与验收标准)
- [11. 非目标](#11-非目标)
- [12. 待定项](#12-待定项)

---

## 1. 为什么现在做（实测数据）

| 症状 | 实测 | 代价 |
| --- | --- | --- |
| 平台前端是空壳 | `platform/web` 仅 102 行，`package.json` 选了 antd 5.21 + rhf + zod，业务侧 **0 处使用** antd | 两套技术栈并存，视觉无法统一 |
| 三个应用各自为政 | iam / rms / approval 共 38.8k 行 | 同一需求做三遍 |
| 5 个文件逐字节相同 | `format.ts`、`theme.ts`、`usePermission.ts`、`Perms.tsx`、`ProtectedRoute.tsx`（md5 一致） | 改一次要改三处，漏一处即漂移 |
| 4 个文件已开始分叉 | `Icons.tsx` 348/375/235、`TabBar` 296/295/257、`Panel`、`LanguageSwitcher`；**`Layout` 已分叉为 288/178/156** | 无人拥有的代码必然漂移 |
| i18n 三套实现 | IAM 后端驱动 230 行（四级兜底+缺键上报+整包缓存）；RMS 本地包 126 行；approval 73 行 | 同一概念三种行为，切语言表现不一致 |
| 请求层靠拷贝 | rms `api.ts` 由 IAM 拷来，注释仍写 `/api/v1/admin/users` | 契约变更时无人知会 |
| 无数据加载抽象 | 各页手写 `const [loading] = useState(false)`，34+ 处 | 无竞态防护、无去重、无轮询 |
| 语义色散落 | 三个 `styles.css` 共 221 处硬编码 hex | 同一个「设备停机」三种绿，属**认知债** |

结论：缺的不是「再写一套组件」，而是**一份让五个系统都能挂上去的接口规范**。

---

## 2. 三条设计铁律

### 2.1 组件不碰数据，也不碰文案

五个 ap 的后端契约完全不同（RMS 配方、MES 工单、MDS 设备、approval 审批单）。组件一旦内置 `fetch` 或 `t()`，就绑定死其中一家的接口与文案键空间。

**正确形态**：组件只接收 `rows / columns / loading / error`；数据由 hook 在外层灌入，文案靠 **key** 注入。

```tsx
const { rows, loading, error, refresh, lastUpdated } = useResource(
  ({ signal }) => rmsApi.listRecipes({ page, size, keyword }, signal),
  { deps: [page, size, keyword], refetchInterval: 15_000 },
);

<DataTable
  columns={[
    { key: 'code',  titleKey: 'rms.recipe.code',  width: 160 },
    { key: 'state', titleKey: 'rms.recipe.state',
      render: (r) => <EnumTag dict="recipeState" value={r.state} /> },
  ]}
  rows={rows} loading={loading} error={error} onRefresh={refresh}
/>
```

**为什么 `titleKey` 而不是 `title: t('...')`**：`t()` 在 `columns` 构造时求值，结果被数组持有；切换语言时表头不会重算。这是本设计里最容易踩的坑，必须由类型系统挡住（`titleKey: string`，禁止 `title: ReactNode` 走文案）。

**反例（禁止）**：

```tsx
// ❌ 组件内部请求：把后端契约焊进平台包
<RecipeTable toolId={id} />          // 内部 await rmsApi.list(...)
// ❌ 组件内部翻译：绑死键空间
<DataTable columns={[{ title: t('rms.recipe.code') }]} />
```

### 2.2 主题是 tokens，不是皮肤

产线场景要求**同一套组件在三种场合成立**：

| 场合 | 字号 | 点击区 | 输入设备 |
| --- | --- | --- | --- |
| 办公室后台 | 13px | ≥28px | 鼠标 |
| 洁净室触屏终端 | ≥16px | **≥40px** | 手套触控 |
| 车间常驻大屏 | ≥18px，高对比 | 只读为主 | 无 |

因此必须支持 `data-cim-density="compact | normal | touch"` + 明暗 + 高对比。**组件内禁止出现裸色值/裸 px**，全部走 `var(--cim-*)`。

### 2.3 语义状态色属平台，不属业务

设备 `UP/DOWN/PM/HOLD/ENG`、配方 `DRAFT/PENDING_SIGN/ACTIVE/OBSOLETE`、批次 `WAIT/RUN/HOLD/SCRAP`、告警三级——这些是全厂公共语言。各 ap 自定色，等于同一状态换个页面就换个含义。

**取色必须按语义，不按颜色名**：

```tsx
// ❌ 按颜色取：RUN 用绿、ACTIVE 用绿，但 RUN 是"进行中"、ACTIVE 是"已生效"
<span style={{ color: 'green' }}>
// ✅ 按语义取
<EnumTag dict="batchState" value="RUN" />   // → --cim-status-run，蓝色，非绿
```

语义档只有 5 个：`ok` / `warn` / `err` / `run`（进行中）/ `idle`（停用/历史）。每域一张映射表，表在 `@cim/theme` 里，不在业务代码里。

---

## 3. 包结构与依赖方向

```
@cim/theme   ← 无依赖，纯 CSS 变量 + 状态映射表
@cim/ui      ← 依赖 theme + antd(peer)（严禁依赖 data / i18n）
@cim/data    ← 只依赖 react（useResource / useDebounced / ApiError）
@cim/i18n    ← 依赖 i18next + @cim/data（拉译文/字典）+ theme（EnumTag 用色）   [未建]
@cim/shell   ← 依赖 theme + ui + react-router-dom(peer) + zustand(peer)
```

> **落地进度**：P0 建了 `@cim/theme` 与 `@cim/ui`（样式）；P1 落地组件层；P2 消息层 + 虚拟滚动 + 路由拆包；**P3 建了 `@cim/shell`**（`ConsoleShell` / `TabStrip` / `createTabStore` / `useShellTabs`），三个应用的外壳改为薄适配层，`shell.css` 仍留在 `@cim/ui`（样式与组件同源，见 §10.1 偏差表）；**P4 建了 `@cim/data`**（`useResource` / `useDebounced` / `ApiError`，见 §10.6），RMS 首批 3 页迁到组件层。
>
> `@cim/i18n` 仍未建，**`@cim/data` 也不含 HTTP 传输层**：原设计写的「依赖 axios + zustand（读 token）」没有落地，因为 RMS/IAM 的 `lib/api.ts` 逐行相同、approval 的契约却不同（裸数据 + 操作人放 body），合并要么逼 approval 改契约、要么让公共层长开关。现状是各 ap 的请求层与 i18n 各自保留（见 §11 非目标、§12-2），`@cim/data` 只提供与传输无关的取数原语。

**依赖规则（CI 可校验）**：

| 规则 | 理由 |
| --- | --- |
| `ui` 不得 import `data` / `i18n` | 组件保持纯展示，否则又回到「组件内置请求」 |
| `ui` 可依赖 antd，**业务代码不得直接 import antd** | antd 是组件的内部实现细节：换掉它只需改 `@cim/ui`，页面不动 |
| `theme` 不得 import 任何包 | tokens 是最底层，反向依赖会造成循环 |
| `data` / `i18n` 不得 import `ui` | 数据/翻译层不应感知组件 |
| `shell` 只依赖 `ui` + `theme`，**不依赖 data / i18n** | 外壳的菜单是数据模型、文案是注入字符串（见 §8），不需要请求层与翻译层 |
| `shell` **不 re-export `@cim/ui`** | 原计划「业务只 import shell」已放弃：re-export 会让「只要外壳」的入口被迫静态加载全部组件（含 antd）。现在两个入口各司其职：结构用 `@cim/shell`，控件用 `@cim/ui` |
| 业务 ap 不 import `TabStrip` 之外的外壳内部文件 | 单点入口，避免绕过 `ConsoleShell` 自己拼 DOM 造成二次分叉 |

---

## 4. @cim/theme：主题不是皮肤

### 4.1 Token 分层

| 层 | 变量族 | 示例 |
| --- | --- | --- |
| 原语 | `--cim-gray-{50..900}`、`--cim-blue-{...}` | 不直接被业务使用 |
| 语义 | `--cim-bg-{page,surface,raised}`、`--cim-text-{primary,secondary,tertiary}`、`--cim-border-*`、`--cim-primary` | 组件只用这一层 |
| 尺寸 | `--cim-space-{1..8}`、`--cim-radius-*`、`--cim-font-*`、`--cim-row-h` | 密度档改这组 |
| 状态 | `--cim-status-{ok,warn,err,run,idle}` + `-bg` 浅底 | 由 §2.3 映射表消费 |

### 4.2 密度档切换

```css
:root                        { --cim-font-base: 13px; --cim-row-h: 32px; --cim-hit: 28px; }
[data-cim-density="compact"] { --cim-font-base: 12px; --cim-row-h: 28px; --cim-hit: 24px; }
[data-cim-density="touch"]   { --cim-font-base: 16px; --cim-row-h: 52px; --cim-hit: 40px; }
[data-cim-theme="dark"]      { --cim-bg-page: #0d1117; --cim-text-primary: #e6edf3; /* … */ }
```

切换是**运行时**的（改 `<html>` 上的属性 + 落 localStorage），不重新构建。密度档与语言一样属「非敏感偏好」，可持久化；令牌不可持久化（README §5）。

### 4.3 反例

```tsx
// ❌ 硬编码
<div style={{ padding: 12, color: '#1f2329' }}>
// ✅
<div className="cim-card">   /* 内部用 var(--cim-space-3) / var(--cim-text-primary) */
```

> 门禁：Stylelint 禁止 `.css` 内出现 `#hex`/`rgb()`（`@cim/theme` 自身除外）；ESLint 禁止 JSX `style` 里的颜色/尺寸字面量。

---

## 5. @cim/data：数据加载的唯一入口

### 5.1 两个实例（沿用既成事实）

| 实例 | baseURL | 用途 |
| --- | --- | --- |
| `api` | `/api/v1` | 本 ap 自身管理面 |
| `rootApi` | `''` | 平台端点 `/sys/**`（含字典）、`/api/i18n/**` |

两个实例共用拦截器：附 `Bearer`、附 `Accept-Language`、解包 `Result<T>`（`code !== 0` 抛带 `code/errors` 的 Error）、401 清态跳登录。

> ⚠️ 踩坑记录：axios 的 `buildFullPath` 只在 url 为**绝对 URL**（带 scheme）时才跳过 baseURL 拼接，光写 `/sys/x` 仍会被拼成 `/api/v1/sys/x`——所以必须用独立实例，不能靠改调用写法。

### 5.2 `useResource` 语义（2026-11-11 已落地，见 §10.6）

```ts
function useResource<T>(
  fetcher: (ctx: { signal: AbortSignal }) => Promise<T>,
  opts?: {
    deps?: unknown[];              // 依赖变化即重取
    refetchInterval?: number;      // 毫秒；页面 hidden 时暂停，可见即补一次
    keepPreviousData?: boolean;    // 翻页时保留旧数据，避免表格闪空
    enabled?: boolean;             // 条件取数（如抽屉未打开不取）
  },
): { data?: T; loading: boolean; error?: ApiError; refresh(): void; lastUpdated?: number };
```

必须内建的 5 件事（现在各页手写 `useState` 全都缺）：

1. **竞态**：请求序号递增，只接受最新一次的回包（快速切页/切筛选时不串数据）
2. **去重**：相同 `deps + url` 的在途请求只发一次
3. **取消**：`deps` 变化/卸载时 `AbortController.abort()`，并忽略 `AbortError`
4. **轮询**：`document.hidden` 时暂停，恢复可见立即补一次（产线大屏常年开着，不能空转）
5. **不吞错**：`error` 交给页面决定是整页空态还是行内提示，不在 hook 里弹 Toast

### 5.3 分页契约

后端统一 `PageQuery` + `Page<T>`（`content / total / page / size`），前端不自己算 total，也不在前端做跨页过滤——**过滤与排序一律下推到服务端**。千行以上列表由 `DataTable` 虚拟滚动承载，不接受「一次拉全量再前端筛」。

---

## 6. @cim/i18n：文案、字典、缺键

### 6.1 三件不同的事，不要混

| 事项 | 例子 | 来源 | 变化频率 |
| --- | --- | --- | --- |
| UI 文案 | `rms.recipe.code` → 配方编码 | `cim-i18n` 后端包（`GET /api/i18n/messages`） | 运营可改，免发版 |
| 枚举字典 | `recipeState=DRAFT` → 草稿 | `sys_dict`（`GET /sys/dicts/code/{code}/items`，字段 `itemKey/itemValue`） | 主数据，需审计 |
| 缺键 | 四级兜底全未命中 | 上报 `POST /api/i18n/missing` | 生成待翻译工单 |

**关键区分**：UI 文案走 i18n，**枚举值不走 i18n**。把 `DRAFT → 草稿` 塞进 i18n 包会导致：新增枚举值要发翻译包、字典的启用/停用无法表达、后端枚举与前端键无法校验。字典的 `itemValue` 已经是当前语言的展示值，前端只负责按 `itemKey` 匹配 + 长文本截断。

### 6.2 运行时就绪：以 IAM 实现为基座

IAM 的 `lib/i18n.ts`（230 行）是三个实现里唯一完整的，直接上提为 `@cim/i18n`：

- 启动：先读 `localStorage` 整包同步渲染（防闪白）→ 再按版本比对拉取
- 四级兜底：目标语言 → `zh-CN`（服务端已合并进目标包）→ 内置最小包 → `humanize(key)`，**绝不渲染裸 key**
- 缺键：3s 批量去重上报
- 切换：写偏好 + 改 `Accept-Language` + 重拉包，**不刷新页面**

> ⚠️ 本地必须缓存**整包**而非仅版本号：端点约定「版本一致则只回版本、消息体为空」，只存版本号会在刷新后拿到空包导致界面全空。

### 6.3 键命名规范

`{ap}.{模块}.{对象}.{语义}`，全小写点分。豁免：平台通用词用 `common.*`，平台自身用 `sys.*`。

| 反例 | 问题 |
| --- | --- |
| `配方编码` 当 key | 改文案即改 key，译文全丢 |
| `rms.Recipe.Code` | 大小写混用，DB 主键大小写敏感 |
| `rms.recipe.code.label.for.table` | 语义堆叠，复用无望 |

---

## 7. @cim/ui：组件清单与分批

**已落地（P1 + 补齐批次，2026-11-11）**

命名：组件一律 `Cim` 前缀（参考项目用 `Goat`，本项目不沿用）；已占用语义名的领域组件直接用领域名（`Page` / `Panel` / `DataTable` / `StatusTag` / `FormGrid`），不加前缀以免读起来像第三方。

| 类目 | 组件 | 文件 | 职责 | 明确不做 |
| --- | --- | --- | --- | --- |
| 注入器 | `CimProvider` | `provider.tsx` | 读 `tokens.css` 变量生成 antd `ThemeConfig`，跟随 `data-theme`/`data-cim-density` | 不自立色板 |
| 注入器 | `UiLocaleProvider` / `useUiLabel` / `useUiT` | `locale.tsx` | 翻译函数注入，组件拿 `translate(key)` | 不 import `@cim/i18n` |
| 注入器 | `UiPermissionProvider` / `useCan` / `Can` | `perm.tsx` | 权限判定注入 | 不读各 ap 的 store |
| 注入器 | `UiDictProvider` / `useDict` / `EnumTag` | `dict.tsx` | 字典缓存（按 `code` 拉一次），`EnumTag` 值→标签 | 不直连后端（由调用方给 fetcher） |
| basic | `CimButton` | `basic/Button.tsx` | 按钮（primary/ghost/danger/尺寸/加载） | 不做权限包装（用 `Can`） |
| basic | `CimSpin` | `basic/Spin.tsx` | 加载指示（纯 CSS；`block` 用于路由兜底，保持内容区高度） | 不包裹内容、不做骨架屏 |
| basic | `CimTag` / `CimCheckTag` | `basic/Tag.tsx` | 标签 / 可选中标签（筛选条） | 不做颜色自选 |
| content | `Page` / `PageHead` / `Panel` / `Toolbar` / `EmptyState` | `content/Page.tsx` | 页面骨架（等价 `.page`/`.page-head`/`.panel`/`.toolbar`） | 不做布局系统，不接管路由 |
| content | `CimDivider` | `content/Divider.tsx` | 分隔线（水平/垂直/带文字） | — |
| content | `CimDrawer` | `content/Drawer.tsx` | 侧滑面板 | 不内置表单 |
| content | `CimScrollArea` | `content/ScrollArea.tsx` | 定高滚动容器（统一滚动条样式） | 不做虚拟滚动 |
| data | `DataTable` | `data/DataTable.tsx` | 渲染 + 工具栏 + 列设置 + 服务端分页 + 选择 + 三态 + 可选虚拟滚动 | **不取数、不导出、不做单元格编辑** |
| data | `useColumns` | `data/useColumns.ts` | 列显示/顺序 + localStorage 持久化 | 不碰服务端列定义（见 §12-4） |
| data | `StatusTag` / `ToneTag` | `data/StatusTag.tsx` | 枚举值 → 5 档语义色 | 调用方不能自选颜色 |
| data | `CimDescriptions` | `data/Descriptions.tsx` | 详情栅格（列数/跨列/空值占位） | 不做可编辑详情 |
| data | `CimInfoTable` | `data/InfoTable.tsx` | 紧凑键值表（配方参数/审计明细这类） | 不做分页 |
| data | `CimSteps` | `data/Steps.tsx` | 阶段流转条（当前/完成/失败） | 不做流程编排 |
| data | `CimTabs` | `data/Tabs.tsx` | 页签（受控/非受控） | 不做路由联动 |
| data | `CimTree` | `data/Tree.tsx` | 组织树/菜单树（展开、勾选、拖拽排序） | 不加载数据 |
| data | `CimCollapse` | `data/Collapse.tsx` | 折叠面板（分组表单/日志段） | — |
| form | `FormGrid` | `form/FormGrid.tsx` | 配置驱动表单，判别联合 `kind`：text/password/textarea/number/select/switch/date/custom | 不做校验规则 DSL |
| form | `CimField` | `form/Field.tsx` | 单字段外壳（标签/必填/错误/提示/单位） | 不做布局 |
| form | `CimInput` | `form/Input.tsx` | 输入框（前后缀、单位、清除） | — |
| form | `CimInputNumber` | `form/InputNumber.tsx` | 数字输入（精度/步进/单位/空值） | — |
| form | `CimSelect` | `form/Select.tsx` | 下拉（可搜索/多选/清空/分组） | 不内置字典（用 `useDict` 传 options） |
| form | `CimDatePicker` / `CimDateRangePicker` / `CimTimePicker` | `form/DatePicker.tsx` | 日期/区间/时间，值统一为 ISO 字符串 | 不暴露 dayjs 对象给业务 |
| form | `CimSwitch` | `form/Switch.tsx` | 开关（带文案位） | — |
| form | `CimChoice` | `form/Choice.tsx` | 单选/多选组（按钮式/卡片式） | 不做联动 |
| form | `CimUpload` | `form/Upload.tsx` | 上传（手动/自动、大小与类型限制、进度） | 不决定存储位置，由调用方给 action |
| feedback | `CimModal` | `feedback/CimModal.tsx` | 弹窗（焦点/Esc/滚动锁由 antd 保证） | 不内置表单 |
| feedback | `UiToastProvider` / `useToast` / `useConfirm` | `feedback/message.tsx` | 全局消息与二次确认（`App.useApp()` 上下文版，继承主题） | 不用 antd 静态 message、不用 `window.confirm` |
| feedback | `CimTooltip` | `feedback/Tooltip.tsx` | 文字提示（超长省略号场景） | — |
| feedback | `CimPopconfirm` | `feedback/Popconfirm.tsx` | 气泡确认（危险动作） | 不做异步 loading 编排 |
| feedback | `CimPopover` | `feedback/Popover.tsx` | 气泡卡片 | — |
| layout | `CimSplitter` | `layout/Splitter.tsx` | 可拖拽分栏，**配置驱动** `panels` | 不用 children（antd `Splitter.Panel` 是 `() => null`，隔层即失效） |

`DataTable` 的实际契约：

```ts
interface DataTableProps<T> {
  columns: Array<{
    key: string;
    titleKey: string;                 // 必填：不给 `title: ReactNode` 的口子
    width?: number; align?: 'left' | 'center' | 'right';
    mono?: boolean;                   // 编码/时间戳等宽
    hiddenByDefault?: boolean;
    render?: (row: T) => ReactNode;
    sortable?: boolean;               // 必须配合 onSortChange，否则不生效
  }>;
  rows: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  error?: string | null;              // 非空 → 整块替换为可重试错误态
  onRetry?: () => void;
  actions?: ReactNode;                // 工具栏左侧
  search?: { value: string; onChange: (v: string) => void; placeholderKey?: string };
  onRefresh?: () => void;
  settingsKey?: string;               // 传了才启用列设置（含持久化）
  selectedKeys?: string[];
  onSelectionChange?: (keys: string[]) => void;
  pagination?: { page: number; size: number; total: number; onChange: (p, s) => void };
  sort?: { key: string; dir: 'asc' | 'desc' };
  onSortChange?: (key: string, dir: 'asc' | 'desc' | null) => void;
  rowClassName?: (row: T) => string;  // 行语义色仍走 tokens
}
```

**后续批次**

| 批次 | 组件 | 说明 |
| --- | --- | --- |
| P2 | 骨架屏 / 空态插图 | 现在只有文字空态；大屏与弱网现场反馈后再做 |
| P3 | `Layout`、`TabBar`、`MenuTree`、`LanguageSwitcher`、`NotificationBell` | 见 §8，最后做 |

---

## 8. @cim/shell：外壳是最后一个

`Layout` 三份已分叉（288/178/156），且**菜单结构本身不同**（IAM 四段 / RMS 三组 / approval 四组）。所以先做组件、后做外壳。

### 8.1 定稿：配置驱动 + 插槽兜底（2026-11-11，关闭 §12-1）

动手前实测三份 `Layout` 的差异，结论比预想的干净——**骨架逐行相同，差异只有两处**：

| 差异点 | IAM | RMS | approval | 归属 |
| --- | --- | --- | --- | --- |
| 菜单来源 | 后端 `sys_menu` 树 | 前端静态 `GROUPS` | 前端静态 `NAV_GROUPS` | **数据** → 映射成 `ShellNavGroup[]` |
| 菜单项形态 | 图标 + 文案 + 副标题（`.desc`） | 图标 + 文案 | 图标 + 文案 | 数据（`desc?` 可选） |
| 分组头 | 可折叠（持久化） | 静态 | 静态 | `collapsibleGroups` 开关 |
| 顶栏右侧 | 超管徽标 + 语言 | 超管徽标 + 通知铃 + 语言 | 身份切换 + 通知铃 + 语言 | **插槽** → `roleBadge` + `topbarExtra` |
| 用户块 | 有 | 有 | 无 | `user?: null` |
| 窄屏 | ≤900px 抽屉 | 收缩图标态 | 收缩图标态 | `mobileBreakpoint?` |
| 登出 | 清菜单+页签→/login | 清页签→/login | 清身份→/ | 回调 `onLogout` |

判据一句话：**结构性差异进模型，内容性差异进插槽**。菜单的「怎么来」是结构、菜单的「长什么样」也是结构（五家都一样），只有顶栏右侧那块是真·内容（每家放的东西不同），所以模型 + 一个插槽就够，不需要整套 `<Shell.Side>` 组合件。

**为什么组合件在这里是错的**：组合件意味着每个 ap 自己写「遍历分组 → 过滤权限 → 判激活态 → 渲染图标/文案/副标题」这段循环，三份还是三份，只是把 `<div>` 换成了 `<Shell.Side>`。§10.3 记录的 antd `Splitter.Panel` 缺陷是同一个道理的另一面：**组合件的隐式契约（子元素 props）比配置的显式契约更容易出错**。

### 8.2 落地形态

| 导出 | 文件 | 职责 | 明确不做 |
| --- | --- | --- | --- |
| `ConsoleShell` | `ConsoleShell.tsx` | 四段式框架：侧栏（品牌/菜单/分组/底部提示）+ 顶栏（开关/徽标/环境/插槽/主题/用户/登出）+ 页签区 + 内容 + footer | 不取数、不认识路由表、不持有登录态 |
| `TabStrip` | `TabStrip.tsx` | 页签条：滑动胶囊指示器、溢出滚动箭头、右键菜单 | 不持有页签状态（`tabs` 从外部进） |
| `createTabStore` | `createTabStore.ts` | 页签 store 工厂（打开/关闭/关闭右侧/关全部 + 选中相邻签语义） | 不含 UI |
| `useShellTabs` | `useShellTabs.ts` | 把 store 绑成 `TabStrip` 要的回调组（含关闭后 `navigate` 到哪） | 不解析标题（`titleOf` 由应用注入） |
| `ShellNavGroup` / `ShellNavItem` / `ShellLabels` / `ShellTabs` | `types.ts` | 契约 | — |
| 7 个 chrome 图标 | `icons.tsx` | 侧栏开关/主题/登出/盾牌/chevron/关闭 | 不做业务图标集 |

**存储键沿用原样**：`storageKey="iam"` → `iam-sidebar-collapsed` / `iam-sidebar-groups`，与改造前逐字一致，用户升级后侧栏状态不丢。

**文案走注入字符串而不是 key**（详见 `types.ts` 头部注释）：数据组件的 `titleKey` 是为了「切语言表头重算」；外壳的 label 在 `Layout` 每次 render 时重建（`Layout` 本身订阅了 i18n），传字符串不会出现不重算的问题，反而免去让外壳认识五个 ap 的键空间。

### 8.3 体量对比

| 项 | 改造前 | 改造后 |
| --- | --- | --- |
| `Layout.tsx` ×3 | 622 | 443（各自只留「菜单映射 + 插槽 + 主题/登出绑定」） |
| `TabBar.tsx` ×3 | 848 | **0**（→ `TabStrip` 290） |
| `tabStore.ts` ×3 | 353 | 45（各 15 行，只定固定签） |
| `@cim/shell` | — | 992 |
| 外壳逻辑份数 | **3** | **1** |

净减 343 行不是重点——重点是「侧栏塌陷、页签联动、关闭后跳哪」这些**易错的交互语义**从三份变成一份。P3 期间就抓到一个只在共享包里才暴露的坑：`useShellTabs` 每次 render 都 `.map` 出新数组，若 `TabStrip` 的 effect 依赖这个数组，`setIndicator` 会触发 render → effect 再跑 → 无限循环；改成依赖**内容签名**（`path + label` 拼接）才正确。

---

## 9. 工程化：workspaces 源码直引

**决策**：根 `package.json` 建 npm workspaces，`@cim/*` 以 **TS 源码**被应用直接编译（不经 dist），改组件三个 app 同时热更。

```jsonc
// 根 package.json（新增）
{ "private": true, "workspaces": ["platform/web/packages/*", "business/*/web"] }
```

每个应用的 `vite.config.ts` 增加：

```ts
resolve: { alias: { '@cim/ui': path.resolve(__dirname, '../../../platform/web/packages/ui/src') } },
server:  { fs: { allow: ['.', '../../../platform/web/packages'] } },   // 允许读根外的源码
```

`tsconfig.json` 同步加 `paths`；`@cim/notify-client` 从 `file:` 依赖改为 workspace 依赖（同一次迁移完成，避免两种机制并存）。

**为什么不选另两条路**：私有 registry 对「单仓 5 个应用」不划算；保持 `file:` + 各自 `tsc` 会导致**改一个 token 要 rebuild 五次**，且版本容易漂——这正是现在 i18n 三份实现的成因。

**代价**：一次动 5 个 `vite.config.ts` + `tsconfig.json`；`ci` 需保证 `npm install` 在根执行。

---

## 10. 落地批次与验收标准

| 批次 | 内容 | 验收 |
| --- | --- | --- |
| **P0** ✅ | `@cim/theme`（tokens + 密度 + 明暗 + 状态映射）+ 抽取设计系统 CSS | **已完成**：三个 ap 删除本地副本后构建通过；样式等价性 + 运行时 CSSOM 双重验证，见 §10.1 |
| **P1** ✅ | `DataTable` + 页面骨架 + `FormGrid` + `CimModal` + `StatusTag` ✅（§10.2/§10.3）；`@cim/data`（`useResource` + `useDebounced`）✅（§10.6） | 组件层用 `DeviceAreaPage` 单页验证；`useResource` 用 3 个真实页面 + 20 项断言验证（§10.6）。**双实例传输层未合并**（决策见 §12-2） |
| **P2** | `@cim/i18n`（IAM 版上提）+ 字典 `useDict`/`EnumTag` | RMS 与 approval 切到后端驱动；切语言表头同步变；缺键上报可见 |
| **P3** | `@cim/ui` 其余组件 + `@cim/shell` | 外壳模型定稿后实施；三端菜单与权限表现一致 |

### 10.1 P0 实施记录（2026-11-11）

**做了什么**

| 改动 | 结果 |
| --- | --- |
| 根 `package.json` 声明 workspaces | 仅声明（含 `platform/web/packages/*` 与 5 个应用），**P0 未执行根 `npm install`**：应用经 vite alias 源码引用，暂不写 `dependencies`（写而不装会让 `npm ci` 失败），P1 一并切换 |
| `@cim/theme` | `tokens.css`（97 行令牌 + 暗色）+ `theme.ts`（读写/密度）+ `status.ts`（5 档语义 + 3 个已核实域的映射表） |
| `@cim/ui` | `base.css` / `shell.css` / `ui.css` / `index.css`，共 1245 行；`index.ts` 为空壳待 P1 |
| 三个 ap | `main.tsx` 增 `import '@cim/ui/index.css'`（早于本地 `styles.css`）；`styles.css` 删除公共前缀；`lib/theme.ts` 瘦成适配层（绑定本 ap 存储键）；vite/tsconfig 加 alias 与 `server.fs.allow` |

**净效果**：三份 `styles.css` 各删 1340 行（共 4020 行），换成 1 份 1245 行包 + 3 份各自保留段 → **净减 2680 行重复**。

**验证方式（不靠"看着差不多"）**

1. **抽取是纯搬运**：包文件对原 `styles.css` 第 7–103 / 104–164 / 165–768 / 769–1340 行做**字节包含**校验，四段连续无重叠（97+61+604+572 = 1334 行全覆盖），4/4 ✓。
2. **保留段零改动**：各 ap 新 `styles.css` 第 6 行起，与 `git show HEAD` 版本第 1341 行起 md5 一致，3/3 ✓。
   > 由此可得：原文 = 应用头注释 + 四个包文件 + 应用保留段，**逐字节可还原**，CSS 层面不可能产生视觉差异。
3. **运行时 CSSOM**：把三个 ap 的构建产物 CSS 注入空白页，用 CDP 读计算样式，12 项断言全过（浅色/暗色双主题、令牌解析、`.panel` 组件样式、`table.data` 字号、密度档 40px 触控区）——IAM 273 条规则 / RMS 367 / approval 509。

**计划与实际的三处偏差（原判断有误，已修正认知）**

| 原计划 | 实际 | 结论 |
| --- | --- | --- |
| 抽取「5 个 md5 相同文件」 | 只有 `theme.ts` 真正可抽 | `format.ts` 是 **IAM 域专用**（全部用 `iam.audit.type.*` 键，rms 只用到其中 `fmtDateTime`，其余是死代码）；`Perms.tsx` / `ProtectedRoute.tsx` / `usePermission.ts` 依赖各 ap 的 `authStore` 与 router，属 `@cim/shell`（P3）范畴。**逐字节相同 ≠ 可上提**，共享前提是"不依赖应用运行时" |
| `format` 放进 `@cim/ui` | 未做 | `ui` 不得依赖 `i18n`，而现有 format 直接调 `i18next.t`；要么把文案当参数传入，要么等 P2 统一 i18n 后再抽。留到 P1 |
| `Layout`/`TabBar` 属 shell | `shell.css` 暂放 `@cim/ui` | 三份 CSS 的前 1340 行是**整块**（含侧栏/顶栏/页签），强行按语义拆会切碎选择器组；P3 建 `@cim/shell` 时再按段迁移，届时仍是纯搬运 |

**顺带修掉的缺陷**：`rms-ap` 的 `lib/theme.ts` 是从 IAM 整体拷贝的，存储键沿用 `iam-theme`。同一域名不同路径部署时（`/iam`、`/rms`）localStorage 同源共享，两个应用的明暗偏好会互相覆盖。已改为 `rms-theme`（用户历史偏好会重置一次，影响可忽略）。

**遗留**：`--bg/--panel/--primary` 等现有令牌未加 `--cim-` 前缀（设计稿 §4.1 的理想形态）。改名需触碰三个应用约 4000 处引用，风险与收益不匹配，暂缓；新令牌（`--cim-status-*`、`--cim-hit`、`--cim-row-h`）已按规范命名。

---

### 10.2 P1 实施记录：组件层落地（2026-11-11）

**参考实现审计（`chengdu_jt` MES 前端，Vue 3 + Element Plus）**

| 项 | 事实 |
| --- | --- |
| 体量 | 75 个组件 / 16 725 行，分 11 个类目（basic·chart·content·custom·data·feedback·form·navigation·others·search·diyIcon），统一 `Goat*` 前缀 |
| 单体 | `GoatViewTable` 1927 行、`GoatTable` 1057 行、`GoatContentBottom` 862 行 |
| 接口 | 全部为 `props: ['options']` 单对象（`<goat-table :options="{...}" />`），无类型约束 |
| 数据 | `GoatTable` 自己分页（客户端）、自己导出 Excel、自己挂右键菜单、自己编单元格 |
| 文案 | **无 i18n**（`package.json` 无 i18n 依赖，标签硬编码中文/韩文） |
| 高度 | `utils/height.js` 用 50+ 个近乎相同的函数算高度（`getTableHeightByTwoLineSearchTwoThirdsInHalf` 之类），靠 `minus` 硬编码补偿 |

**借鉴 / 不借鉴**

| 参考做法 | 我们的取舍 | 理由 |
| --- | --- | --- |
| 按类目分目录 + 统一前缀 | ✅ 采纳（`content/data/form/feedback` + `Cim*`/领域名） | 一眼知道组件归哪类 |
| 表格工具栏（动作 + 搜索 + 计数 + 列设置 + 刷新） | ✅ 采纳 | 现仓 22 处 `.toolbar` 各写一遍 |
| 列显示/隐藏 + 顺序可调 | ✅ 采纳并**加持久化**（`settingsKey` → localStorage） | 原实现刷新即回默认 |
| 行语义色由后端字段驱动（`rowColor`/`styleCode`） | ⚠️ 改造采纳：只暴露 `rowClassName`，语义档来自 `@cim/theme/status` | 原做法让后端能直接指定颜色，等于把配色决策外泄 |
| 服务端视图模型下发列定义（`useViewColumn(viewId)` → `/ViewModelDefAp/...`） | ❌ 本期不做，列入 §12 待定 | 我们后端无对应表；先做成 `useColumns(settingsKey)`，将来接后端只需换数据源 |
| 单 `options` 对象传参 | ❌ 不采纳，改判别联合 + 泛型 | 无类型提示、无跳转定义；TS 下等于放弃重构安全性 |
| 组件内自带分页/导出/编辑 | ❌ 不采纳，只做渲染层 | 1057→1927 行的膨胀就是这么来的；页面需要时自行组合 |
| `utils/height.js` 高度计算 | ❌ 不采纳 | 50+ 个魔法 `minus` 无法维护；用 antd Table 的 `scroll` + flex 布局，由 CSS 决定高度 |
| 无 i18n | ❌ 明确反向：`titleKey` + `UiLocaleProvider` 注入 | 切语言时表头必须重算 |

**决策变更（重要，覆盖先前结论）**

先前 §11 写「antd 不进入业务侧」。实际需求是把组件做成**业务可直接用的一套**，而 React 生态里表格的虚拟滚动、日期区间、树选择、上传等自研成本远高于收益。故调整为：

> **antd 作为 `@cim/ui` 的内部交互层，业务代码不直接 import antd。**

- 主题不另立色板：`provider.tsx` 运行时读 `tokens.css` 的 CSS 变量喂给 antd `ConfigProvider`，明暗/密度切换经 `MutationObserver` 跟随（§2.2 仍然成立）。
- 代价：产物体积上涨（RMS 由约 0.6 MB 升至 **1.3 MB / gzip 388 KB**，IAM 与 approval 因只用到 Provider 为 520/530 KB / 156/168 KB）。后续用路由级 `lazy()` 拆分，不引入新依赖解决。
- 前置事实修正：动工前核查发现"我们系统用的 react+antd"并不成立 —— 4 个前端均**未安装** antd，源码 0 处引用，仅 `platform/web/package.json` 有一条声明。为避免"两套技术栈并存"，本次统一在三个应用安装 `antd@5.21.4`；`platform/web` 的既有声明（`^5.21.0`）与之兼容，保留不动（该包暂不参与构建）。

**工程接线（坑在依赖解析）**

| 问题 | 处理 |
| --- | --- |
| `@cim/ui` 源码在工程根之外，其 `import 'antd'` 从 `platform/web` 上溯找不到业务 `node_modules` | 各应用 vite `resolve.alias.antd` 指向本应用副本 |
| 同上，`import 'react'` 也落空 → 两份 React 风险 | 各应用 `resolve.dedupe: ['react','react-dom']` |
| tsc 报 `Cannot find module 'react'`（共享包内） | 各应用 tsconfig `paths` 映射 `react`/`react-dom` → `node_modules/@types/*` |
| antd 的样式改为 CSS-in-JS，无 CSS 引入 | 无需改 `main.tsx` 的样式顺序；`components.css` 只做少量对齐 |

**页面迁移样板**：`business/rms-ap/web/src/pages/DeviceAreaPage.tsx` 全量改写。
旧实现里的手写结构对照：`<table className="data">` + 手工 thead/tbody + `colSpan` 空态 → `DataTable`；`useState(loading)` 三态 → `loading/error`；`.modal-backdrop/.modal-head/.modal-body/.modal-foot` → `CimModal`；`.form-grid` + 4 个 `.field` → `FormGrid`；`.page-head` → `PageHead`。页面只剩取数与业务动作。

**验证（17 项运行时断言，全过）**

用临时探针页（已删除）渲染真实组件 + 假数据，CDP 读计算样式与 DOM：

| 断言 | 结果 |
| --- | --- |
| 表头背景取自 `--panel-2` | `rgb(246,248,251)` ✓ |
| 单元格字号 `--fs-sm` | `12.5px` ✓ |
| 暗色切换后表头 | `rgb(18,28,47)`（`#121c2f`）✓ |
| `ACTIVE`/`PENDING_SIGN` 语义色 | `rgb(13,138,85)` ok / `rgb(168,106,18)` warn ✓ |
| 工具栏计数 | 「共 3」✓ |
| 搜索受控过滤 | 3 行 → 2 行 ✓ |
| 列设置 | 弹层 4 行 → 取消一列后表头 3 列 ✓ |
| 列偏好持久化 | `cim.columns.probe` 写入正确 JSON ✓ |
| 列顺序调整 | 首列由「编码」变「名称」✓ |
| `CimModal` + `FormGrid` | 弹窗打开、3 字段、标签 uppercase、标题走注入翻译「新建」✓ |

三个应用 `tsc + vite build` 均通过（RMS 3198 模块 / 10.9s）。

**遗留**

1. 产物体积：RMS 1.3 MB，需路由级 `lazy()`（P2）。—— **已在 §10.4 解决**（拆为 19 个页面 chunk，首屏 index 103 KB）。
2. `FormGrid` 暂缺 `date` 字段（需 dayjs 值转换口径，避免与后端 ISO 字符串语义打架）—— **已在 §10.3 补齐**（对外只进出 ISO 字符串）。
3. 其余 22 个 RMS 页面、21 个 IAM 页面未迁移，仍用旧的手写结构（两套并存期需明确"新页面一律用组件"）。

### 10.3 P1 补齐批次：6 类目组件全量落地（2026-11-11）

§10.2 只交了首批 9 个文件（`DataTable` / `FormGrid` / `CimModal` / `Page` 骨架 / `StatusTag` / `useColumns` / 注入器）。参考实现有 75 个组件，光"表格 + 弹窗"远不够页面直接用，故本批按类目补齐。

**新增体量**

| 类目 | 新增文件 | 行数 |
| --- | --- | --- |
| basic | `Button` / `Tag` | 202 |
| content | `Divider` / `Drawer` / `ScrollArea`（`Page` 已于 P1 交） | 189 |
| data | `Descriptions` / `InfoTable` / `Steps` / `Tabs` / `Tree` / `Collapse` | 669 |
| form | `Field` / `Input` / `InputNumber` / `Select` / `DatePicker` / `Switch` / `Choice` / `Upload` | 928 |
| feedback | `Tooltip` / `Popconfirm` / `Popover`（`CimModal` 已于 P1 交） | 162 |
| layout | `Splitter` | 122 |
| 注入器 | `perm.tsx` / `dict.tsx` | 262 |
| 样式 | `components.css` 追加 | +182 |
| 入口 | `index.ts` 统一导出 | +61 |
| **合计** | 24 个新文件 + 3 个改动 | **约 +2 900 行**（含注释与类型） |

**本批修掉的设计缺陷**

| 缺陷 | 现象 | 处理 |
| --- | --- | --- |
| `CimSplitter` 用 children 传面板 | antd `Splitter.Panel` 实现是 `() => null`，`Splitter` 只读直接子元素的 props 来摆布局；中间隔一层自定义组件后左面板**完全不渲染** | 改为**配置驱动**：`panels: SplitterPanelConfig[]`，面板内容走 `render`。这是配置驱动优于组合件的一个硬证据（对应 §12-1 的倾向） |
| `FormGrid` 缺 `date` 字段 | P1 遗留（怕与后端 ISO 字符串语义打架） | 本批补：内部 `dayjs` ↔ ISO 字符串双向转换，对外**只进出字符串**，`CimDatePicker` 同理，业务侧永不接触 dayjs 对象 |
| `CimInputNumber` formatter/parser 类型不匹配 | antd `formatter` 返回 `string` 但 `value` 是 `number`，TS 报错 | 去掉自定义 formatter，改 `onChange: (v: number \| null)`，精度交给 `precision` |
| `CimTree` 拖拽落点语义推错 | `onDrop` 的 `dropPosition` 正负与 `dropToGap` 组合有四种含义 | 按 `info.dropToGap` + `dropPosition` 正负判定 before / after / inside 三态 |

**验证（45 项运行时断言，全过）**

同 §10.2 手法：临时探针页（已删除）渲染真实组件 + 假数据，无头 Chrome 经 CDP 读 DOM 与 `getComputedStyle`。断言按 6 个类目组织，每项含「渲染成功 / 关键计算样式等于 token / 交互结果符合预期」三类检查之一或多条。

下表列出其中可量化、有确定数值的代表项：

| 断言 | 结果 |
| --- | --- |
| 主色 `--primary` 透传到 antd | `#1f5fe0`（亮）/ `rgb(79,131,247)`（暗）✓ |
| 语义 ok 色 | `#0d8a55` → `rgb(13,138,85)` ✓ |
| 面板背景 `--bg` | 亮 `#eef1f6` / 暗 `#06090f` ✓ |
| 表头背景 `--panel-2` | 亮 `rgb(246,248,251)` / 暗 `rgb(18,28,47)` ✓ |
| `table.data` 单元格字号 `--fs-sm` | `12.5px` ✓ |
| 密度 `touch` 档 | 字号 `16px`、行高 `40px` ✓ |
| 计数文案 | 「共 3」✓（走 `UiLocaleProvider` 注入，非硬编码） |
| 搜索受控过滤 | 3 行 → 2 行 ✓ |
| 列设置 | 4 列 → 取消一列后表头 3 列，偏好写入 `cim.columns.*` ✓ |
| 列顺序调整 | 首列由「编码」变「名称」，刷新后保持 ✓ |
| `CimModal` + `FormGrid` | 弹窗打开、字段齐全、标签大写、标题走注入翻译 ✓ |
| 三应用 `tsc + vite build` | 全部通过（RMS 3198+ / IAM 3211 / approval 3206 模块） ✓ |

断言数演进：42/44（`Splitter` 左面板未渲染 + 拖拽无效）→ 修 `Splitter` 后 44/44 → 补 `Upload` 后 **45/45**。这个"先不过再修"的过程留在结论里，是因为 `Splitter` 那个坑（`Panel` 是空组件）在全网文档里几乎没有提示，属易复发的类型。

**产物体积（本批后）**

| 应用 | JS（minify） | gzip |
| --- | --- | --- |
| RMS（用全部组件） | 1.47 MB | 437 KB |
| IAM（只用 Provider） | 538 KB | 164 KB |
| approval（只用 Provider） | 543 KB | 176 KB |

RMS 因新增 `DatePicker` 把 dayjs 全量 locale 拉进来涨了约 170 KB。仍走 §10.2 的结论：用路由级 `lazy()` 拆，不为此删组件。

**工程接线补充**

approval 侧 `tsconfig.json` 漏了 `dayjs` 的 `paths`（`vite.config.ts` 已加 alias），表现为 `tsc` 报 `Cannot find module 'dayjs'` 而 `vite build` 与 dev 均正常 —— 说明 **tsc 的 `paths` 与 vite `alias` 是两套配置，必须同时改**。

### 10.4 P2 实施记录：消息层 · 虚拟滚动 · 路由级拆包（2026-11-11）

三件事的共同点：都不是"加组件"，而是**收掉一种各页面重复的写法**。判断标准仍是"能不能让页面少写代码、且写错的空间变小"。

**1. 消息层 `UiToastProvider` / `useToast` / `useConfirm`（`feedback/message.tsx`）**

改造前四种写法并存：RMS/approval 页面用 `useState('')` 在页顶挂提示条（不自动消失、成功失败同色、切页即丢）；确认用 `window.confirm()`（阻塞主线程、不可主题化）；少数地方手写 `.modal` 三件套。

| 取舍 | 决定 | 理由 |
| --- | --- | --- |
| antd 静态 `message.success()` | ❌ 不用 | 拿不到 `ConfigProvider` 主题，暗色下弹出来仍是浅色，z-index 也不与其它浮层共享 |
| `App.useApp()` 的上下文版 | ✅ 采用 | 与主色/密度/语言同源；`UiToastProvider` 放在 `CimProvider` **内部**、业务 `<App/>` **外部** |
| 容器 DOM | `component={false}` | antd 的 `<App>` 默认渲染 `<div class="ant-app">`，套在最外层会多一层块级容器，破坏 `.app` 的 100vh 布局；关掉后只留上下文，DOM 零变化 |
| 确认返回值 | `Promise<boolean>` | 调用方 `const ok = await confirm({...}); if (!ok) return;` —— 比回调更难过写成"点了取消还继续执行" |

内置按钮文案走 `useUiLabel`；业务消息（`已删除`）由调用方 `t()` 后传入 —— 那是数据，不是组件文案。

**2. `DataTable` 虚拟滚动（`virtual?: number`）**

传视口高度即启用 antd 的 `virtual`。适用「一次给全量」的长列表（日志/审计/参数明细），不适用服务端分页（一页 20 行没有收益还丢分页语义）。

antd 虚拟表格的两个硬约束已内建处理：`scroll.x` 必须是数字（`'max-content'` 不可用）→ 未给时按列宽求和兜底；每列必须有 `width` → 缺失时按 160 兜底并 `console.warn` 点名缺哪几列（不静默降级）。

**3. 路由级拆包（三应用）**

| 应用 | 改法 |
| --- | --- |
| RMS | 19 个业务页 `lazy()` + `lazyRoute(Page, permCode)` 辅助函数（把 `Suspense`+`RequirePerm`+页面合成一个 element）；登录/回调/403/404 保持同步加载 |
| IAM | 路由由后端菜单下发，故改在 `PAGE_REGISTRY`：值由组件改为 `lazy(() => import(...))`，`RoutePage` 内套 `Suspense` |
| approval | 9 个页面 `lazy()`；404 同步 |

`Suspense` 一律放在 Layout 的 `<Outlet/>` **内部**（路由 element 上），不是 `Routes` 外层 —— 放外层的话切页时侧边栏和顶栏会一起被兜底替换，闪一下再回来。

**⚠️ 拆包后的第二个坑：共享依赖要有归属。** 只加 `lazy()` 时，rollup 会把"第一个用到 antd Table 的页面"当成它的家 —— 实测 `DeviceAreaPage` 的 chunk 达 **613 KB**（表、弹窗、表单全在里面），首屏反而没小多少。补 `build.rollupOptions.output.manualChunks` 按 `vendor-react` / `vendor-antd` / `cim-ui` / `vendor` 分组后才正常。

| 应用 | 改前（单 chunk） | 改后首屏 index | 改后 vendor 三块 | 页面 chunk |
| --- | --- | --- | --- | --- |
| RMS | 1472 KB / gzip 437 | **103 KB** / 34 | 1158 KB / 370 | 19 个，最大 35 KB |
| IAM | 538 KB / gzip 164 | **33 KB** / 11 | 559 KB / 185 | 15 个，最大 15 KB |
| approval | 543 KB / gzip 176 | **62 KB** / 23 | 559 KB / 185 | 9 个，最大 32 KB |

**注意别把这张表读成"首屏变小了"**：antd 在根上被 `CimProvider` 用到，注定属于首屏，所以三块 vendor 加起来才是首屏真实成本。IAM 甚至从 538 涨到 595（+11%）—— 拆开后跨 chunk 无法共享 gzip 压缩冗余，同样的代码分文件后压缩率略降。**收益是另外两条**：① 首屏只解析必要的业务代码（RMS 的解析量从 1.47 MB 降到约 1.28 MB）；② 缓存粒度——业务发版只改页面 chunk，vendor 三块命中浏览器缓存，用户重下的量从"整个应用"变成"几十 KB"。

**验证（11 项运行时断言，全过）**

临时探针页（已删除）自驱动断言，结果写回 DOM，无头 Chrome `--dump-dom` 读取：

| 断言 | 结果 |
| --- | --- |
| 虚拟滚动 DOM 行数 | **11 行** / 数据 5000 行 ✓ |
| 虚拟视口高度 | `320px`（传入值）✓ |
| `toast.success` 出现且文案正确 | ✓ |
| confirm 弹出、确定按钮为危险态 | `ant-btn-dangerous` ✓ |
| confirm 点确定 → `resolve(true)` | ✓ |
| confirm 点取消 → `resolve(false)` | ✓ |
| `CimSpin` 环形取 `--primary` | `rgb(31,95,224)` = `#1f5fe0` ✓ |
| 真动态 `import()` + `Suspense` 落地 | 内容出现 `LAZY_OK` ✓ |
| 拆包后表头仍取 `--panel-2` | `rgb(246,248,251)` ✓ |
| 三应用 `tsc + vite build` | 全过 ✓ |

断言演进留痕：第一次 5/7（虚拟行选择器用了 `tr`，而 antd 虚拟表格的行是 `div`；`confirm.cancel` 的 promise 永远不 resolve）→ 定位到"上一个弹窗在离场动画期间仍在 DOM 里，点到了旧按钮"→ 改成按弹窗正文文案认领 → **11/11**。这类"点到了不可见的旧节点"在 E2E 里很常见，selector 必须带语义锚点而不是只靠类名。

---

### 10.5 P3 实施记录：`@cim/shell` 外壳层落地（2026-11-11）

**新增**：`platform/web/packages/shell/` 7 文件 992 行（`ConsoleShell` / `TabStrip` / `createTabStore` / `useShellTabs` / `types` / `icons` / `index`）。

**三应用改造**（每个都是 5 处改动，形状完全一致）：

| 改动 | 说明 |
| --- | --- |
| `components/Layout.tsx` | 改为薄适配层：菜单源 → `ShellNavGroup[]`（含各自的权限过滤/树展开）、`labels` 映射、`topbarExtra` 插槽、主题与登出的绑定 |
| `components/TabBar.tsx` | **删除**（296/295/257 行），实现进 `TabStrip` |
| `store/tabStore.ts` | 15 行：`createTabStore(HOME_PATH)`，对外导出不变 |
| `vite.config.ts` | 加 `@cim/shell` alias，另加 `react-router-dom` / `zustand` alias（同 antd 的跨目录解析问题） |
| `tsconfig.json` | 加 `@cim/shell`、`react-router-dom`、`zustand` 的 `paths` |

**踩到的 4 个坑**：

1. **`useShellTabs` 的返回对象每次 render 都是新的** → `tabs` 数组引用永远变 → `TabStrip` 的 effect 每次都跑 → `setIndicator` 触发 render → **无限循环**。修法：effect 依赖改「内容签名」`path + '\u0001' + label`，既区分「真的变了」也覆盖切语言（label 变 → 宽度变 → 需重测）。
2. **图标组件类型互不可赋值**：IAM `ComponentType<SVGProps>`（width 是 `string | number`）、RMS `(p:{width?:number}) => JSX.Element`、approval 两者交叉。唯一共同上界是 `any`，收窄只会逼调用方写 `as unknown as`；外壳对图标的用法只有「传两个数字」，故 `ShellIconCmp = ComponentType<any>` 并在此记录理由。
3. **副标题必须按 `undefined` 判断而非真假值**：IAM 的 `.side-desc` 在缺翻译时渲染空串占位，用 `m.desc ?` 会少一个节点。
4. **`@cim/shell` 依赖 `react-router-dom` / `zustand`**：和 antd 一样，共享包在 `platform/web` 下向上解析不到业务 `node_modules`，必须 alias + tsconfig paths 双配置（§10.3 已记过一次，这是第二次复发）。

**验证：75 项断言全过**（自驱动探针 + 无头 Chrome `--dump-dom` 读回 DOM）：

| 组 | 项数 | 覆盖 |
| --- | --- | --- |
| 外壳组件（`@cim/shell` 本身） | **31/31** | 菜单模型→DOM（19/6 条目计数、可折叠分组头 vs 静态头）、激活态、副标题渲染与缺失、顶栏插槽顺序 `role-chip>env-chip>extra>theme>user>logout`、环境灯 ok/warn、用户首字母、footer 环境、分组折叠+持久化+自动展开、侧栏塌陷+持久化、抽屉开合（`mobile-open` + `.scrim`）、主题按钮标题切换、页签计数/固定签无关闭钮/激活态/指示器（`w=100px`）、页签点击与关闭回调、右键菜单 4 项 + 固定签首项禁用 + 动作回调 |
| 真实 RMS Layout | **16/16** | 19 个导航项、3 个静态分组头、激活 `/devices`、无副标题、品牌/环境/用户块（`tester` / `tenant:t1`）、无超管徽标、6 个顶栏槽位、页签 2 个（固定签 + 当前页）、footer、侧栏提示取注入值、Outlet 渲染 |
| 真实 IAM Layout | **16/16** | 后端菜单树→6 项/2 个可折叠头、不可见菜单被跳过、副标题 6 个、`/users` 激活、超管徽标、6 槽位、页签 2 个、分组折叠后剩 3 项 + `iam-sidebar-groups` 落盘、塌陷 + `iam-sidebar-collapsed` 落盘 |
| 真实 approval Layout | **12/12** | 8 项/4 个静态头、`/todo` 激活、无外壳用户块（身份切换器复用 `.user-chip` 样式但是 `<button>`）、6 槽位、2 个页签、footer、壳高 801px（`ToastHost` 在壳外未顶坏布局） |

**为什么用「直接渲染 Layout + 假 store」而不是开真应用**：真应用要过 `ProtectedRoute` → `BootGate` → 后端 `/me`+`/permissions`，本机没有后端；而且这台机器的无头 Chrome 对 SPA 回退路径（`/`、`/index.html`）的**导航**稳定 `ERR_CONNECTION_REFUSED`（同为静态的 `/probe.html` 正常），绕不开那次导航。直接渲染 `Layout` 反而是更聚焦的验证——它的职责就是把 GROUPS/菜单树/权限映射成外壳。

**两端 assert 写错的两次留痕**：① 把 IAM 顶栏槽位当 5 个（漏了超管徽标）→ 实际 6；② approval 断言「没有 `.user-chip`」失败，因为 dev 态身份切换器复用了这个类名 → 改断 `div.user-chip`。两次都是**探针的预期写错，产品是对的**——所以断言失败时必须先看实际值再改断言，不能反过来改代码。

**产物**：RMS `index` 95.77 KB（gzip 32.10，改前 103）、IAM `index` 24.03 KB（改前 33）、approval `index` 55.13 KB；三应用 `tsc + vite build` 全过。

---



### 10.6 P4 实施记录：`@cim/data` 数据层 + 三个页面迁移（2026-11-11）

**为什么先做这个**：改造前 20 多个页面各写一遍同一段取数配方——

```ts
const [list, setList] = useState([]); const [loading, setLoading] = useState(true);
async function refresh() { setLoading(true); try { setList(await api.x()) } finally { setLoading(false) } }
useEffect(() => { const id = setTimeout(refresh, 200); return () => clearTimeout(id) }, [keyword]);
```

缺的不是行数，而是四个会真出事的语义：**竞态**（快筛时旧回包覆盖新数据，表格显示上一次筛选结果，数据看着都合法、极难发现）、**取消**（每切一次筛选留一个在途请求）、**轮询暂停**（产线大屏常年后台空转）、**错误态**（`try/finally` 里没有 `catch`，失败后 `loading` 关了但列表是空的，界面表现为「无数据」而不是「加载失败」）。

**落地形态**（`platform/web/packages/data`，4 文件 ≈ 300 行，只依赖 react）：

| 导出 | 职责 |
| --- | --- |
| `useResource(fetcher, opts)` | 竞态只认最新、deps 变化 abort 旧请求、同时最多一个在途、`hidden` 时暂停轮询且恢复即补、`keepPreviousData`、`enabled` 条件取数；**`error` 交页面决定界面，不在 hook 里弹 Toast** |
| `useDebounced(value, ms)` | 只返回滞后值。取数与渲染用**同一个**滞后值——旧实现「后端按滞后值过滤、前端按即时值再滤一遍」会闪一行空态 |
| `ApiError` / `toApiError` / `isAbortError` | 错误契约。**刻意不定义成闭合的类**：各 ap 的错误体不同（RMS/IAM 读 `{code,msg,errors}`，approval 还要兼容裸数据与 Spring 默认体），定成类就等于把业务 API 层统一了 |
| — | **不做**：请求缓存（SWR 式，hook 不认识 URL）、重试（与幂等性相关，POST 不能盲重试）、分页（`DataTable` 的受控属性，把 page/size 放进 `deps` 即可） |

**页面迁移**（RMS 首批 3 页，均为「列表 + 弹窗 CRUD」同形）：

| 页面 | 改前 | 改后 | 消失的写法 |
| --- | --- | --- | --- |
| `DeviceTypePage` | 222 行 | 264 行 | 手写 `table.data` + `modal-backdrop` 三件套 + `confirm()` + 手写防抖 |
| `BypassPage` | 178 行 | 311 行 | 同上 + `className="tag ok"` 手写状态色 |
| `GoldenPage` | 174 行 | 301 行 | 同上 + `<td>` 里的条件分支 |

行数没降反升，**这是预期内的**：多出来的是字段级错误提示、删除失败只弹提示不毁列表、语义色档、模态框的焦点/Esc/滚动锁、列显示与顺序持久化——旧实现这些**根本不存在**。真正减少的是「同类逻辑的份数」：这三页里 `loading / error / 防抖 / 确认框 / 表格空态` 从各写一遍变成各调一次。

**顺带修掉的三个缺陷**（都不是新功能）：

1. **组件内置文案的中文兜底是死的** —— 探针在真实页面里打出 `t('ui.confirm.cancel') === 'Cancel'`：RMS 的 i18n 对缺键做 humanize（取末段、首字母大写），于是 `useUiLabel` 里「返回值是否等于 key」这个判据永远不成立，`label(key, '取消')` 永远拿不到中文。表现是确认框「确定 = 删除 / 取消 = Cancel」中英混排——**读代码看不出来，只有跑起来才看得见**。
   修法：`UiLocaleProvider` 增加 `hasKey`（应用注入 `i18n.exists`），`useUiLabel` 优先用它判断是否命中；未注入时退回旧判据（向后兼容）。三个应用的 `main.tsx` 各加一行。
2. **循环 chunk 警告** —— `vendor → vendor-react → vendor`。react-router 6.4+ 把核心挪到 `@remix-run/router`，它被分到 `vendor`，而 `vendor-react` 又依赖它。把 `/@remix-run/` 并入 react 分组后警告消失（三应用同步改）。
3. **`FormGrid` 的日期按「看起来对」的格式发出去会被后端静默丢弃** —— `BypassController` 用 `Instant.parse` / `LocalDateTime.parse` 解析，两者都只认 ISO 的 `T` 分隔；而 `FormGrid` 默认口径是 `YYYY-MM-DD HH:mm:ss`（空格）。解析失败被 `catch` 成 `null`，即「生效时间填了但没生效」。为此 `FormGrid` 的 `date`/`datetime` 增加 `valueFormat`（发给后端的格式）与 `displayFormat`（给人看的格式）两个独立参数；`text` 增加字面 `placeholder`（`CHAMBER.TEMP` 这类不翻译的格式示例）。**教训：日期格式必须显式对齐后端解析器，不能靠默认值猜。**

**验证 20/20**（探针页 + 本机 8084 接口桩 + 无头 Chrome `--dump-dom`）：

| 组 | 项数 | 覆盖 |
| --- | --- | --- |
| 确认框基线 | 1 | 直接 `useConfirm()` 弹窗点确定 → `resolve(true)`（先证明机制本身可用，后面的失败才不是环境问题） |
| 机型页（真实页面） | 12 | 3 行渲染、表头取 `--panel-2`（`rgb(246,248,251)`）、搜索「D2」后 1 行**且请求真的带 `keyword=D2`**（`useDebounced`→`deps`→服务端整条链通）、弹窗 5 个字段、空提交 2 个字段错误、新建 POST body 正确 + toast、确认框危险态、取消不删（0 次 DELETE）、确定删除（`DELETE /api/v1/rms/device-types/dt2`）、取消按钮中文兜底 |
| Bypass 页 | 2 | 2 行渲染、状态标签取 `--cim-status-ok`（`rgb(13,138,85)`） |
| Golden 页 | 5 | 首次请求 500 → 错误块出现 → 点「重试」→ 2 行出现；待审行 3 个动作 / 已审行 1 个；批准 POST 落地 |

**本次「失败」全是探针写错，产品是对的**（与 P3 同样的教训）：
- `dt.deleteConfirmed` 连续失败 3 轮。加轨迹后才看清：**在无头 Chrome 的 `--virtual-time-budget` 下，antd 弹窗的离场动画不推进**，关闭后的弹窗一直留在 DOM 里且 `offsetParent !== null`，「可见性过滤」失效——第二次点到的仍是上一个已 resolve 的弹窗按钮，点了等于没点（P2 踩过同源问题）。改为**先测确定分支、再测取消分支**（同一时刻只有一个弹窗），并保留 XHR 记录来区分「没点中」与「请求失败」。这条坑对后续所有弹窗 E2E 都适用。
- `gp.errorShown` 的文案从 `Retry` 变成 `重 试`，正是缺陷 1 修好后的副产物。

**产物**：RMS `index` 96.18 KB（gzip 32.21）、IAM 24.07 KB、approval 55.18 KB；新增 `@cim/data` 后三端体积无可见变化（该包只依赖 react，被并入 `cim-ui` 块）。三应用 `tsc + vite build` 全过。

**⚠️ 一条要说清的边界**：`useResource` 的 abort 目前只做到「丢弃迟到回包」，**没有真正断掉连接**——各 ap 的 API 层（`rmsApi` 等 900 行调用）还没把 `ctx.signal` 转给 axios。要真取消，需在 API 层给取数函数加可选 `signal` 参数并逐层透传，属 API 层收敛时的事（§12-2）。

---

## 11. 非目标

- 不做微前端（qiankun / Module Federation）——五个应用独立部署，无运行时集成需求
- 不做 SSR
- **不把 antd 暴露给业务代码**：antd 只在 `@cim/ui` 内部使用（页面只 import `@cim/ui`）。
  这不是"不封装第三方库"，而是"只在一处封装"——换实现只改一个包，页面零改动。
- 不接管业务状态管理：各 ap 的 zustand store 各自持有，`@cim/*` 只提供会话（token/权限/语言/密度）
- 不统一业务 API 层：`rmsApi.ts`、`approvalApi.ts` 这类域 API 留在各 ap

## 12. 待定项

| # | 事项 | 现状 | 定稿时点 |
| --- | --- | --- | --- |
| 1 | 外壳模型（配置驱动 vs 组合件） | **已定稿（§8.1）**：配置驱动（`ShellNavGroup[]`）+ 两个插槽（`roleBadge` / `topbarExtra`）；`@cim/shell` 已落地，§10.5 | 已定稿 |
| 2 | HTTP 传输层是否合并到 `@cim/data`（双实例 + 拦截器 + `Result` 解包） | **暂不合并**。事实：RMS 与 IAM 的 `lib/api.ts` 除 `downloadBlob` 外逐行相同；但 approval 的契约不同（成功返回**裸数据**而非 `Result`，操作人放 body 不走 Bearer）。合并要么逼 approval 改契约，要么让公共层长出开关，两头都不划算。附带代价：`useResource` 的 abort 只能丢弃回包、不能断连接 | API 层收敛时重估 |
| 2 | 字典条目 i18n 化：`itemValue` 单列多语言，还是复用 `sys_i18n` 键 | `sys_dict_item` 当前只有 `itemValue` | P2 前 |
| 3 | 是否需要「高对比」第三套主题（车间大屏） | 已列需求，未定是否独立档 | P1 后按现场反馈 |
| 4 | 列定义是否由后端下发（参考实现的 `viewId` → 视图模型） | 现为前端声明 + 本地偏好；后端无视图模型表 | 视"不同角色看不同列"是否为真需求 |
| 5 | 路由级拆包以抵消 antd 体积 | **已完成（§10.4）**：三应用均按路由 `lazy()` + `manualChunks` 分组；RMS 首屏 index 103 KB | 已定稿 |
| 6 | `DataTable` 虚拟滚动的行高策略（固定行高 vs 动态测量） | **已定稿**：沿用 antd 默认行高（按 `size` 档），不自己测量 | 已定稿 |
| 7 | `--bg/--panel/--primary` 等基础变量无 `--cim-` 前缀 | 改名要动约 4000 处引用（各 ap styles.css 全在引用） | 暂缓，等外壳层统一时一并改 |
