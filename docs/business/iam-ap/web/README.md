# IAM-AP 前端设计文档

> 本文件记录 **已落地** 的 IAM 控制台前端实现（统一登录门户 + 管理后台）。
> IAM 是基础设施型 ap，承载统一身份与跨业务**准入**控制（只管准入，不碰业务内部权限）。
> 后端契约见 `docs/business/iam-ap/server/README.md`。

## 0. 定位

- **归属**：`docs/business/iam-ap/web/`，基于平台前端框架（`docs/platform/web`）构建，不另起炉灶。
- **职责**：统一登录门户 + IAM 管理后台（应用接入管理、准入分配、粗角色组分配、令牌踢人、自助改密）。
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
    ├── App.tsx               # 路由表（/login + 受保护路由）
    ├── types.ts              # 与 server Result/Controller 对齐的共享类型
    ├── styles.css            # 设计体系 v2（令牌/外壳/KPI/面板/数据表/表单/登录页，浅深双主题）
    ├── lib/
    │   ├── crypto.ts         # 口令第一层 PBKDF2 派生（浏览器侧）
    │   ├── theme.ts          # 主题读写（localStorage + <html data-theme>）
    │   └── api.ts            # axios 实例 + 拦截器 + get/post/put/del/postRaw
    ├── store/
    │   └── authStore.ts      # zustand 登录态（token/refreshToken/user）
    ├── components/
    │   ├── ProtectedRoute.tsx# 未登录跳 /login
    │   ├── Layout.tsx        # 控制台外壳：顶部 header + 可收缩侧栏 + 内容区 + footer
    │   ├── Panel.tsx         # 工程化面板（统一标题栏 + 内容区）
    │   └── Icons.tsx         # 内联 SVG 图标集（不引入图标库依赖）
    └── pages/
        ├── LoginPage.tsx     # 统一登录（第一层 PBKDF2）
        ├── DashboardPage.tsx # 概览（身份/apps/roles）
        ├── AppMgmtPage.tsx   # 应用与准入管理
        ├── TokenVersionPage.tsx # 令牌踢人（bump 版本）
        └── ProfilePage.tsx   # 自助改密
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

## 3. 管理后台（准入与账户自助）

所有管理端点受 server 端 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")` 保护；前端仅做展示与调用。

- **概览（DashboardPage）**：从 `authStore.user`（来自 `GET /me`）呈现三层信息 —— ① **KPI 指标块**（用户 ID / 可进入应用数 / 角色组数 / 租户）；② **身份信息**与**会话与安全**两个只读面板（认证源、签名算法、验签方式、准入判定、失效机制）；③ **准入与角色矩阵**数据表（按接入码列出准入状态 LED 与角色组标签）。
- **应用与准入（AppMgmtPage）**：
  - 列表 `GET /api/v1/apps`；
  - 注册 `POST /api/v1/apps {appCode, appName, sortNo}`；
  - 分配 `POST /api/v1/apps/{appCode}/users {userId, roles:Set<String>}`（逗号分隔解析）；
  - 撤销 `DELETE /api/v1/apps/{appCode}/users/{userId}`；
  - 查询用户已准入应用 `GET /api/v1/apps/users/{userId}/apps`。
- **令牌踢人（TokenVersionPage）**：`POST /api/v1/internal/token-version/bump?uid=`（`postRaw` 透传裸对象）→ 该用户令牌版本 +1，验证端版本判定不通过即拒绝该用户全部存量令牌、需重新登录（IAM 自身经 §4(k) 的 `LocalTokenVersionChecker` 进程内直查库比对，无缓存 TTL 滞后）。本地账号 userId 即用户名。
- **自助改密（ProfilePage）**：`POST /api/v1/me/password`，与登录一致在浏览器内先对旧/新口令做第一层 PBKDF2 派生，仅传 `clientHash`——请求体 `{ oldCredential, newCredential, newClientSalt }`（旧口令派生前先 `GET /api/v1/login/salt?username=` 取服务端盐，新口令由客户端 `randomClientSalt()` 生成随机盐后派生）。明文口令不出浏览器，已与登录对齐。
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
