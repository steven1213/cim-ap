# IAM-AP 前端设计文档

> 本文件记录 **已落地** 的 IAM 控制台前端实现（统一登录门户 + 管理后台）。
> IAM 是基础设施型 ap，承载统一身份与跨业务**准入**控制（只管准入，不碰业务内部权限）。
> 后端契约见 `docs/business/iam-ap/server/README.md`。

## 0. 定位

- **归属**：`docs/business/iam-ap/web/`，基于平台前端框架（`docs/platform/web`）构建，不另起炉灶。
- **职责**：统一登录门户 + IAM 管理控制台（概览、用户账号、登录锁定、应用注册、准入授权、角色组、在线会话、审计日志、系统设置、账户自助）。菜单由**注册表驱动并按权限过滤**（见 §1.2），不再硬编码固定几项。
- **不负责**：业务系统的菜单 / 按钮管理 UI —— 由各业务 ap 自己的前端承载。
- **无三方登录**：认证源为 IAM 后端（LDAP / AD 或本地凭证），前端只对接 `iam-ap` 后端。

## 1. 技术栈与目录

- **栈**：React 18 + TypeScript 5.6 + Vite 5（`@vitejs/plugin-react`）。
- **状态/请求**：`zustand`（persist 到 localStorage，刷新不丢登录态）+ `axios`（请求/响应拦截器）。
- **路由**：`react-router-dom` v6（`BrowserRouter` + `ProtectedRoute` 守卫）。
- **别名**：`tsconfig.json` 配 `@/*` → `src/*`；`vite.config.ts` 同配，`dev` 端口 `5171`。
- **开发代理**：`vite.config.ts` 将 `/api` 代理到 `http://localhost:8081`（iam-ap server 端口），避免跨域联调。
  - ⚠️ iam-ap server 必须监听 **8081**（`application.yml` 的 `server.port: 8081`；缺省会退化为 Spring Boot 默认 8080 → 代理连不上，浏览器见 500）。模板见 `server/src/main/resources/application.yml.example`。
- **API 基址**：`src/lib/api.ts` 的 axios `baseURL = '/api/v1'`（后端所有端点均带 `/api/v1` 前缀），页面内调用只写资源段（如 `/login`、`/apps`、`/me`）。

```
business/iam-ap/web/
├── index.html
├── package.json / tsconfig.json / vite.config.ts
└── src/
    ├── main.tsx              # 挂载 BrowserRouter + App
    ├── App.tsx               # 路由表（/login + 受保护路由；管理页统一套 RequireAdmin）
    ├── types.ts              # 与 server Result/Controller 对齐的共享类型
    ├── styles.css            # 设计体系 v2（令牌/外壳/KPI/面板/数据表/表单/登录页，浅深双主题）
    ├── lib/
    │   ├── crypto.ts         # 口令第一层 PBKDF2 派生（浏览器侧）
    │   ├── theme.ts          # 主题读写（localStorage + <html data-theme>）
    │   ├── api.ts            # axios 实例 + 拦截器 + get/post/put/del/postRaw
    │   ├── permissions.ts    # isIamAdmin()：依据 apps + roles 判定管理面权限
    │   ├── menu.tsx          # 菜单注册表（分组 + 权限可见性），信息架构的唯一来源
    │   └── format.ts         # 审计类型标签/语义色 + 时间格式化
    ├── store/
    │   └── authStore.ts      # zustand 登录态（token/refreshToken/user）
    ├── components/
    │   ├── ProtectedRoute.tsx# 未登录跳 /login
    │   ├── RequireAdmin.tsx  # 非 IAM 管理员跳 /（管理页路由守卫）
    │   ├── Layout.tsx        # 控制台外壳：顶部 header + 可收缩分组侧栏 + 内容区 + footer
    │   ├── Panel.tsx         # 工程化面板（统一标题栏 + 内容区）
    │   └── Icons.tsx         # 内联 SVG 图标集（不引入图标库依赖）
    └── pages/
        ├── LoginPage.tsx     # 统一登录（第一层 PBKDF2）
        ├── DashboardPage.tsx # 概览（管理员=平台态势 / 普通用户=个人准入矩阵）
        ├── UsersPage.tsx     # 用户账号（创建/启停/重置口令/解锁/删除）
        ├── LockoutsPage.tsx  # 登录锁定（锁定清单 + 手动解锁）
        ├── AppMgmtPage.tsx   # 应用注册（接入码、状态、接入指引）
        ├── AdmissionsPage.tsx# 准入授权（用户 × 应用 × 角色组）
        ├── RolesPage.tsx     # 角色组（按 ap 聚合角色分布）
        ├── SessionsPage.tsx  # 在线会话（活跃会话 + 强制下线）
        ├── AuditPage.tsx     # 审计日志（类型筛选）
        ├── SettingsPage.tsx  # 系统设置（只读生效参数）
        └── ProfilePage.tsx   # 自助改密与账户信息
```

### 1.1 界面外壳与设计体系（v2 · 工业级控制台）

面向半导体 / CIM 场景的**高信息密度**控制台风格，兼顾美观与可读性。

**布局（`Layout.tsx`）**：标准的四段式管理后台外壳 —— **顶部 header**（模块标题 + 面包屑路径 + 环境标识 + 主题切换 + 用户菜单 + 退出）、**左侧可收缩菜单**（图标 + 名称 + 说明，收缩态仅留图标）、**右侧内容区**（最大宽度 1320px，居中）、**底部 footer**（系统标识 + 构建模式）。

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
| 提示条 | `.alert`（`err`/`ok`/`warn`） | 操作结果与校验反馈 |

> 图标全部为 `Icons.tsx` 中的内联 SVG（`currentColor` 描边），**不引入任何图标库依赖**，包体与许可都更可控。

### 1.2 信息架构（IA）与菜单注册表

**功能全景（IAM = 统一身份 + 跨业务准入）**：概览 → 身份管理（账号、锁定）→ 接入管理（应用、准入、角色组）→ 安全与会话（会话、审计、设置）→ 我的。

**菜单由 `lib/menu.tsx` 注册表驱动**（不再在组件里硬编码）：

| 分组 | 菜单项 | 路由 | 权限 | 对应后端 |
| --- | --- | --- | --- | --- |
| — | 概览 | `/` | 登录即可 | `GET /admin/overview`（管理员）/ `GET /me`（普通用户） |
| 身份管理 | 用户账号 | `/users` | ADMIN | `/admin/users` CRUD |
| 身份管理 | 登录锁定 | `/lockouts` | ADMIN | `/admin/lockouts` |
| 接入管理 | 应用注册 | `/apps` | ADMIN | `/apps` |
| 接入管理 | 准入授权 | `/admissions` | ADMIN | `/apps/{code}/users`、`/apps/users/{uid}/assignments` |
| 接入管理 | 角色组 | `/roles` | ADMIN | `/admin/roles` |
| 安全与会话 | 在线会话 | `/sessions` | ADMIN | `/admin/sessions`、`/internal/token-version/bump` |
| 安全与会话 | 审计日志 | `/audit` | ADMIN | `/admin/audit` |
| 安全与会话 | 系统设置 | `/settings` | ADMIN | `/admin/settings` |
| — | 我的 | `/profile` | 登录即可 | `/me/password` |

- **权限可见性**：菜单项声明 `admin?: boolean`；`Layout` 用 `isIamAdmin(user)`（apps 含 `iam-ap` 且 roles 含 `ADMIN`）过滤，被过滤空的分组不渲染。管理员在顶栏额外显示 `ADMIN` 标识。
- **路由守卫**：管理页统一套 `RequireAdmin`（非管理员重定向到 `/`）。前端守卫只负责体验，**真正鉴权始终在后端**（无令牌 401 / 非管理员 403）。
- **按使用习惯设计的核心流程**：
  - *入职*：用户账号「新建」→ 准入授权「授予」ap + 角色组 → 通知登录；
  - *停用/离职*：用户账号「禁用」（自动 bump 强制下线）→ 准入授权「撤销」；
  - *排障*：审计日志定位 → 登录锁定「解锁」/ 在线会话「强制下线」；
  - *自助*：我的 → 改密（改密后强制重登）。
- **内容区布局**：`.content` 不设居中宽度上限，铺满可用宽度，仅保留 14px gutter（高密度工业后台观感）。

## 2. 登录门户（统一登录）

- **第一层派生（明文口令不出浏览器）**：`lib/crypto.ts` 的 `deriveClientHash(password, clientSalt)` 用 `crypto.subtle` 做 `PBKDF2-SHA256(password, utf8(clientSalt), rounds=100_000)` → 256bit → hex。**与 server 端 `PasswordDerivation` 严格对齐**（salt 取 UTF-8 字节；rounds 与 `cim.iam.auth.password.rounds` 一致）。
- **登录流程（`LoginPage`）**：
  1. `GET /api/v1/login/salt?username=` 取服务端 `clientSalt`（未知用户返回空盐，防账号枚举）→ 兜底用 `randomClientSalt()`；
  2. 浏览器内 `deriveClientHash` → `clientHash`；
  3. `POST /api/v1/login {username, credential: clientHash, clientSalt}` → `{token, refreshToken, expiresInSeconds, tokenType}`；
  4. `authStore.setSession` + `loadMe()`（`GET /me`）→ 跳转 `/`。
- **会话存储**：`authStore`（zustand + persist，`name:'iam-auth'`）存 `token/refreshToken/user` 于 localStorage，刷新不丢；`ProtectedRoute` 据此守卫。
- **令牌附带**：`api.ts` 请求拦截器自动加 `Authorization: Bearer <token>`；响应拦截器解包 `Result<T>`（`code===0` → 取 `data`，否则抛 `{code,msg,errors}`）；401 → 清态并跳 `/login`。
- **交互细节（按常用登录习惯设计）**：
  - **记住用户名**：勾选后仅把用户名写入 `localStorage['iam-last-user']`（**口令从不落盘**），下次进入自动回填；
  - **口令可见性切换**：口令框右侧眼睛按钮切换明文/密文；
  - **Caps Lock 提示**：口令框检测大写锁定并给出提示，减少输错；
  - **自动聚焦**：已记住用户名 → 直接聚焦口令框，否则聚焦用户名框；**回车即可提交**；
  - **失败回填友好**：登录失败自动聚焦并全选口令框，便于立即重输。

## 3. 管理控制台（功能页）

所有管理端点受 server 端 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")` 保护；前端仅做展示与调用（无令牌 401、非管理员 403）。后端端点详见 `docs/business/iam-ap/server/README.md` §4(l)。

- **概览（DashboardPage）**：按权限二分呈现。**管理员** → `GET /admin/overview` 的平台态势（6 个 KPI：用户账号启用/总数、接入应用启用/总数、在线会话、锁定账户、登录成功、登录失败）+ 最近审计事件表（可跳转审计页）。**普通用户** → 个人身份信息、会话与安全属性、准入与角色矩阵（来自 `GET /me`）。
- **用户账号（UsersPage）**：`GET /admin/users` 清单（含启用态、锁定态、准入数、角色组）；「新建账号」在浏览器内对初始口令做第一层 PBKDF2 后 `POST /admin/users`；行内操作 = 启用/禁用（`PUT /{userId}/status`）、解锁（`POST /{userId}/unlock`）、重置口令（`PUT /{userId}/password`，浏览器内派生）、删除（`DELETE /{userId}`，二次确认）。**禁用 / 重置 / 删除均会 bump 令牌版本 → 该用户所有会话立即失效**。
- **登录锁定（LockoutsPage）**：`GET /admin/lockouts` 锁定清单（失败次数、首末失败时间、锁定至）；`DELETE /admin/lockouts/{username}` 手动解锁。
- **应用注册（AppMgmtPage）**：`GET/POST /apps` 注册接入码；行内启用/停用（`PUT /apps/{appCode}`，停用前二次确认——其下所有用户对该 ap 的准入立即失效）；附「接入指引」（JWKS 拉取、`apps` claim 准入、`roles` claim 角色组）。
- **准入授权（AdmissionsPage）**：以用户为主线，进入页面默认查询 `admin`（可见即所得）；`GET /apps/users/{userId}/assignments` 拉取该用户在全部已注册应用上的准入与角色组，逐行编辑角色组后「授予/更新」（`POST /apps/{appCode}/users`）或「撤销」（`DELETE /apps/{appCode}/users/{userId}`）。任何变更都会 bump 该用户令牌版本。
- **角色组（RolesPage）**：`GET /admin/roles` 按接入码聚合**实际在用**的角色名与人数（不维护独立字典表，避免定义与实际脱节）。
- **在线会话（SessionsPage）**：`GET /admin/sessions` 活跃会话（未撤销未过期的刷新令牌 ≈ 一个登录会话）；行内「强制下线」（`DELETE /admin/sessions/{userId}`）与「按用户 ID 强制下线」（`POST /internal/token-version/bump?uid=`，`postRaw` 透传裸对象）。两者都是 bump 令牌版本 → 验证端（IAM 自身经 `LocalTokenVersionChecker` 进程内直查库）即时判定，无缓存 TTL 滞后。
- **审计日志（AuditPage）**：`GET /admin/audit?limit=&type=` 动作流水（登录成功/失败/被拒、登出、改密、账号增删改、应用注册/更新、准入授予/撤销、强制下线），类型/结果以标签 + LED 双色标识，支持按类型与条数过滤。
- **系统设置（SettingsPage）**：`GET /admin/settings` **只读**展示生效中的运行时策略（认证源、PBKDF2 轮数与 pepper 是否已配（不回显值）、访问/刷新令牌 TTL、issuer/kid、RSA 私钥是否 KMS 注入、锁定阈值/时长/窗口、跨域白名单、JWKS 路径）；刻意不支持在线修改（避免与已签发令牌/验证端漂移）。
- **自助改密（ProfilePage）**：`POST /api/v1/me/password`，与登录一致在浏览器内先对旧/新口令做第一层 PBKDF2 派生，仅传 `clientHash`——请求体 `{ oldCredential, newCredential, newClientSalt }`（旧口令派生前先 `GET /api/v1/login/salt?username=` 取服务端盐，新口令由客户端 `randomClientSalt()` 生成随机盐后派生）。明文口令不出浏览器。
  - **改密后强制重登**：服务端改密成功即 `bump` 该用户令牌版本（旧会话全部失效，含当前会话）。前端收到成功回执后**清空本地会话（`authStore.clear()`）并跳转登录页**，提示「口令已更新，请用新口令重新登录」，避免用户停留在已失效的会话上。

## 4. 构建与运行

```bash
cd business/iam-ap/web
npm install                       # 安装依赖
npm run dev                       # 开发（端口 5171，/api 代理到 :8081）
npm run build                     # tsc -p tsconfig.json && vite build → dist/
npm run preview                   # 预览产物
```

- `package-lock.json` 入库（锁定依赖）；`node_modules/`、`dist/` 已由根 `.gitignore` 忽略。
- 生产部署：将 `dist/` 交由网关/静态服务托管，并将 `/api` 反向代理到 iam-ap server（与开发态同理）。

## 5. 安全要点

- **明文口令不出浏览器**：登录口令仅以 `clientHash`（PBKDF2 第一层产物）提交；服务端再做第二层派生比对。
- **跨域**：server 端 `IamWebProperties.allowedOrigins`（默认 `http://localhost:5171`）+ `IamSecurityConfig` 的 `CorsFilter` 放行前端开发源；生产按域名收紧。
- **鉴权分层**：前端守卫只挡未登录；真正的准入/管理权限由 server 端 `iam-ap:ADMIN` 守护，前端无法越权（无令牌 401、非管理员 403）。
