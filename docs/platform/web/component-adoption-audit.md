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
| 4 | `LanguageSwitcher` × 3 | IAM 61 / RMS 51 / approval 31 行，**语义不同**：IAM 走 `/api/i18n/locales` 远端目录 + `LocaleInfo`，另两个用内置两语种 | 三份行为不一致 | 上提 `@cim/shell`，语言目录由应用注入（props 传 `locales` / `onChange`） | ⏳ 仍待办 |
| 5 | `NotificationBell` × 2（RMS 180 / approval 123） | 重复的"轮询未读 + 下拉列表"逻辑，取数接口不同 | 依赖各 ap 通知 API | 上提为受控组件：`fetch` 与 `onOpenItem` 由 props 注入 | ⏳ 仍待办 |
| 6 | 状态页（403 / 404 / ComingSoon / 登录中继） | 6 个文件 ~350 行，同一套 `.panel` + `<h1>` + 裸按钮手写三遍 | 无 | 上提 `StatusPage`（`code` / `titleKey` / `descKey` / `action`），三应用替换 | ✅ 未新增 `StatusPage`，而是把 `EmptyState` 扩到 `icon`/`children`/`large` 后统一为 `Panel + EmptyState`（少一个组件、少一层概念） |
| 7 | `.split` 双栏 | 6 处 5 文件；`ui.css` 已有 `.split` 样式但**无组件** | 无 | 加 `SplitPane`（`left` / `right` / `ratio`），顺带把 `CimSplitter` 的用途讲清 | ⏳ 仍待办 |
| 8 | `.field` 表单块 | 101 处（IAM 65 / approval 36）；`FormGrid` 已存在但只 4/4 文件使用 | 与 `FormGrid` 职责重叠 | **不建议**新增组件，按页把 `.field` 手写块并进 `FormGrid` | ✅ 结论修正：容器口径实为 49 处，`CimField` 已存在但**没有 CSS**（空壳）→ 补样式后全部替换，`FormGrid` 只在"整表配置驱动"时用 |
| 9 | `PermTree`（IAM 335 行） | 单应用 | 领域组件 | 不上提 | — |
| 10 | `th`/`td` 手写表 | 仅 RMS `Dashboard` / `DeviceArea` 各 1 处 | 无 | 换 `DataTable`（`scrollX` 已支持） | ⏳ 仍待办 |

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

