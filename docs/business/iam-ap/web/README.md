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

```
business/iam-ap/web/
├── index.html
├── package.json / tsconfig.json / vite.config.ts
└── src/
    ├── main.tsx              # 挂载 BrowserRouter + App
    ├── App.tsx               # 路由表（/login + 受保护路由）
    ├── types.ts              # 与 server Result/Controller 对齐的共享类型
    ├── styles.css            # 控制台样式（header/nav/card/form/table）
    ├── lib/
    │   ├── crypto.ts         # 口令第一层 PBKDF2 派生（浏览器侧）
    │   └── api.ts            # axios 实例 + 拦截器 + get/post/put/del/postRaw
    ├── store/
    │   └── authStore.ts      # zustand 登录态（token/refreshToken/user）
    ├── components/
    │   ├── ProtectedRoute.tsx# 未登录跳 /login
    │   └── Layout.tsx        # 顶部导航 + 加载 /me + 退出
    └── pages/
        ├── LoginPage.tsx     # 统一登录（第一层 PBKDF2）
        ├── DashboardPage.tsx # 概览（身份/apps/roles）
        ├── AppMgmtPage.tsx   # 应用与准入管理
        ├── TokenVersionPage.tsx # 令牌踢人（bump 版本）
        └── ProfilePage.tsx   # 自助改密
```

## 2. 登录门户（统一登录）

- **第一层派生（明文口令不出浏览器）**：`lib/crypto.ts` 的 `deriveClientHash(password, clientSalt)` 用 `crypto.subtle` 做 `PBKDF2-SHA256(password, utf8(clientSalt), rounds=100_000)` → 256bit → hex。**与 server 端 `PasswordDerivation` 严格对齐**（salt 取 UTF-8 字节；rounds 与 `cim.iam.auth.password.rounds` 一致）。
- **登录流程（`LoginPage`）**：
  1. `GET /api/v1/login/salt?username=` 取服务端 `clientSalt`（未知用户返回空盐，防账号枚举）→ 兜底用 `randomClientSalt()`；
  2. 浏览器内 `deriveClientHash` → `clientHash`；
  3. `POST /api/v1/login {username, credential: clientHash, clientSalt}` → `{token, refreshToken, expiresInSeconds, tokenType}`；
  4. `authStore.setSession` + `loadMe()`（`GET /me`）→ 跳转 `/`。
- **会话存储**：`authStore`（zustand + persist，`name:'iam-auth'`）存 `token/refreshToken/user` 于 localStorage，刷新不丢；`ProtectedRoute` 据此守卫。
- **令牌附带**：`api.ts` 请求拦截器自动加 `Authorization: Bearer <token>`；响应拦截器解包 `Result<T>`（`code===0` → 取 `data`，否则抛 `{code,msg,errors}`）；401 → 清态并跳 `/login`。

## 3. 管理后台（准入与账户自助）

所有管理端点受 server 端 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")` 保护；前端仅做展示与调用。

- **概览（DashboardPage）**：从 `authStore.user`（来自 `GET /me`）展示 userId/username/tenantId/可进入 apps/各 ap 角色。
- **应用与准入（AppMgmtPage）**：
  - 列表 `GET /api/v1/apps`；
  - 注册 `POST /api/v1/apps {appCode, appName, sortNo}`；
  - 分配 `POST /api/v1/apps/{appCode}/users {userId, roles:Set<String>}`（逗号分隔解析）；
  - 撤销 `DELETE /api/v1/apps/{appCode}/users/{userId}`；
  - 查询用户已准入应用 `GET /api/v1/apps/users/{userId}/apps`。
- **令牌踢人（TokenVersionPage）**：`POST /api/v1/internal/token-version/bump?uid=`（`postRaw` 透传裸对象）→ 该用户令牌版本 +1，验证端缓存到期后所有存量令牌失效、需重新登录。本地账号 userId 即用户名。
- **自助改密（ProfilePage）**：`POST /api/v1/me/password {oldPassword, newPassword}`。
  - ⚠️ **已知不一致（非阻塞）**：当前改密接口按服务端契约接收**明文口令**（与登录第一层 PBKDF2 不一致）；已在 `ProfilePage` 标注，后端列为待优化项（后续拟改为客户端先派生后传 `clientHash`）。

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
