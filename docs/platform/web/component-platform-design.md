# 前端组件中台设计（多 ap 共享组件 · 主题 · 数据 · 翻译）

> 状态：**P0 已落地（2026-11-11）**，P1–P3 未开始。实现细节与实测数据见 [§10](#10-落地批次与验收标准)。
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
@cim/ui      ← 只依赖 theme（严禁依赖 data / i18n）
@cim/data    ← 依赖 axios + zustand（读 token）
@cim/i18n    ← 依赖 i18next + @cim/data（拉译文/字典）+ theme（EnumTag 用色）
@cim/shell   ← 依赖以上全部
```

> **落地进度**：P0 只建了 `@cim/theme` 与 `@cim/ui` 两个包（其余待 P1/P2/P3）。其中 `shell.css` 暂放在 `@cim/ui` 内（原因见 §10.1 偏差表），P3 建 `@cim/shell` 时按段迁移。

**依赖规则（CI 可校验）**：

| 规则 | 理由 |
| --- | --- |
| `ui` 不得 import `data` / `i18n` | 组件保持纯展示，否则又回到「组件内置请求」 |
| `theme` 不得 import 任何包 | tokens 是最底层，反向依赖会造成循环 |
| `data` / `i18n` 不得 import `ui` | 数据/翻译层不应感知组件 |
| 业务 ap 只 import `shell`（其已 re-export 其余四包） | 单点入口，减少各 ap 的 import 噪音 |

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

### 5.2 `useResource` 语义

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

| 批次 | 组件 | 说明 |
| --- | --- | --- |
| P0 | `Panel`、`EnumTag`、`Kpi`、`Empty`、`Tag`、`Icons` | 纯展示，5 个同构文件直接上提 |
| P1 | `DataTable`（虚拟滚动 + 服务端分页 + 列固定 + 空/错/载三态） | 承载千行表，最关键的单个组件 |
| P1 | `Form`（zod schema + 后端 `errors` 回填到字段） | 与 README §8 一致 |
| P2 | `Modal`、`Drawer`、`Toast`、`Confirm` | 行为统一，焦点管理到位 |
| P3 | `Layout`、`TabBar`、`MenuTree`、`LanguageSwitcher`、`NotificationBell` | 见 §8，最后做 |

`DataTable` 的 props 契约（不含任何请求能力）：

```ts
interface DataTableProps<T> {
  columns: Array<{
    key: string;
    titleKey: string;                       // 必填，禁止直接传 ReactNode 文案
    width?: number | string;
    align?: 'left' | 'right' | 'center';
    render?: (row: T) => ReactNode;         // 例外：自定义单元格，内部自行决定
  }>;
  rows: T[];
  rowKey: keyof T | ((row: T) => string);
  loading?: boolean;
  error?: Error;
  emptyKey?: string;                        // 空态文案键，默认 common.empty
  onRefresh?: () => void;
  virtual?: boolean | { rowHeight: number };
  rowClassName?: (row: T) => string;        // 供状态行着色，仍走 tokens
}
```

---

## 8. @cim/shell：外壳是最后一个

`Layout` 三份已分叉（288/178/156），且**菜单结构本身不同**（IAM 四段 / RMS 三组 / approval 四组）。所以先做组件、后做外壳。

外壳模型必须在动手前二选一：

| 模型 | 做法 | 适合 |
| --- | --- | --- |
| **配置驱动** | 菜单来自 `sys_menu`（平台已有），外壳只渲染 + 过滤权限 | 五个系统菜单同源、菜单由运营维护 |
| **组合件** | 外壳只提供 `<Shell.Header>` / `<Shell.Side>` / `<Shell.Content>` 插槽，各 ap 自己拼 | 各 ap 菜单结构差异大、需自定义区域 |

倾向**配置驱动 + 插槽兜底**：菜单来自 `sys_menu`，但允许某一段用插槽覆盖（如 RMS 的机台树侧栏）。

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
| **P1** | `@cim/data`（双实例 + `useResource`）+ `DataTable` | 先只替换 1 个页面（建议 RMS 配方列表）；快速切页无串数据、轮询在 `hidden` 时暂停 |
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

## 11. 非目标

- 不做微前端（qiankun / Module Federation）——五个应用独立部署，无运行时集成需求
- 不做 SSR
- 不封装第三方 UI 库（antd 不进入业务侧；`platform/web` 里的 antd 依赖在 P0 一并移除）
- 不接管业务状态管理：各 ap 的 zustand store 各自持有，`@cim/*` 只提供会话（token/权限/语言/密度）
- 不统一业务 API 层：`rmsApi.ts`、`approvalApi.ts` 这类域 API 留在各 ap

## 12. 待定项

| # | 事项 | 现状 | 定稿时点 |
| --- | --- | --- | --- |
| 1 | 外壳模型（配置驱动 vs 组合件） | 倾向配置驱动 + 插槽兜底 | P3 动手前 |
| 2 | 字典条目 i18n 化：`itemValue` 单列多语言，还是复用 `sys_i18n` 键 | `sys_dict_item` 当前只有 `itemValue` | P2 前 |
| 3 | 是否需要「高对比」第三套主题（车间大屏） | 已列需求，未定是否独立档 | P1 后按现场反馈 |
| 4 | `DataTable` 虚拟滚动的行高策略（固定行高 vs 动态测量） | 固定行高可满足产线表格 | P1 实现时 |
