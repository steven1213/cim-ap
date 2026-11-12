# 前端组件采用度审计与上提清单

> 审计时间：2026-11-12。范围：`business/{iam,rms,approval}-ap/web` 三个有界面的应用（MDS/MES 仍是 18 行脚手架，不计）。
> 目的：回答两个问题——① 页面**真的用**了 `platform/web/packages` 里的组件吗？② 现有界面里还有哪些东西**该上提**却没上提？
> 统计口径见 [§6](#6-统计口径可复现)，全部数字由脚本实测得出，不是估算。

> **状态：已收口（同日）**。用户指令「没有用已有组件的都应该用」下达后，按本文 §3 的 **B 方案**全部执行完毕：171 个裸 `<button>`、22 个 `.alert`、`.field` 容器、`.kpi` 容器、6 个手写状态页全部清零，`CimButton` 改为渲染原生 `<button className="btn …">`（视觉零变化）。执行记录见 `AUDIT.md §31`，**本文保留为审计证据与决策依据**；§3 的「A / B 两方案待拍板」已按 B 定案，§4 清单里除 `LanguageSwitcher` / `NotificationBell` 外均已落地。

⚠️ **§1 表内 `.kpi` / `.field` 两列的原始数字有口径错误**，见 §1 下方的更正说明。

---

## 1. 采用度现状

| 应用 | 页面 | 用 `@cim/*` 的页面 | 裸 `<button>` | `.kpi` 手写 | `.alert` 手写 | `.field` 手写 | `.split` | `styles.css` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| iam-ap | 21 | 16 | 74 | 0（已迁） | 11 | 65 | 5 | 593 行 |
| rms-ap | 23 | 19 | 92 | 131 | 9 | 0 | 1 | 1175 行 |
| approval-ap | 10 | 9 | 5 | 40 | 1 | 36 | 0 | 1994 行 |
| **合计** | **54** | **44** | **171** | **171**⚠️ | **21** | **101**⚠️ | **6** | **3762 行** |

⚠️ **口径更正**：`.kpi` 与 `.field` 两列是按 `className="kpi` / `"field"` **前缀**匹配出来的，把 `kpi-main` / `kpi-label` / `kpi-val` / `field-label` / `field-hint` 这些**子元素**也算进去了，属于口径错误。按「容器元素」精确复核后：`.kpi` 容器 **40 处**（原记 171）、`.field` 容器 **49 处**（原记 101）。裸 `<button>` 的 74/92/5 = 171 是对的（按元素计）。§6 的脚本已按此更新（加 `\s|>` 边界约束）。

**结论一：数据层与容器层已收敛，控件层没有。**

- 已普遍采用：`Page` / `Panel` / `DataTable` / `useResource` / `FormGrid` / `CimModal` / `useToast` / `useConfirm`（三应用都在两位数文件量级）。
- 几乎没用：`CimButton`（仅 approval 11 处）、`StatusTag`/`ToneTag`（0）、`CimTooltip`/`CimPopconfirm`（0）、`CimSplitter`（0）、`useColumns`（0）。

**结论二：没用组件的页面全是"状态页"。**

| 应用 | 未用 `@cim/*` 的页面 |
| --- | --- |
| iam-ap | `ComingSoon` / `ForbiddenPage` / `NotFoundPage` / `SsoCallback`（+ `pageRegistry` 非页面） |
| rms-ap | `ForbiddenPage` / `LoginPage` / `NotFoundPage` / `SsoCallback` |
| approval-ap | `NotFoundPage` |

这些页面各自手写 `.panel` + `<h1>` + 裸按钮，占 6 个文件 ~350 行，是 §4 里"状态页"候选的直接证据。

---

## 2. 本次已修（6 项，均已验证）

### 2.1 通用图标 34 个上提 `@cim/ui`

- **证据**：IAM 的 34 个图标是 RMS 37 个的**子集**，且 `<svg>` 子节点**逐字节相同**（md5 对比），两份文件共 723 行重复。
- **做法**：`platform/web/packages/ui/src/icons.tsx` 成为唯一实现；两应用的 `components/Icons.tsx` 变薄壳（IAM 44 行、RMS 86 行），页面 `from '@/components/Icons'` **零改动**。
- **判据**（写进文件注释）：跨 ≥2 应用的通用语义图标 → 上提；单应用领域图标（`IconFlask` 配方、`IconWeCom` 企微）→ 留在应用。
- approval 的 30 个图标是另一套画法（`IconCheck` vs `IconCheckCircle`），**未合并**——同名不同图，强合并等于改版。

### 2.2 权限门 `Perms` 并入 `Can`（并补上一个静默失效）

- **证据**：IAM 与 RMS 的 `components/Perms.tsx` **逐字节相同**（30 行 × 2），共 30 处调用（IAM 12 文件 / RMS 18 文件）。
- **发现（重要）**：`@cim/ui` 早就提供了等价能力 `<Can>` / `<CimButton perm>`，但它们读的是 `UiPermissionProvider` 注入的上下文，而**三个应用一个都没挂这个 Provider**。未挂时走「一律放行」兜底——界面完全不同，权限门却失效。这类静默失效比报错更难发现。
- **做法**：① 在 `iam`/`rms` 的 `main.tsx` 挂 `UiPermissionProvider`（`has` 来自各应用 `usePermissions`，`ready` 来自 `permissionsLoaded`）；② 30 处 `<Perms code|codes>` 全量改为 `<Can perm>`，删除两份本地副本。
- **验证**：IAM 渲染契约 33/33（该测试同步补挂 Provider——否则 `<Can>` 走放行兜底，"无权限不挂载"的断言会全部反转成通过）。

### 2.3 删死代码

`components/Panel.tsx` × 3（31/34/34 行）、`rms/components/Drawer.tsx`——均 **0 引用**：页面早已改用 `@cim/ui` 的 `Panel` / `CimDrawer`，本地副本留在仓库里误导人。

### 2.4 `.alert` 样式上提 `ui.css`

三份 `styles.css` 的 `.alert` 段（7 条规则）**逐字节相同**。规则搬进 `ui.css`，三份应用样式各删 31–36 行（净减 93 行）。因为 `main.tsx` 里 `@cim/ui/index.css` 先于 `styles.css` 加载、且规则值原本一致，**视觉零变化**；`approval` 独有的 `.login-card .alert` 保留在本地。

### 2.5 新组件 `KpiCard` / `KpiGrid`

- **证据**：`.kpi` 结构手写 178 处（RMS 131 / approval 40 / IAM 7），且 IAM 的 `DashboardPage` 里**自带一个同款局部组件 `Kpi`**（11 处调用）——样式 `.kpi-*` 早在 `ui.css` 里，缺的只是组装成组件这一步。
- **做法**：`data/KpiCard.tsx`；IAM Dashboard 改用 `KpiCard`/`KpiGrid`，删除局部 `Kpi`。组件不依赖 react-router（要跳转由调用方包 `<Link>`）。

### 2.6 新组件 `InlineAlert`

`.alert` 结构手写 22 处（其中「错误提示 + 下方重试按钮」组合最多）。组件只负责提示块本身（色调 + 图标 + 文案），**刻意不含重试按钮**——按钮放块内还是块下、用 `.btn-sm` 还是 antd Button，各页现状不同，猜错就是改版。IAM Dashboard 已采用。

**验证**：三应用 `tsc --noEmit` 零错 + `vite build` 通过；IAM 渲染契约 33/33。

---

## 3. 最大缺口：171 个裸 `<button>` 与两套按钮视觉

这是"组件已存在、页面却不用"的核心案例，值得单独说：

| 事实 | 数据 |
| --- | --- |
| `@cim/ui` 提供 `CimButton`（权限门 + tooltip + `labelKey`） | 已导出 |
| 页面里手写 `<button className="btn-sm">` 等 | **171 处** |
| `CimButton` 实际采用 | 11 文件（全在 approval） |
| `.btn` / `.btn-sm` / `.btn-ghost` / `.btn-danger` 样式位置 | **各应用 `styles.css` 各留一份**（未上提） |

根因不是"懒得换"，而是**两套视觉**：`CimButton` 基于 antd `Button`，而 IAM/RMS 的页面用的是手写 `.btn`（自有圆角、边框、hover 动效）。把 171 处换掉 = 全站按钮改版，且 `.btn` 基样式还在应用侧、没有可比对的"平台版"。

**两条路，需业务/设计拍板（本次未动）**：

- **A. 视觉向 antd 收**：把 `.btn` 系列删掉，全部换 `CimButton`。收益是彻底统一（含权限门/tooltip/无障碍），代价是按钮外观变化，需过一遍视觉确认。
- **B. 视觉保持手写**：把 `.btn` 段上提 `ui.css`（三份已高度一致），并把 `CimButton` 的渲染目标从 antd `Button` 换成 `.btn` 结构，保留 `perm`/`tooltip`/`labelKey` 三个增量。收益是视觉零变化 + 171 处可机械替换，代价是 `CimButton` 要走一次内部实现替换。

倾向 **B**（先统一语义、再统一外观），但这是产品决策，不宜由重构单方面决定。

**→ 已定案并按 B 执行完毕（2026-11-12）**。定案依据是用户指令「没有用已有组件的都应该用」——这条要求同时蕴含"必须用组件"和"视觉不能变"，只有 B 能同时成立。执行要点（细节见 `AUDIT.md §31`）：

- `.btn` 体系上提 `ui.css`，但**必须保留联合选择器 `button, .btn`**：原设计下所有原生 `<button>` 默认就是主色按钮外观，`btn-ghost`/`btn-sm` 只是修饰类（页面里大量 `<button className="btn-ghost btn-sm">` 根本没有 `.btn` 基类）。拆开会静默改变 166 个按钮的外观。
- `CimButton` 加 `bare` 形态：`icon-btn`/`chip-x`/`tree-toggle`/`user-chip`/`bell-item`/`node-del` 这些**不是按钮的控件**不能套 `.btn` 基类。
- 余 9 处 `<Link className="btn …">` 与 1 处 `<Link className="kpi">` 保留手写：`CimButton` 渲染 `<button>`，`<a>` 内嵌 `<button>` 是非法 HTML；这正是联合选择器要留 `button, .btn` 两半的原因。

---

## 4. 待上提清单（按 ROI 排序）

> 状态列：✅ 已落地 / ⏳ 仍待办。除 `LanguageSwitcher` 与 `NotificationBell` 外均已于同日落地（`AUDIT.md §31`）。

| # | 候选 | 现状与证据 | 阻塞点 | 建议动作 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | `.btn` 系列按钮 | 见 §3，171 处 | 视觉路线未定 | 先定 A/B，再机械替换 | ✅ 按 B 落地，裸 `<button>` 清零 |
| 2 | `KpiCard` 推广（RMS 131 / approval 40） | 组件已就绪；RMS 用 `<Link className="kpi">`、approval 用 `<Link>` 包 `<span>` | 带跳转的卡需要"外层是链接"的形态 | 给 `KpiCard` 加 `as`/`href` 或提供 `KpiCardLink`（不引 router，收 `render` 回调） | ✅ 改为 `KpiCard bare` 只吐内层 body，链接仍由 `<Link>` 承担（比引 router 更轻） |
| 3 | `InlineAlert` 推广（余 21 处） | 组件已就绪 | 无 | 逐页替换，顺带把「错误 + 重试」组合统一到 `useResource` 的 `refresh` | ✅ `.alert` 清零 |
| 3.5 | `StatusTag` 落地（0 采用） | 组件已就绪，但 `@cim/theme` 的 `STATUS_TONES` 只登记了 3 个域，页面各自写 `statusTone()` / `STATUS_TONE` 映射 | 登记的域与后端枚举不一致 | 按后端枚举把域补全（`signoffState`/`approvalState`/`bypassState`/`overrideState`/`tempReleaseState`），页面删掉本地映射改 `StatusTag` | ✅ 已落地，业务侧引用 0 → **17**；顺带修出两处语义错（见下） |
| 4 | `LanguageSwitcher` × 3 | IAM 61 / RMS 51 / approval 31 行，**语义不同**：IAM 走 `/api/i18n/locales` 远端目录 + `LocaleInfo`，另两个用内置两语种 | 三份行为不一致 | 上提 `@cim/shell`，语言目录由应用注入（props 传 `locales` / `onChange`） | ✅ 上提为 `LocaleSwitcher` + `useLocaleSwitcher`，三应用各剩 10 行薄壳 |
| 5 | `NotificationBell` × 2（RMS 180 / approval 123） | 重复的"轮询未读 + 下拉列表"逻辑，取数接口不同 | 依赖各 ap 通知 API | 上提为受控组件：`fetch` 与 `onOpenItem` 由 props 注入 | ✅ 展示层上提 `@cim/shell`，数据/SSE/导航留在应用（approval 无通知表，语义本就不同） |
| 6 | 状态页（403 / 404 / ComingSoon / 登录中继） | 6 个文件 ~350 行，同一套 `.panel` + `<h1>` + 裸按钮手写三遍 | 无 | 上提 `StatusPage`（`code` / `titleKey` / `descKey` / `action`），三应用替换 | ✅ 未新增 `StatusPage`，而是把 `EmptyState` 扩到 `icon`/`children`/`large` 后统一为 `Panel + EmptyState`（少一个组件、少一层概念） |
| 7 | `.split` 双栏 | 6 处 5 文件；`ui.css` 已有 `.split` 样式但**无组件** | 无 | 加 `SplitPane`（`left` / `right` / `ratio`），顺带把 `CimSplitter` 的用途讲清 | ✅ 新增 `CimSplitPane`，6 处替换；与 `CimSplitter` 的分工写进两个 header |
| 8 | `.field` 表单块 | 101 处（IAM 65 / approval 36）；`FormGrid` 已存在但只 4/4 文件使用 | 与 `FormGrid` 职责重叠 | **不建议**新增组件，按页把 `.field` 手写块并进 `FormGrid` | ✅ 结论修正：容器口径实为 49 处，`CimField` 已存在但**没有 CSS**（空壳）→ 补样式后全部替换，`FormGrid` 只在"整表配置驱动"时用 |
| 9 | `PermTree`（IAM 335 行） | 单应用 | 领域组件 | 不上提 | — |
| 10 | `th`/`td` 手写表 | `Dashboard` / `DeviceArea` 两处已换 `DataTable`；现仅剩 `RecipeDetailDrawer` 一处 k/v 表（与 §6.5 同一处） | 无 | 剩下的那处是「字段名/字段值」两列，`DataTable` 接不住 | ✅ 已落地（本条曾标 ⏳，实际早已迁完——核账时按 `grep -rln "<th\\b\\|<td\\b"` 数，别按记忆） |

---

## 5. 明确不上提的（负向清单）

避免后来者重复评估——这些**看起来像通用组件、实际绑业务语义**：

| 组件 | 所在 | 为什么不提 |
| --- | --- | --- |
| `RecipeDetailDrawer`（1075 行）/ `SpecEditModal` / `BatchOpsModal` / `DiffTable` / `ChamberPanel` | rms-ap | 配方版本链、腔体实例化、逐字 diff 都是 RMS 领域模型 |
| `DetailModal`（605 行）/ `FlowProgress` / `DisplayFields` / `CompleteModal` / `ResubmitModal` | approval-ap | 绑定审批流程图与表单 schema |
| `IdentitySwitcher` | approval-ap | 审批暂无 RBAC，用"切身份"联调，与 IAM/RMS 的权限模型不同 |
| `PermTree` | iam-ap | 菜单树 + 平台码双闸门，IAM 域专用 |
| `NotificationBell` 的通知 DTO | rms/approval | 见 §4-5，**组件可上提、DTO 不提** |
| `lib/format.ts`（72 行，iam 与 rms 曾**逐字节相同**） | iam / rms | **看似重复，实则只有一方在用**：rms 那份里 5 个函数（`auditLabel`/`orgTypeLabel`/`originLabel`/`auditTone`/`fmtRelative`）零调用点，且引用的 `iam.*` 键在 rms 内置包里一个都没有（缺键兜底 humanize → `iam.common.dash` 渲染成 **"Dash"** 而非 "—"）。已删死函数、兜底改用 `rms.common.dash`。**判据：逐字节相同不等于都活着——先数调用点，再数 key 是否真的能命中。** |

### 5.1 0 采用但**保留**的（边界已写清，别再来一遍）

这几个在审计表里是「0 采用」，但结论不是「删」也不是「必须用」——它们各自有明确的适用面，
只是当前页面恰好没走到那条路上。判断依据是**看它被谁消费**，而不是数业务侧的引用：

| 组件 | 业务侧引用 | 实际情况与边界 |
| --- | --- | --- |
| `useColumns` | 0 | **不是没用**：`DataTable` 内部就是靠它做列显隐/排序持久化（`DataTable.tsx` 第 18 行 import、第 167 行调用）。它是「表格的实现细节」，不该被页面直接调——页面用 `DataTable` 就等于用了它。审计时按「业务侧引用数」统计会把它误报成死代码。 |
| `CimTooltip` | 0 | 站内 124 处提示走原生 `title=`，都是**一行短文案**（图标按钮说明、`.dim` 小字注解）。原生 `title` 在这些场景更好：无延迟、不抢焦点、不产生额外 DOM。`CimTooltip` 的适用面是**多行/富内容/需要 `placement` 控制**（如长哈希、多字段说明）。边界写在这里，避免下个人把 124 处全换掉再回滚。 |
| `CimSplitter` | 0 | 可**拖拽**分栏（antd `Splitter`，尺寸可上报）。管理页的主从形态用 `CimSplitPane`——定宽左列、窄屏自动堆叠。只有「两栏都是主工作区」时才用 `CimSplitter`。两个组件的 header 都写了这段。 |
| `CimPopconfirm` | 0 | 页面统一用 `useConfirm()`（`CimModal` 承载），因为破坏性操作大多要**填理由**（驳回意见、结束原因），气泡确认框放不下 textarea。`CimPopconfirm` 保留给「一键即生效、无需说明」的场景。 |
| `CimPopover` | 0 | 与 `CimTooltip` 同源但可交互（内含按钮/链接）。当前没有被点开的浮层需求。 |

> 教训：**「0 采用」要先分清是被页面用还是被组件用。** `useColumns` 这类内部基元按业务侧计数必然为 0，
> 把它当死代码删掉会直接打断 `DataTable`。

---

## 6. 统计口径（可复现）

```bash
# 1) 裸控件：页面目录下数原生标签
grep -rc '<button' business/*/web/src/pages/*.tsx
# 2) 手写结构：数「容器元素」，必须带边界约束。
#    ⚠️ 别写 'className="kpi' —— 前缀匹配会把 kpi-main/kpi-label/kpi-val/field-label/field-hint
#    这些子元素一起算上（首版就是这样把 40 数成 171、49 数成 101 的）。
grep -rEo 'className="(kpi|alert|field|split)("|\s)' business/*/web/src --include=*.tsx
# 3) 组件采用度：每个页面是否 import 了 @cim/*
grep -rl "@cim/ui\|@cim/data" business/*/web/src/pages/*.tsx
# 4) 跨应用重复组件：逐字节比对（先按文件名配对）
diff business/iam-ap/web/src/components/Icons.tsx business/rms-ap/web/src/components/Icons.tsx
# 5) 样式重复：按选择器聚合三份 styles.css，比较规则体 md5
```

**坑一**：页面是 `pageRegistry` 里 `lazy(() => import(...))` 的，按"谁 import 了它"扫会全部误判成死代码；判断死代码要先排除 `pages/`，再对 `components/` 单独 grep。

**坑二（数字口径）**：`className="kpi` 这类**前缀**匹配会把子元素算进容器数。凡是要报"容器有几处"，正则必须带 `("|\s)` 边界。这条错误已经污染过一版审计表（`.kpi` 40→被数成 171、`.field` 49→被数成 101），更正记录见 §1。


---

## 7. 复扫与再收敛（2026-11-12 续 2）

上一轮按「具名 `statusTone` 函数」和「字符串字面量类名」清完，本轮换判据重扫，补掉两类统计盲区。

### 7.1 复扫结果

| 项 | 数量 | 处理 |
| --- | --- | --- |
| `className="kpi-grid"` | 12 | ✅ 本轮收敛（见 7.2） |
| 内联 `tone={x === 'A' ? 'ok' : …}` | 61 | 部分入字典（见 7.4） |
| 手写 `<table>` | 1 | 保留：`RecipeDetailDrawer` 的两行 k/v 表 |
| 裸 `<input>` | 6 | 保留：`checkbox`/`radio`/`file`，`CimInput` 刻意不收 |
| 未用 `useResource` 的页面 | 13 / 54 | 保留：9 个状态页 + `DesignerPage`/`ProfilePage`/`LoginPage` |

### 7.2 组件能力不足 = 页面绕开的真正原因

`KpiGrid` 早就有，但签名是 `({ children })`——**没有 `className`/`style`**。而 12 处手写里有 3 处要压外边距：

```tsx
// DeviceRecipePage:358 嵌在 Panel 内，要吃掉网格默认外边距
<div className="kpi-grid" style={{ margin: '10px 14px 0' }}>
```

页面不是不知道有组件，是**组件接不住这个用法**。补 `className`（合并而非覆盖）与 `style` 后 12 处全部替换，采用 2 → **14**。

> 判据补充：替换受阻时先问「组件能不能表达这个用法」，再问「页面为什么不用组件」。
> 同 `CimField` 当年的「组件在、`.cim-field` 样式没上来」，都是能力缺口而非纪律问题。

### 7.3 跨应用重复（本轮上提）

| 对象 | 证据 | 处理 |
| --- | --- | --- |
| `components/ProtectedRoute.tsx` | iam/rms **md5 相同**（`f5911ec3`） | → `@cim/shell`，收 `authenticated` + `loginPath`；应用给 token |
| `lib/permissions.ts` | 39 行 ×2，除末尾应用专属常量外**逐字节相同** | 三纯函数 → `@cim/ui`；应用侧留 re-export + 本地常量 |
| `components/RequirePerm.tsx` | 同概念两种写法（跳 `/403` vs 渲染 403 页） | → `@cim/shell` `RequirePermission`，呈现走 `fallback` 注入 |

⚠️ `RequirePermission` **保留**两种 403 呈现：跳转会改 URL，就地渲染不改（权限补上后刷新即达原页）。
这是「刷新/后退/分享」的产品决策，不进组件层。若要统一，倾向**就地渲染**。

### 7.4 内联状态映射：入字典的判据

61 处 `tone=` 三元按字段频次：`status`×4、`result`×3、`versionStatus`×2、`qualificationStatus`×2，其余各 1。

- **入字典**：同一枚举**跨页出现 ≥2 次**（`qualificationStatus`、`versionStatus`、`auditResult`）。
- **不入**：只出现 1 次的页面局部判定（`reconcileResult === 'DRIFT'`、`severity === 'CRITICAL'`、`cmpResult`）——进 `STATUS_TONES` 只会造出**无人复用的键**，反而让字典失真。

### 7.5 `approval` 的图标：同名但不同画法 ✅ 已合并（2026-11-12）

`approval-ap/web/src/components/Icons.tsx` 曾是第三份未并的副本（235 行 / 31 个本地图标），其中
**13 个与 `@cim/ui` 同名**（`IconBell`/`IconChevron`/`IconInbox`/`IconLogout`/`IconMoon`/`IconPlus`/
`IconRefresh`/`IconSearch`/`IconShield`/`IconSidebar`/`IconSun`/`IconTrash`/`IconUser`，外加别名 `IconX`），
逐个比对 `d` 属性**无一相同**。

当时按「逐字节相同才上提」判为**不合并**（合并会改观感），并把它标成「需一次统一图标的独立决策」。
2026-11-12 做掉了：同名就是同一语义，两套画法属缺陷而非差异 → 改薄壳 re-export，本地只留 17 个
审批领域图标，`Svg` 基准对齐平台（20px / stroke 1.8，原 18 / 1.7）。

⚠️ **同名 ≠ 同语义，合并时逐个看方向/细节**：平台 `IconChevron` 是**右**箭头（树/面包屑），
approval 原版是**下**箭头（下拉展开），直接换会让身份切换器箭头指错 → 那一处加 `rotate(90deg)`。
`IconShield` 平台版无勾、approval 版带勾，语义仍在「权限/安全」内，接受。
判据补充：**判「能不能合并」看语义，判「要不要上提」才看逐字节相同**——前者是产品一致性，后者是代码复用。

---

## 8. 第三次复扫：组件能力缺口与样式重复（2026-11-12 续 3）

前两轮清的是「页面没引组件」和「类名手写」，本轮换了第三种判据：
**把「页面为什么绕开组件」当成组件缺陷来查**。结论：剩下的手写基本不是纪律问题，
是组件签名接不住那个用法。

### 8.1 三处能力缺口（本轮已补）

| 现象 | 处数 | 组件缺口 | 修法 |
| --- | --- | --- | --- |
| `<label className="check"><CimSwitch/>{文案}</label>` | 4 | `CimSwitch` 只有 `onText`/`offText`（**随状态变化**的文案），没有**静态说明**文案 → 页面把开关塞进复选框的排版里 | 增 `label`/`labelKey`，渲染 `.cim-switch-row` |
| `<label className="check"><input type="checkbox"/>` | 2 | `CimChoice` 只收 `options: CimOption[]`，单布尔要写成 `options=[{value:'x'}]` 再取 `value[0]`，比手写还绕 | 新增 `CimCheckbox`（+ `.cim-check` 供网格单元格单独用） |
| `<div className="empty" style={{padding:'28px 0'}}>` | 4 | `EmptyState` 没有 `className`/`style`，嵌在面板里要压内边距就只能手写 | 增 `className`（合并）/`style` 透传 |

> 与 §7.2 的 `KpiGrid` 是同一条规律：**替换受阻时先问「组件能不能表达这个用法」**。
> 这三处如果只做「页面改用组件」，改不动——必须先进组件补签名。

⚠️ `CimSwitch` 的静态文案用 `<span>` 不用 `<label>`：antd `Switch` 渲染的是 `<button>`，
`<label>` 包裹**不会**产生「点文字切换」的效果，写成 label 只是看起来像而实际不生效。

### 8.2 跨 3 ap 逐字节相同 → 上提（本轮已做）

统计口径：逐条规则文本比对（去注释、去空白），三份完全相同才动。

| 对象 | 处数 | 去向 |
| --- | --- | --- |
| `.hint` | 15 | `ui.css` |
| `.cell-actions` | 26 | `ui.css` |
| `@media (max-width: 900px)` 外壳块（`.sidebar` / `.shell.collapsed *` / `.main`） | 3 份 | `shell.css` |
| `@media (max-width: 640px)` 外壳块（`.topbar` / `.content` / `.env-chip` / `.user-meta` / `.crumb`） | 3 份 | `shell.css` |
| `.grid-2` / `.grid-3` 收窄断点（1180 / 900） | 3 份 | `ui.css` |
| `.scrim` | 3 份 | `shell.css` |

⚠️ **`.scrim` 是反向缺口**：`ConsoleShell` 一直在渲染 `<div className="scrim">`（移动端抽屉遮罩），
但 `shell.css` 从来没有这条规则——样式只存在于三个 ap 里。任何一个新建 ap 漏抄，表现就是
「抽屉打开后点空白处没反应」。**组件渲染的类名，样式必须在组件包内**。

### 8.3 登录页：全仓只有一页，但它刻意不复用组件库

结论先说：**登录页已收敛到唯一一处**——`business/iam-ap/server/src/main/resources/static/login/`
（`index.html` + `login.css` + `login.js`，IAM 后端 `GET /login` 直出，随 jar 发布）。
此前它是 `SsoLoginPageController` 里一段 255 行的 Java 文本块，现拆成静态资源：
可 lint、可格式化、改样式不用重启 JVM。

| 位置 | 角色 | 是否复用 `@cim/ui` |
| --- | --- | --- |
| IAM 后端 `static/login/` | **唯一登录页**，全仓共用 | ❌ 刻意纯 CSS/JS，零框架依赖 |
| `@cim/shell` 的 `<SsoRelay>` | 各 ap 的「正在跳转 / 准入失败」中继页 | ✅ 在 React 应用内，正常用组件 |
| `@cim/ui` 的 `.sso-relay-dev*` | `SsoRelay` 卡片里的 DEV 令牌区 | ✅ 两 ap 逐字节相同，已从 rms 上提 |
| iam / approval / rms 的 `.login-*` | 无（已删） | — |

**接入面**：iam、rms、approval 三处都只做「中继 + 回跳」，不再各自持有登录版式；
mds / mes 仍是骨架，接入时照 approval 这一份抄即可（后端 `/me` + 前端 `authStore` 两步）。
**为什么不复用组件库**：登录页要在「React/antd 全都没加载」的状态下可用——这正是它存在的场景。
把登录表单挂进控制台 SPA，就要过 `ProtectedRoute`/`BootGate`，未登录时被弹回登录页形成往返；
控制台打包失败时登录入口跟着一起死。这是身份服务最不该有的单点。

**代价（已知并接受）**：`login.css` 是一份**令牌子集副本**（17 个变量，机械复制自
`platform/web/packages/theme/src/tokens.css`，变量名逐字一致），改配色要改两处；
`tokens.css` 顶部有指向 `login.css` 的反向注释，减少漏改。版式也只能与 `ui.css` 人工对齐。

**approval-ap 的接入（本轮）**：它的身份链本来就是「两条通道」——`CurrentUserResolver`
在 `cim.auth.enabled=true` 时只认 JWT、`false` 时用查询参数。前端此前只实现了后者，
于是顶栏常驻「身份切换」、登出只是清 localStorage。现在补 `authStore` + `/login` `/sso`
两条路由 + `ProtectedRoute`/`BootGate`，**通道由后端 `/me` 的 `authEnabled` 决定**：
同一份前端产物既能跑带 IAM 的部署，也能跑无 IAM 的演示（顶栏自动换回身份切换器）。

> 为什么不像 rms 那样用构建期常量定死：那样两种后端配置要各打一次包。
> 开关放在后端配置里、由接口回传，前端只按事实分支。

**同时修掉的两个坑**：
1. rms 旧中继页把品牌名放在 `.brand-title` 里，而该类的文字色是为深色侧栏定的 `#fff`——
   在白色卡片上**完全看不见**。`<SsoRelay>` 改用自有的 `.sso-relay-name`，配色显式声明。
2. 登录页的 `.caps-hint` 是从未匹配到元素的死规则，且引用了未定义变量 `--warn`；已删。

### 8.4 明确不提取（新增负向清单）

| 对象 | 处数 | 不提取的理由 |
| --- | --- | --- |
| 手写 `<div className="form-grid">` 包 `CimField` 子树 | 1 | `FormGrid` 是**受控声明式表单**（`fields[]` + `value/onChange` 自动渲染控件），接不住自定义 `CimField` 子节点；只有 1 处，不值得再开一个「纯布局网格」组件 |
| 手写 `<dl className="meta">` / `<dl className="kv">` | 9 | 用的是平台 `.meta`/`.kv` 样式，`CimDescriptions` 是「label/value 数组」语义；这些块的 value 里嵌 `CimTag`/条件渲染，逐块判断成本高于收益 |
| 时间线 | 2 套 → **已统一** | 原判「类名与结构都不同，是否统一属视觉决策」。2026-11-12 决定统一：`.timeline*` 规则两 ap **逐字节相同**（脚本比对），上提 `@cim/ui/ui.css`；approval 的 `.detail-timeline` 改为同一结构，动作标签就近放进标题行。见 §9.2 |
| `table.data` 遗留表格样式 | iam / approval → **已删** | 两 ap 已无手写 `<table>`；approval 侧 7 条规则直接删除（`grep <table` 零命中）。rms 侧仍在用（`RecipeDetailDrawer` 的 k/v 表），未动。见 §9.1 |

### 8.5 复扫方法（可复现）

三个脚本，都在仓库外跑，判据写在注释里：

1. **跨 ap 类名比对**：解析各 `styles.css` 顶层规则 → 类名 → 规则文本，取交集且文本全等。
2. **平台归属判定**：类名是否出现在 `platform/web/packages/*/src/*.css`；是否由组件源码 `className="..."` 产生。
3. **死类名判定**：app 内 `.tsx/.ts`（**剔除注释**）+ 平台源码里是否有整词出现。

⚠️ 第 3 步必须剔注释：本仓大量迁移说明写着「手写 `.modal-backdrop` → `CimModal`」，
不剔注释会把 `.modal*`/`.drawer*`/`table.data` 全判成活代码（本轮先踩了一次）。

---

## 9. 第四次复扫：清死代码 + 三处横向统一（2026-11-12 续 4）

本轮不再是「页面绕开组件」，而是**同一件事的第二份实现**：样式抄了一份、图标存了第三份、
403 页各写一份、时间线各有一套。

### 9.1 `approval/styles.css`：从 RMS 整份拷来的 32 个死类

线索是一段注释——approval 的样式文件里写着「RMS 业务流补充」。扫完 146 个类名：**32 个零引用**，
13 个「平台组件在用、approval 又抄一份」。删 42 + 7 条规则，文件 1243 → 953 行。

判定口径（三步，缺一会误删）：

1. **ap 内引用**：`tsx/ts/index.html` 里整词出现（前后不能粘 `-`/字母）；
2. **平台组件引用**：`platform/web/packages` 的 `tsx/css` 里出现 → 是平台组件产生的类名，**不能删**；
3. **动态拼接复核**：``className={`fp-step fp-${st}`}` `` 让 `fp-active/fp-done/…` 在字面量里永不出现。
   本项目仅 4 处动态拼接，逐个人工看一遍即可。

⚠️ **按类名判会误杀/误留**：`data` 这个类名会被 `detailRes.data` 命中 → `table.data` 被判活。
表格家族必须按**完整选择器**判。姊妹教训：逐字节相同 ≠ 都活着；同一个词在不同命名空间命中 ≠ 命中。

### 9.2 三处横向统一

| 对象 | 统一到 | 保留的差异（有意） |
| --- | --- | --- |
| 权限图标 | 13 个同名图标 → `@cim/ui`，本地只留 17 个领域图标（见 §7.5 更新） | `IconChevron` 方向（平台右箭头 = 树语义；下拉处 `rotate(90deg)`） |
| 403 页 | 新增 `@cim/ui` 的 `ForbiddenState`（Panel + EmptyState large + 图标 + 出口插槽） | 呈现位置：IAM 跳 `/403`、RMS 就地渲染 |
| 时间线 | `.timeline*` 上提 `@cim/ui/ui.css`，两 ap 共用 | approval 多一行哈希（`.detail-hash` 留本地） |

### 9.3 顺带修掉的：`CimDispatchController`

默认关 + 零测试 + 用 Spring 6 不认的注解写法（注释还断言「仍被 MVC 识别」）。
改三件套并补 4 例测试；反向验证：换回旧注解，`endpointIsMapped` 立刻 404 而 Bean 断言仍绿
——「Bean 在、路由不在」。详见 `docs/platform/server/api-convention.md §6.5` 与 AUDIT §41.5。

### 9.4 结论：`approval` 前端没有权限门，是**不做**不是**漏做**

页面级守卫、`<Can>`、`CimButton perm` 在 approval 里全为零。原因是授权链只到角色级
（`approval-ap:ADMIN`），没有细粒度权限码：加守卫只能「全放行」或「全拦」，前者是假门、
后者直接不可用。**等权限码体系落地再补**——现在补一个看起来有门的空壳，比没有更糟。
