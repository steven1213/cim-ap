# IAM-AP 前端设计文档

> 本文件记录 **已落地** 的 IAM 控制台前端实现（统一登录门户 + 管理后台）。
> IAM 是基础设施型 ap，承载统一身份与跨业务**准入**控制（只管准入，不碰业务内部权限）。
> 后端契约见 `docs/business/iam-ap/server/README.md`。

## 0. 定位

- **归属**：`docs/business/iam-ap/web/`，基于平台前端框架（`docs/platform/web`）构建，不另起炉灶。
- **职责**：统一登录门户 + IAM 管理控制台（概览、**组织架构**、用户与档案、登录锁定、应用注册、准入授权、**组织授权**、角色组、在线会话、审计日志、系统设置、**系统配置（菜单/权限/角色/多语言）**、账户自助）。
- **不负责**：业务系统的菜单 / 按钮管理 UI —— 由各业务 ap 自己的前端承载。
- **无三方登录**：认证源为 IAM 后端（LDAP / AD 或本地凭证），前端只对接 `iam-ap` 后端。

> **⚠️ 与 1.x 版的根本变化（控制面配置化，2026-10-09）**：菜单、按钮权限、界面文案三件事
> **都已改成「配置驱动」**，前端不再持有硬编码清单：
>
> | 维度 | 1.x（硬编码） | 2.0（配置驱动） |
> | --- | --- | --- |
> | 菜单/路由 | `lib/menu.tsx` 注册表 + `App.tsx` 静态路由表 | `GET /sys/me/menus` 下发的**菜单树**（`sys_menu` 表）；前端按 `path`/`component` **动态生成路由** |
> | 按钮显隐 | `isIamAdmin(user)`（判 `apps`+`roles`） | `GET /sys/me/permissions` 下发的**权限码集** + `<Perms code="iam:user:create">` |
> | 界面文案 | JSX 里写死中文 | i18next 运行时拉取 `GET /api/i18n/messages`（**中文强制兜底**） |
> | 图标 | JSX 里写 `<IconTree/>` | 后端存 `icon: 'tree'` 标识 → 前端 `iconRegistry` 解析 |
>
> 收益：**改菜单、改按钮权限、改文案都不需要发版**——管理端（`/menus`、`/permissions`、`/roles-admin`、
> `/i18n`，见 §3）改完即时生效。设计依据见
> [`../server/console-menu-perm-i18n.md`](../server/console-menu-perm-i18n.md)。


## 1. 技术栈与目录

- **栈**：React 18 + TypeScript 5.6 + Vite 5（`@vitejs/plugin-react`）。
- **状态/请求**：`zustand`（persist 到 localStorage，刷新不丢登录态）+ `axios`（请求/响应拦截器）。
- **路由**：`react-router-dom` v6（`BrowserRouter` + `ProtectedRoute` 守卫 + **菜单派生的动态路由**）。
- **多语言**：`i18next` + `react-i18next`（运行时从后端拉译文包，见 §4）。
- **别名**：`tsconfig.json` 配 `@/*` → `src/*`；`vite.config.ts` 同配，`dev` 端口 `5171`。
- **开发代理**：`vite.config.ts` 把 **两个**前缀代理到 `http://localhost:8081`（iam-ap server 端口）：
  - `/api` → IAM 自己的 `/api/v1/**`，以及 i18n starter 的公开端点 `/api/i18n/**`；
  - `/sys` → 平台 `cim-system` 的端点（**无** `/api/v1` 前缀，如 `/sys/me/menus`）。
  - ⚠️ iam-ap server 必须监听 **8081**（`application.yml` 的 `server.port: 8081`；缺省会退化为 Spring Boot 默认 8080 → 代理连不上，浏览器见 500）。模板见 `server/src/main/resources/application.yml.example`。
- **API 基址（两类路径，见 `src/lib/api.ts` 文件头）**：
  - `api`（`baseURL='/api/v1'`）：IAM 自己的端点，页面内只写资源段（如 `/login`、`/admin/users`、`/me`）；
  - `rootApi`（`baseURL=''`）：平台端点，按**绝对路径**调用（`/sys/me/menus`、`/sys/me/permissions`、`/api/i18n/messages`）。
  - 为什么必须两个实例：axios 的 `buildFullPath` 只在 url 是**绝对 URL**（带 scheme）时才跳过 baseURL 拼接，写 `/sys/...` 仍会被拼成 `/api/v1/sys/...`（实测踩坑）。
- ⚠️ **生产部署同样要反代 `/api` 与 `/sys` 两个前缀**，否则菜单树与译文包取不到（页面会停在启动闸门或 403）。

```
business/iam-ap/web/
├── index.html
├── package.json / tsconfig.json / vite.config.ts
└── src/
    ├── main.tsx              # 先 bootstrapI18n()（走本地缓存，防闪烁）再挂载 App
    ├── App.tsx               # 路由：/login + 受保护外壳；受保护路由**由菜单树派生**
    ├── types.ts              # 与 server Result/Controller 对齐的共享类型（含菜单/权限/多语言 DTO）
    ├── styles.css            # 设计体系 v2（令牌/外壳/KPI/面板/数据表/表单/登录页，浅深双主题）
    ├── lib/
    │   ├── api.ts            # 两个 axios 实例（/api/v1 与平台根路径）+ 拦截器 + Accept-Language
    │   ├── i18n.ts           # i18next 初始化 + 运行时拉包 + 版本缓存 + 缺 key 上报（§4）
    │   ├── iconRegistry.ts   # `icon: string` → 图标组件（未知标识回退，不渲染空白）
    │   ├── permCodes.ts      # 权限码常量（与 server 端 IamPermissionCodes 一一对应）
    │   ├── permissions.ts    # 纯函数判定：hasPermission / hasAny / hasAll（精确匹配，不通配）
    │   ├── usePermission.ts  # useHasPermission(...) 等 Hook（配合 <Perms>）
    │   ├── crypto.ts         # 口令第一层 PBKDF2 派生（浏览器侧）
    │   ├── theme.ts          # 主题读写（localStorage + <html data-theme>）
    │   └── format.ts         # 审计类型标签/语义色 + 时间格式化
    ├── store/
    │   ├── authStore.ts      # 登录态（token/refreshToken **落盘**）+ permissions（**仅内存**）
    │   └── menuStore.ts      # 后端菜单树/可路由菜单（`GET /sys/me/menus`，不落盘）
    ├── components/
    │   ├── ProtectedRoute.tsx  # 未登录跳 /login
    │   ├── BootGate.tsx        # 启动闸门：等齐「菜单 + 权限」再渲染控制台（避免抖动/误判 403）
    │   ├── RequirePerm.tsx     # 路由级权限守卫（无权限跳 /403）
    │   ├── Perms.tsx           # 按钮级权限包裹器（无权限**不挂载**，非 display:none）
    │   ├── LanguageSwitcher.tsx# 语言切换（顶栏 + 登录页共用，语言目录来自公开端点）
    │   ├── Layout.tsx          # 控制台外壳：菜单树渲染 + i18n 文案 + 语言/主题切换
    │   ├── Panel.tsx           # 工程化面板（统一标题栏 + 内容区）
    │   └── Icons.tsx           # 内联 SVG 图标集（不引入图标库依赖）
    └── pages/
        ├── pageRegistry.tsx  # `component` 标识 → 页面组件（与后端 sys_menu.component 对应）
        ├── LoginPage.tsx     # 统一登录（第一层 PBKDF2；含语言切换）
        ├── DashboardPage.tsx # 概览（有 iam:overview:view = 平台态势 8 KPI / 否则 = 个人准入矩阵 + 组织归属）
        ├── ForbiddenPage.tsx # 403（已登录但无该页权限）
        ├── NotFoundPage.tsx  # 404（路径不在当前用户的菜单集内）
        ├── ComingSoon.tsx    # 占位页（菜单已入库但前端尚无对应组件时的兜底，不白屏）
        ├── OrgPage.tsx       # 组织架构（组织树 + 节点 CRUD/移动/删除 + 挂人；AD 节点只读）
        ├── UsersPage.tsx     # 用户与档案（账号 + 档案 + 组织归属；创建/启停/重置/解锁/删除）
        ├── LockoutsPage.tsx  # 登录锁定（锁定清单 + 手动解锁）
        ├── AppMgmtPage.tsx   # 应用注册（接入码、状态、接入指引）
        ├── AdmissionsPage.tsx# 准入授权（用户 × 应用 × 角色组）
        ├── OrgGrantsPage.tsx # 组织授权（组织 × 应用 × 角色组，含子组织覆盖）
        ├── RolesPage.tsx     # 角色组（按 ap 聚合角色分布）
        ├── SessionsPage.tsx  # 在线会话（活跃会话 + 强制下线）
        ├── AuditPage.tsx     # 审计日志（类型筛选）
        ├── SettingsPage.tsx  # 系统设置（只读生效参数 + 目录 API / AD 同步面板）
        ├── ProfilePage.tsx   # 自助改密与账户信息（含组织归属）
        ├── MenusAdminPage.tsx       # 菜单管理（/menus）
        ├── PermissionsAdminPage.tsx # 权限管理（/permissions）
        ├── RolesAdminPage.tsx       # 角色管理（/roles-admin）
        └── I18nAdminPage.tsx        # 多语言（/i18n）
```

### 1.1 界面外壳与设计体系（v2 · 工业级控制台）

面向半导体 / CIM 场景的**高信息密度**控制台风格，兼顾美观与可读性。

**布局（`Layout.tsx`）**：标准的四段式管理后台外壳 —— **顶部 header**（模块标题 + 面包屑路径 + 环境标识 + 主题切换 + 用户菜单 + 退出）、**左侧可收缩菜单**（图标 + 名称 + 说明，收缩态仅留图标）、**右侧内容区**（**不设居中宽度上限，铺满可用宽度**，仅保留 14px gutter）、**底部 footer**（系统标识 + 构建模式）。

- **侧栏收缩**：桌面端点击顶栏按钮在 `232px ⇄ 64px` 间切换，状态持久化在 `localStorage['iam-sidebar-collapsed']`；`≤900px` 时侧栏转为抽屉式（带遮罩，路由切换自动收起）。
- **主题**：浅色 / 深色双主题，全部走 CSS 变量令牌，切换写入 `localStorage['iam-theme']` 并作用在 `<html data-theme>`；`index.html` 内置首屏内联脚本抢先应用，**避免主题闪烁（FOUC）**。
- **环境标识**：顶栏按 `import.meta.env.MODE` 显示 `DEVELOPMENT`（琥珀灯）/ `PRODUCTION`（绿灯），避免误在开发态当生产用。

**排版（半导体行业要求）**：

- **等宽字体栈用于编码类信息** —— 令牌算法、接入码（`mds-ap`）、用户 ID、租户、角色组、表格中的 ID 等统一用 `--font-mono`（JetBrains Mono / Cascadia Mono / Consolas 回退），与 UI 正文的无衬线字体区分，便于逐字符核对。
- **数字对齐** —— 全局 `font-variant-numeric: tabular-nums`，表格、KPI 数值、版本号等按等宽数字对齐，便于纵向比对。
- **小号大写字母标签** —— 表单字段标签、表头、KPI 标签统一 `uppercase + letter-spacing`，形成工程图纸式的层级感。
- **字号收敛**：正文 13px，辅助 12/12.5px，微标 10/11px；行高 1.5，压缩留白换取一屏更多信息。

**组件（`styles.css` + `Panel.tsx`）**：

| 组件 | 类名 | 用途 |
| --- | --- | --- |
| 面板 | `.panel` / `.panel-head` / `.panel-body` | 统一标题栏 + 内容区，承载表格/表单/信息块 |
| KPI 指标块 | `.kpi` / `.kpi-val` / `.kpi-label` | 左侧色条 + 等宽数值 + 大写标签 |
| 数据表 | `table.data` | 紧凑行高、吸顶表头、hover 高亮、数字右对齐 |
| 状态指示 | `.status` + `.led`（`ok`/`err`/`warn`/`info`） | 圆点 + 文字的明确状态表达（启用/停用/已准入） |
| 标签 | `.tag`（`pri`/`ok`/`warn`） | 等宽小标签，用于角色组、计数 |
| 键值矩阵 | `.meta` / `.meta-k` / `.meta-v` | 虚线分行的只读信息呈现 |
| 左右分栏 | `.split` | 主从式布局（如组织树 + 节点详情） |
| 组织树 | `.tree` / `.tree-row` / `.tree-toggle` / `.tree-name` / `.tree-code` | 层级缩进树；叶子节点保留不可见占位以对齐 |
| 键值明细 | `.kv` | 节点详情等「标签 : 值」成对展示 |
| 多选标签 | `.org-pick` / `.chip-x` | 组织归属等多选控件与已选项 |
| 提示条 | `.alert`（`err`/`ok`/`warn`） | 操作结果与校验反馈 |

> 图标全部为 `Icons.tsx` 中的内联 SVG（`currentColor` 描边），**不引入任何图标库依赖**，包体与许可都更可控。

### 1.2 信息架构（IA）：菜单树由后端下发

**功能全景（IAM = 统一身份 + 跨业务准入）**：概览 → 身份管理（**组织 → 人** → 锁定）→ 接入管理（应用、个人准入、**组织准入**、角色组）→ 安全与会话（会话、审计、设置）→ 系统配置（菜单/权限/角色/多语言）→ 我的。

> **组织排在人员之前**：先有组织树，才有归属与「按组织批量授权」，符合先建组织再挂人的实际工作顺序（IA v4）。

**侧栏结构 = 后端 `sys_menu` 表**（种子的单一事实源在 server 端 `IamConsoleCatalog`）：

| parent | type | 路由 | i18n_code | icon | perm_code |
| --- | --- | --- | --- | --- | --- |
| — | MENU | `/` | `iam.menu.overview` | `gauge` | —（登录即可，页内再按 `iam:overview:view` 二分） |
| DIR | — | — | `iam.menu.group.identity` | — | — |
| ↑ | MENU | `/orgs` | `iam.menu.orgs` | `tree` | `iam:org:list` |
| ↑ | MENU | `/users` | `iam.menu.users` | `users` | `iam:user:list` |
| ↑ | MENU | `/lockouts` | `iam.menu.lockouts` | `lock` | `iam:user:list` |
| DIR | — | — | `iam.menu.group.access` | — | — |
| ↑ | MENU | `/apps` | `iam.menu.apps` | `apps` | `iam:app:list` |
| ↑ | MENU | `/admissions` | `iam.menu.admissions` | `link` | `iam:admission:list` |
| ↑ | MENU | `/org-grants` | `iam.menu.orgGrants` | `org-grant` | `iam:grant:list` |
| ↑ | MENU | `/roles` | `iam.menu.roles` | `shield` | `iam:role:list` |
| DIR | — | — | `iam.menu.group.security` | — | — |
| ↑ | MENU | `/sessions` | `iam.menu.sessions` | `monitor` | `iam:session:list` |
| ↑ | MENU | `/audit` | `iam.menu.audit` | `list` | `iam:audit:list` |
| ↑ | MENU | `/settings` | `iam.menu.settings` | `sliders` | `iam:settings:view` |
| DIR | — | — | `iam.menu.group.system` | — | — |
| ↑ | MENU | `/menus` | `iam.menu.menus` | `tree` | `iam:menu:list` |
| ↑ | MENU | `/permissions` | `iam.menu.permissions` | `shield` | `iam:perm:list` |
| ↑ | MENU | `/roles-admin` | `iam.menu.rolesAdmin` | `shield` | `iam:role-admin:list` |
| ↑ | MENU | `/i18n` | `iam.menu.i18n` | `list` | `iam:i18n:list` |
| — | MENU | `/profile` | `iam.menu.profile` | `user` | —（登录即可） |
| （各 MENU 下挂 BUTTON 子节点） | BUTTON | — | `iam.btn.*` | — | 细粒度写权限（`iam:*:create/update/...`） |

**渲染链路（前端）**：

1. `BootGate` 就位后拉两份权威数据：`GET /sys/me/menus`（**后端已按角色 `sys_role_menu` 过滤**）→ `menuStore`；`GET /sys/me/permissions`（**直接回显已注入 `SecurityContext` 的 authorities、不重查库**）→ `authStore.permissions`（**仅内存、不落盘**）。
2. `Layout` 把菜单树映射为侧栏：`DIR` 节点 → 分组标题（`t(i18n_code)`）；其子 `MENU` → 条目（`t(i18n_code)` 名称 + `t(i18n_code + '.desc')` 副标题 + `iconRegistry` 图标）。`visible=false` 的节点不出现在导航（但路由仍可达，用于详情页这类"隐藏入口"）。
3. `App` 按 `flat`（`type=MENU` 且有 `path`，**按 path 去重**）**动态生成路由**：`path` → 路由，`component` → `pageRegistry` 解析组件，`permCode` → `RequirePerm` 守卫。
4. 找不到组件的 `component` → `ComingSoon`（路由可达、提示未实现，**不白屏**）；路径不在菜单集内 → 404；有菜单但无权限码 → 403。

**权限分层的三层（前端只管前两层）**：

| 层 | 载体 | 判据 | 说明 |
| --- | --- | --- | --- |
| 能进控制台 | 令牌 `apps` claim | IAM 准入 | `iam-ap` 是签发方，准入留空即放行（见 server §0） |
| 能看哪些页 | `sys_role_menu` | `GET /sys/me/menus` | 后端过滤，前端**不做任何权限判断** |
| 能点哪些按钮 | `sys_role_perm` + `<Perms>` | `GET /sys/me/permissions` | 前端隐藏入口；**真正鉴权在后端 `@PreAuthorize`** |

- **⚠️ 「菜单可见性」与「按钮/接口权限」是两套授权**，允许交叉配置，故「有菜单无权限码」是合法状态 —— 此时路由可达但被 `RequirePerm` 拦到 403（这是刻意的，不是 bug）。
- **⚠️ 不做通配**：后端 `hasAuthority(...)` 是**精确字符串匹配**，前端 `hasPermission` 同样精确匹配。超管之所以拥有全部权限，是因为 `cim-system` 在解析时把超管角色**展开为全部启用权限码**（`DbLocalAuthorityLoader` → `resolveAuthorities`），**不是**靠某个 `*` 通配——所以前端绝不能把 `SUPER_ADMIN` 当成 `*` 放通，否则会出现「前端显示按钮、后端 403」的假阳性。
- **⚠️ 角色名不可作判据**：角色是可配置的（`/roles-admin` 能新建角色），判据必须落在**权限码**上。历史实现里的 `isIamAdmin(user)`（判 `apps` 含 `iam-ap` 且 `roles` 含 `ADMIN`）已删除。
- **菜单即路由的推论**：**未分配任何控制台角色的用户没有菜单 ⇒ 没有任何页面**（落到 404）。这是「准入 ≠ 授权」的应有之义——能进 `iam-ap` 不等于能看控制台。种子提供两个内置角色：`IAM_ADMIN`（超管）与 `IAM_OPERATOR`（只读运维）。

- **按使用习惯设计的核心流程**：
  - *入职*：组织架构「新建节点 / 挂人」→ 用户与档案「新建账号 + 档案 + 组织归属」→ 组织授权「授予」（该组织人员自动继承）或准入授权「授予」（个人例外追加）→ 通知登录；
  - *停用/离职*：用户与档案「禁用」（自动 bump 强制下线）→ 准入授权「撤销」；
  - *排障*：审计日志定位 → 登录锁定「解锁」/ 在线会话「强制下线」；
  - *自助*：我的 → 改密（改密后强制重登）；
  - *配置化运维*：系统配置 → 菜单管理 / 权限管理 / 角色管理 / 多语言（**改完即时生效，无需发版**）。
- **内容区布局**：`.content` 不设居中宽度上限，铺满可用宽度，仅保留 14px gutter（高密度工业后台观感）。

## 2. 登录门户（统一登录）

- **第一层派生（明文口令不出浏览器）**：`lib/crypto.ts` 的 `deriveClientHash(password, clientSalt)` 用 `crypto.subtle` 做 `PBKDF2-SHA256(password, utf8(clientSalt), rounds=100_000)` → 256bit → hex。**与 server 端 `PasswordDerivation` 严格对齐**（salt 取 UTF-8 字节；rounds 与 `cim.iam.auth.password.rounds` 一致）。
- **登录流程（`LoginPage`）**：
  1. `GET /api/v1/login/salt?username=` 取服务端 `clientSalt`（未知用户返回空盐，防账号枚举）→ 兜底用 `randomClientSalt()`；
  2. 浏览器内 `deriveClientHash` → `clientHash`；
  3. `POST /api/v1/login {username, credential: clientHash, clientSalt}` → `{token, refreshToken, expiresInSeconds, tokenType}`；
  4. `authStore.setSession` + `loadMe()`（`GET /me`）→ 跳转 `/`。
- **会话存储**：`authStore`（zustand + persist，`name:'iam-auth'`）**只落盘 `token`/`refreshToken`**（`partialize` 显式裁剪），刷新不丢登录态；`user` 与 `permissions` **每次启动重取**。
  - ⚠️ **权限刻意不落盘**：权限随角色变更即时变化，落盘会带来「本机缓存的旧权限被当成本会话真值」——典型表现是「已撤权却仍看得到按钮」。`/sys/me/permissions` 每请求回显当前授权，重取即最新。
- **令牌附带**：`api.ts` 请求拦截器自动加 `Authorization: Bearer <token>` 与 `Accept-Language`；响应拦截器解包 `Result<T>`（`code===0` → 取 `data`，否则抛 `{code,msg,errors}`）；401 → 清态并跳 `/login`。
- **交互细节（按常用登录习惯设计）**：
  - **记住用户名**：勾选后仅把用户名写入 `localStorage['iam-last-user']`（**口令从不落盘**），下次进入自动回填；
  - **口令可见性切换**：口令框右侧眼睛按钮切换明文/密文；
  - **Caps Lock 提示**：口令框检测大写锁定并给出提示，减少输错；
  - **自动聚焦**：已记住用户名 → 直接聚焦口令框，否则聚焦用户名框；**回车即可提交**；
  - **失败回填友好**：登录失败自动聚焦并全选口令框，便于立即重输。
  - **语言切换**：登录面板右上角提供语言切换（译文包端点是公开的，登录前即可用），见 §4。

## 3. 管理控制台（功能页）

所有管理端点受 server 端**方法级** `@PreAuthorize("hasAuthority('iam:<域>:<动作>')")` 保护（类级保留 `iam:console:admin` 作兜底安全网，见 `../server/console-menu-perm-i18n.md` §4.4/§4.5）；前端仅做展示与调用——**无令牌 401、无权限码 403**。页面内所有写操作按钮均包 `<Perms code="...">`，无权限时**不挂载**（而非 `display:none`）。后端端点详见 `docs/business/iam-ap/server/README.md` §4(l)/§4(m)。

**按钮 → 权限码对照（已落地）**：

| 页面 | 操作 | 权限码 |
| --- | --- | --- |
| 组织架构 | 新建节点 / 保存 / 移动 / 删除 / 挂人与移除成员 | `iam:org:create` / `:update` / `:move` / `:delete` / `:member` |
| 用户与档案 | 新建账号 / 档案 / 归属 / 启停 / 重置口令 / 解锁 / 删除 | `iam:user:create` / `:update` / `:orgs` / `:status` / `:reset-pwd` / `:unlock` / `:delete` |
| 登录锁定 | 解锁 | `iam:user:unlock` |
| 应用注册 | 注册应用 / 启停 | `iam:app:create` / `:update` |
| 准入授权 | 授予·更新 / 撤销 | `iam:admission:grant` / `:revoke` |
| 组织授权 | 授予 / 撤销 | `iam:grant:grant` / `:revoke` |
| 在线会话 | 强制下线（行内 + 按用户 ID） | `iam:session:kick` |
| 系统设置 | 立即同步（AD） | `iam:sync:run` |
| 菜单/权限/角色/多语言管理 | 新建 / 编辑 / 删除 / 授权 / 语言维护 | `iam:menu:*`、`iam:perm:*`、`iam:role-admin:*`、`iam:i18n:*`（配平台码，见下） |

> 「刷新」这类只读重载按钮不单独包 `<Perms>`——它们由页面级的列表权限（菜单 `perm_code`）决定可达性。

### 3.1 配置化自助闭环：4 个管理页（W4 已落地）

菜单、按钮权限、角色授权、界面文案以后**由界面维护，无需发版**。路由 `/menus` `/permissions` `/roles-admin` `/i18n`。

| 页面 | 数据面（前端调用） | 入口闸门（路由 `perm_code`） |
| --- | --- | --- |
| 菜单管理 | 平台 `GET /sys/menus/tree` + `POST/PUT/DELETE /sys/menus` | `iam:menu:list` |
| 权限管理 | 平台 `GET /sys/permissions/page` + `POST/PUT/DELETE /sys/permissions` | `iam:perm:list` |
| 角色管理 | 平台 `GET /sys/roles/page`、`GET/PUT /sys/roles/{id}/permissions`、`.../menus`、角色 CRUD | `iam:role-admin:list` |
| 多语言 | IAM `GET/POST/DELETE /api/v1/admin/i18n/**`（语言目录 + 译文 + 缺失汇总） | `iam:i18n:list` |

> 平台端点**无 `/api/v1` 前缀**，故走 `lib/api.ts` 的 `rootApi` 实例（`rootGet/rootPost/rootPut/rootDel`）；
> IAM 端点走默认实例。路径前缀差异见 §1。

#### ⚠️ 双闸门（写操作务必按此渲染）

菜单/权限/角色三页的**数据面复用平台 `cim-system` 端点，而那些端点用平台码 `sys:*` 鉴权**；
控制台自己的 `iam:*` 只决定**入口显隐**。二者同时满足才能真正操作：

```
能读/能写 ⟺ 持有 IAM 控制台码 ∧ 持有对应平台码
```

- 前端用 `<Perms codes={[IAM码, SYS码]}>`（**AND** 语义，见 `components/Perms.tsx`）渲染写按钮；
  页级读权限用 `useHasAllPermissions([...])` 判定，缺失时整页显示 `iam.common.noPlatformPerm` 提示，
  **不再发起注定 403 的请求**。
- 平台码常量镜像在 `lib/permCodes.ts` 的 `SYS` 对象（与后端 `com.cim.system.support.PermissionCodes` 对齐）。
- **超管**（`IAM_ADMIN`）由「库内全部启用权限码」短路展开，天然具备两族 → 开箱即用；
  **只读运维**（`IAM_OPERATOR`）由种子补授平台只读码（`sys:menu:list`/`sys:permission:list`/`sys:role:list`）；
  **自定义角色**需在「角色管理」页把两族都勾上（该页权限选择器直接列出全部 `sys_permission` 行，可发现）。
- `/i18n` 页不受此约束：数据面是 IAM 自己的 `/api/v1/admin/i18n/**`，鉴权用 `iam:i18n:*`，**单闸门**。

#### 实现口径

- **菜单树含按钮节点**（`tree?includeButtons=true`）：`DIR`/`MENU`/`BUTTON` 三类同树编辑；`BUTTON` 必须绑 `perm_code`（后端 `SysMenuService.save` 强制），且不入导航。
- **图标不硬编码**：`icon` 只存注册表键（`lib/iconRegistry.ts`），下拉可选项即 `iconKeys()`；未知回退 `IconAlert`。
- **菜单表不存名称**：只存 `i18n_code`，名称 `t(i18n_code)`、副标题 `t(i18n_code + '.desc')`；新增菜单后须去「多语言」页补译文。
- **权限码三段由平台拆解**：前端只填 `code`，`SysPermissionService.applyCode` 拆出 `module/res/action`（前端仅前置校验「必须三段」）。
- **角色两张授权表并列、各自独立保存**（`sys_role_perm` / `sys_role_menu`）：刻意不合并——合并会让「能调用接口」与「看得见菜单」重新耦合（README §21.1）。「有菜单无权限码」是合法态。
- **编辑提交整实体**：`{...origin, ...edits}` 回填基础字段，避免平台 `AbstractJpaService.update` 丢列。


- **概览（DashboardPage）**：按权限二分呈现。**管理员** → `GET /admin/overview` 的平台态势（**8 个 KPI**：用户账号启用/总数、**用户档案（含 AD 同步数）**、**组织节点（含 IAM 自建数）**、接入应用启用/总数、在线会话、锁定账户、登录成功、登录失败）+ 最近审计事件表（可跳转审计页）。**普通用户** → 个人身份信息、会话与安全属性、准入与角色矩阵（来自 `GET /me`）**+ 我的组织归属**。
- **组织架构（OrgPage）**：左侧组织树（按 `path` 组装层级、可展开收起、按 `source` 标注 `AD 同步` 并置为只读）+ 右侧节点详情。支持新建节点（父节点/编码/名称/类型/排序）、编辑、**移动**（改父节点，重写子树路径）、删除（校验无子节点/无归属/无授予）、**成员挂人/移除**（`GET/PUT /admin/orgs/{id}/users`）。**AD 同步节点禁止本地改/删/移**（后端 `guardManaged` 拦截）。
- **用户与档案（UsersPage）**：列表取自「本地凭证 ∪ 用户档案」并集（**AD 同步来的人无本地凭证、只出现在档案侧**），列含用户名、工号、**来源（AD 同步 / IAM 自建）**、组织归属（多归属时并列展示）、启用/锁定状态、准入数、角色组。面板含：新建账号（可选同时建档案）、重置口令、**档案编辑**（AD 来源仅岗位可改）、**组织归属设置**（多选，含主属标记）。**禁用 / 重置 / 删除 / 归属变更均会 bump 令牌版本**。
- **登录锁定（LockoutsPage）**：`GET /admin/lockouts` 锁定清单（失败次数、首末失败时间、锁定至）；`DELETE /admin/lockouts/{username}` 手动解锁。
- **应用注册（AppMgmtPage）**：`GET/POST /apps` 注册接入码；行内启用/停用（`PUT /apps/{appCode}`，停用前二次确认——其下所有用户对该 ap 的准入立即失效）；附「接入指引」（JWKS 拉取、`apps` claim 准入、`roles` claim 角色组）。
- **准入授权（AdmissionsPage）**：以用户为主线，进入页面默认查询 `admin`（可见即所得）；`GET /apps/users/{userId}/assignments` 拉取该用户在全部已注册应用上的准入与角色组，逐行编辑角色组后「授予/更新」（`POST /apps/{appCode}/users`）或「撤销」（`DELETE /apps/{appCode}/users/{userId}`）。任何变更都会 bump 该用户令牌版本。
- **组织授权（OrgGrantsPage）**：`GET/POST /admin/org-grants`、`DELETE /admin/org-grants/{orgId}/{appCode}`。清单展示「组织 × 接入码 × 角色组 × 是否覆盖子组织」；授予表单可选组织、接入码、角色组（逗号分隔）、**覆盖子组织**开关，并**实时显示该授予的受影响人数**。页面附「与准入授权的分工」说明：**组织授予** 给组织整体准入（人员进出组织自动继承/失去），**个人准入** 在其上做例外追加（个人只能加、不能减）。变更同样 bump 受影响用户的令牌版本（含子组织成员）。
- **角色组（RolesPage）**：`GET /admin/roles` 按接入码聚合**实际在用**的角色名与人数（不维护独立字典表，避免定义与实际脱节）。
- **在线会话（SessionsPage）**：`GET /admin/sessions` 活跃会话（未撤销未过期的刷新令牌 ≈ 一个登录会话）；行内「强制下线」（`DELETE /admin/sessions/{userId}`）与「按用户 ID 强制下线」（`POST /internal/token-version/bump?uid=`，`postRaw` 透传裸对象）。两者都是 bump 令牌版本 → 验证端（IAM 自身经 `LocalTokenVersionChecker` 进程内直查库）即时判定，无缓存 TTL 滞后。
- **审计日志（AuditPage）**：`GET /admin/audit?limit=&type=` 动作流水（登录成功/失败/被拒、登出、改密、账号增删改、应用注册/更新、准入授予/撤销、**组织增删改移、档案创建/更新、用户归属变更、组织授予/撤销、目录同步**、强制下线），类型/结果以标签 + LED 双色标识，支持按类型与条数过滤。
- **系统设置（SettingsPage）**：`GET /admin/settings` **只读**展示生效中的运行时策略（认证源、PBKDF2 轮数与 pepper 是否已配（不回显值）、访问/刷新令牌 TTL、issuer/kid、RSA 私钥是否 KMS 注入、锁定阈值/时长/窗口、跨域白名单、JWKS 路径）；刻意不支持在线修改（避免与已签发令牌/验证端漂移）。另含两个面板：**身份目录 · 只读 API**（是否已配服务密钥、认证方式 `X-Directory-Key`、目录端点前缀、水位机制）与 **身份目录 · AD 同步**（同步开关、是否可连、检索基址、同步间隔、跨源保护说明）+ 「**立即同步**」按钮（`POST /admin/sync/ad`，未配置 AD 时返回 `skipped` 并提示）。
- **自助改密（ProfilePage）**：`POST /api/v1/me/password`，与登录一致在浏览器内先对旧/新口令做第一层 PBKDF2 派生，仅传 `clientHash`——请求体 `{ oldCredential, newCredential, newClientSalt }`（旧口令派生前先 `GET /api/v1/login/salt?username=` 取服务端盐，新口令由客户端 `randomClientSalt()` 生成随机盐后派生）。明文口令不出浏览器。页面另展示**档案摘要（姓名/工号/岗位/来源）与组织归属标签**（来自 `GET /me`）。
  - **改密后强制重登**：服务端改密成功即 `bump` 该用户令牌版本（旧会话全部失效，含当前会话）。前端收到成功回执后**清空本地会话（`authStore.clear()`）并跳转登录页**，提示「口令已更新，请用新口令重新登录」，避免用户停留在已失效的会话上。

- **配置化自助闭环（4 个管理页，`/menus` `/permissions` `/roles-admin` `/i18n`）**：见 §3.1。菜单、按钮权限、角色授权、界面文案以后**由界面维护，无需发版**。

---

### 3.2 测试与验证口径

本工程**刻意不引入 vitest / jest / RTL / Playwright**（前端零测试框架依赖）。四层验证由轻到重：

| 层 | 命令 / 位置 | 覆盖什么 | 代价 |
| --- | --- | --- | --- |
| ① 类型 | `npm run build`（内含 `tsc -p tsconfig.json`） | 类型、未用变量/参数、JSX 契约 | 秒级 |
| ② 渲染契约 | `npm test` → `test/run.mjs` + `test/render-contract.tsx` | **闸门与骨架**：给定权限码集时页面渲染出哪些元素、请求了哪些文案键 | 秒级，**无需浏览器** |
| ③ 接口端到端 | `business/iam-ap/server` 的 `@SpringBootTest`（如 `UserListProjectionTest`、`ConsolePermissionI18nTest`） | 后端契约（含真实 HTTP 语义 401/403） | 需要后端可编译 |
| ④ 视觉回归 | 本地脚本（无头 Chrome + CDP，**不入库**） | 浅/深双主题实拍、交互路径、横向溢出量化 | 需起后端 + 前端 |

**② 渲染契约测试的设计**（`test/render-contract.tsx`，21 条断言）：

- 用 `react-dom/server` 的 `renderToStaticMarkup` 把四个配置页渲染成 HTML 字符串，在 Node 里断言其内容——**不装 jsdom、不启浏览器**。
- 因为 `useEffect` 在 SSR 不执行，页面停在「未加载」首屏；因此它断言的是 **闸门 + 骨架 + 文案键**，而非数据渲染（数据渲染由 ③④ 覆盖）。
- i18next 初始化时**不灌任何资源包**，缺失处理器把键包成 `⟪key⟫` 返回。于是断言 `⟪iam.admin.menus.tree⟫` 出现的语义是「这里**请求了** `iam.admin.menus.tree` 这条键」——比断言中文更稳（改文案不误伤），并且顺带验证键名没写错。
- 关键断言即**双闸门**：只持 `iam:menu:list`（缺 `sys:menu:list`）→ 只渲染无权限提示；两码齐备 → 才渲染主体与写按钮。`/i18n` 页反向断言**单闸门**（只持 `iam:i18n:list`、完全不含 `sys:*` 也能正常渲染）。
- 写按钮一律按 **`<button class="icon-btn" title="⟪key⟫"` 精确断言**，不按文案断言——同一文案键常被「空态提示」复用（如权限页右侧空态 `新增权限` 与面板头 ＋ 按钮同为 `iam.admin.perms.new`），只按文案会把「已挂载」误判成「不存在」。

> ⚠️ **两个实现坑（已在代码注释里写明）**：
> ① zustand 4 的 `useStore` 用 `api.getServerState || api.getInitialState` 作 `getServerSnapshot`，
> 而 `setState` 只替换 `state` 引用、不碰创建时捕获的初始对象 → 只 `setState` 时 SSR 读到的权限**永远是空数组**，
> 表现为「所有页面都被判成无权限」。测试里需**同时**就地改写初始快照。
> ② 用 esbuild 打成 ESM 单文件后，`react-dom` 等 CJS 依赖运行期 `require('stream')` 会撞上 esbuild 的
> 「Dynamic require is not supported」占位 → 需在产物顶部用 `createRequire` 先注入真实 `require`
> （`test/run.mjs` 的 `banner`）。

**③ 后端侧的对应测试**（详见 `../server/README.md` §4 与 `../server/console-menu-perm-i18n.md` §10/§11）：

- `UserListProjectionTest`（9 条）覆盖用户清单「本地凭证 ∪ 用户档案」并集投影的 service/repository 两层，并**实测钉死**了「方法级 `@PreAuthorize` **覆盖**类级、不是 AND 叠加」这一反直觉语义。
- `ConsoleMenuIconContractTest`（2 条）是**跨端契约测试**：直接读前端 `lib/iconRegistry.ts` 解析键集，与后端 `IamConsoleCatalog.menus()` 的 `icon` 比对——把「菜单种子用的图标标识必须全部存在于前端注册表」这条 Java↔TS 字符串约定变成可失败断言（注册表未命中只会静默回落兜底图标，不会编译/启动报错）。
- `ConsolePermissionI18nTest`（9 条）覆盖 W4 三页数据面落在平台 `sys/**` 端点、且入口码 `iam:*` 与平台码 `sys:*` **双闸门**并行生效（只读运维角色已补授平台只读码）。

---

## 4. 多语言（i18n）

目标是「**翻译后展示 + 中文兜底**」：界面文案全部入库（`sys_locale` + `sys_i18n`），改文案不发版。

**运行时链路（`lib/i18n.ts`）**

1. **启动**：`main.tsx` 先 `bootstrapI18n()` 再用**本地缓存的整包**同步渲染（防闪白/闪中文），随后**异步**比对版本拉最新包，最后才挂载 React。
2. **拉包**：`GET /api/i18n/messages?lang=<BCP-47>&v=<版本号>`（`cim-i18n-starter` 的**公开**端点，登录页也需要）。
3. **语言切换**：`LanguageSwitcher` 写 `localStorage['iam-lang']` + 更新 `Accept-Language` 请求头 + 重拉 bundle + `i18n.changeLanguage()`，**不刷新页面**。语言目录来自公开端点 `GET /api/i18n/locales`（库内新增语种 → 下拉自动出现，无需改前端；拉取失败回退内置 `zh-CN`/`en-US`）。

**中文兜底的三层保障**

| 层 | 机制 | 效果 |
| --- | --- | --- |
| 服务端整包合并 | `I18nService#frontendBundle` 按「内置(默认) → 内置(目标) → DB(默认) → DB(目标)」合并 | 目标语言只译了一部分（或压根未收录，如 `fr-FR`）时，**返回的仍是完整中文包**，前端一次请求即可 |
| i18next | `fallbackLng: 'zh-CN'` | 键在目标语言缺失时回落默认语言 |
| 兜底渲染 | `parseMissingKeyHandler` → `humanize(key)`（如 `iam.menu.orgs` → `Orgs`） | 四级全未命中时**绝不渲染裸 key** |

**缺 key 上报**：`saveMissing` + `missingKeyHandler` 把真缺口（四级全未命中）去重后**延迟 3s 批量** `POST /api/i18n/missing`，生成待翻译工单；开发态同时打印控制台告警。管理端可在「多语言」页查看缺失汇总。

**⚠️ 两个实现坑（已在代码注释中固化）**

- **`v` 版本号不能只缓存版本**：端点约定「`v` 与当前版本一致 → 只回版本、`messages` 为空（省流量）」。若前端只把版本号落盘、不落**整包**，刷新页面就会拿到空包、界面全空。故 `lib/i18n.ts` 按语言把**译文包整体**写 `localStorage['iam-i18n-bundle:<lang>']`，启动先用它渲染。
- **不能用 Suspense**：init 显式设 `initImmediate: false`（资源为空对象、同步完成）与 `react.useSuspense: false`。文案是「先渲染缓存、后台刷新」模式，挂起会导致白屏/闪烁。

**文案键命名（`iam.*`）**

| 前缀 | 含义 | 例 |
| --- | --- | --- |
| `iam.menu.*` | 菜单名（= `sys_menu.i18n_code`） | `iam.menu.orgs` = 组织架构 |
| `iam.menu.*.desc` | 菜单副标题（侧栏第二行 / 页头说明；**菜单表不存字段**，靠 `i18n_code + '.desc'` 约定） | `iam.menu.orgs.desc` = 厂区→产线→工序 |
| `iam.btn.*` | 按钮名（= BUTTON 节点的 `i18n_code`） | `iam.btn.user.create` = 新建用户 |
| `iam.page.*` | 管理页标题与说明 | `iam.page.menus.title` |
| `iam.field.*` / `iam.type.*` | 表单字段 / 枚举展示名 | `iam.field.permCode` / `iam.type.button` |
| `iam.shell.*` / `iam.common.*` / `iam.login.*` | 外壳、通用提示、登录页 | `iam.shell.tagline` / `iam.login.title` |

> 菜单**表里没有名称字段**（只有 `i18n_code`），这是刻意设计：避免「名称硬编码」与「全量 DB i18n」自相矛盾（见 `cim-system` `SysMenu` javadoc）。种子与译文键的单一事实源同在 server 端 `IamConsoleCatalog`。

**⚠️ 存量页面文案的 i18n 迁移尚未完成**：外壳（含登录页、菜单、页头、403/404）已全部走 `t()`；各业务页**正文文案**（表格列名、表单标签、提示语）目前仍是中文硬编码，随 W4 逐页迁移。切到 `en-US` 时可见：菜单/页头/按钮框的框架文案为英文，页内明细仍为中文——这是**已知的阶段性状态**，不是缺陷。

---

## 5. 构建与运行

```bash
cd business/iam-ap/web
npm install                       # 安装依赖
npm run dev                       # 开发（端口 5171；/api 与 /sys 均代理到 :8081）
npm run build                     # tsc -p tsconfig.json && vite build → dist/
npm run preview                   # 预览产物
```

- `package-lock.json` 入库（锁定依赖）；`node_modules/`、`dist/` 已由根 `.gitignore` 忽略。
- **生产部署：必须把 `/api` 与 `/sys` 两个前缀都反向代理到 iam-ap server**（与开发态同理）。只代 `/api` 会导致 `/sys/me/menus`、`/sys/me/permissions` 取不到 → 卡在启动闸门；`/api/i18n/**` 随 `/api` 一起代理即可。
- 后端必须监听 **8081**（或同步调整 `vite.config.ts` 的 proxy target）；IAM 自身端点在 `/api/v1/**`，平台 `cim-system` 端点在 `/sys/**`。

## 6. 安全要点

- **明文口令不出浏览器**：登录口令仅以 `clientHash`（PBKDF2 第一层产物）提交；服务端再做第二层派生比对。
- **跨域**：server 端 `IamWebProperties.allowedOrigins`（默认 `http://localhost:5171`）+ `IamSecurityConfig` 的 `CorsFilter` 放行前端开发源；生产按域名收紧。
- **鉴权分层（前端不可越权）**：
  1. 未登录 → `ProtectedRoute` 跳 `/login`（缓存 401 由响应拦截器统一清态跳登录）；
  2. 准入 → 令牌 `apps` claim，由后端过滤器判定（403）；
  3. 页面可达（`sys_role_menu`）与按钮/接口权限（`sys_role_perm`）→ 后端 `@PreAuthorize` **方法级精确匹配**；
  4. 前端 `<Perms>` / `RequirePerm` 只负责「不给无权限的人看到入口」，**不构成安全边界**。
- **权限不落盘**：`permissions` 仅驻内存，每次启动重取，避免「本机旧权限」被当成本会话真值；控制台登出时 `menuStore.clear()` 清菜单残留，防下一个登录者看到上一个人的菜单。
- **译文接口公开**：`/api/i18n/**` 无需认证（登录页也要用），内容为展示层文案，不含敏感信息；管理端读写译文仍走 `/api/v1/admin/i18n/**` 并受 `iam:i18n:*` 权限保护。

