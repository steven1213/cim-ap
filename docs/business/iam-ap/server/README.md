# IAM-AP 后端设计文档（骨架）

> 本文件为 **骨架占位**，仅记录已确认的架构边界与认证方案。详细设计待后续按章填充。
> IAM 是基础设施型 ap，承载统一身份与跨业务**准入**控制。

## 0. 定位与设计原则

- **归属**：`docs/business/iam-ap/`，作为业务层中的基础设施型 ap（与 `mds-ap` / `mes-ap` 等同构，都含 `server/` 与 `web/`）。
- **职责边界（核心）**：IAM **只做各业务系统的准入权限控制，不做业务系统内部的权限控制**。
  - ✅ IAM 负责：企业内统一登录、令牌颁发/刷新、跨业务**准入**（用户能否进入某个 ap）、ap 内**粗角色组**（如 `mds-ap` 的「管理员 / 操作员」）。
  - ❌ IAM 不负责：业务系统的菜单 / 按钮 / 数据行级权限 —— 这些由**各业务 ap 自行控制**（复用平台框架 RBAC，后端文档 §21.1）。
- **基于 platform 框架实现**：IAM 自身不另起炉灶，复用 `docs/platform/server` 的 Maven 多模块、通用数据模型、审计历史等能力。
- **与框架 `cim-auth-starter` 的关系**：平台框架的认证 starter 改为**对接 IAM 令牌**（验签 + 准入 claim 校验），业务系统内部的 RBAC 仍走 §21.1。

## 1. 企业内认证源（已确认：对接 LDAP / AD）

- 员工身份由企业 **LDAP / Active Directory** 托管，IAM 不重建员工目录。
- IAM 自身维护两张映射：
  - **用户 ↔ 应用准入**：某用户可进入哪些 ap（`apps` 列表）。
  - **用户 ↔ ap 内粗角色组**：在某 ap 内的粗粒度分组（如管理员 / 操作员），**不跨业务**。

## 2. 令牌方案（已确认：令牌 claim + JWKS 本地验签）

- OAuth2 / OIDC 风格，**JWT（RS256 非对称）** + **JWKS 公钥分发**。
- 令牌 claim 约定：
  - `sub`：用户身份标识。
  - `apps`：该用户可进入的 ap 列表（准入依据）。
  - `roles`：各 ap 内的粗角色组（可选，形如 `{ "mds-ap": ["ADMIN"] }`）。
- 业务系统**本地验签**：用 IAM 的 JWKS 公钥验证签名与有效期，**不每请求回查 IAM**（性能与可用性更优）。

## 3. ap 接入 / 注册（已确认：动态纳入）

- IAM 维护**应用接入表**，新增 `mds-ap` / `mes-ap` 时注册即可，动态纳入并下发对应准入，无需改 IAM 代码。

## 4. 已落地能力（首批实现 · 2026-10-08；管理面与前端于后续扩展）

> 基于 platform 框架（`cim-spring-support` / `cim-jpa-starter` / `cim-auth-starter`）落地，**41 个测试全绿**：
> - 首批 24（`AccountLockServiceTest`×4 / `LoginLockoutTest`×2 / 登录·刷新·签发·会话端到端若干）；
> - 管理面 5：`AdminSecurityTest`×4（管理面鉴权）+ `BootstrapAdminTest`×1（首管理员引导端到端）；
> - 改密派生 2：`LocalCredentialServiceChangePasswordTest`×2（clientHash 改密正确/错误两态）；
> - 改密失效 5：`LocalTokenVersionCheckerTest`（版本比对单元）+ `ChangePasswordInvalidatesSessionTest`（改密后旧令牌 401 → 重登恢复端到端）；
> - 管理控制台 5：`AdminConsoleTest`×5（用户清单/创建、禁用后登录被拒、锁定列表与解锁、审计与概览/设置/角色组/会话、非管理员 403）。
> 包结构：`com.cim.iam.server.{token,app,auth,config,admin,audit}`（新增 `admin` 管理面与 `audit` 审计包）。

**(a) 令牌版本存储 / 递增 / 下发（`token` 包）** — T6.5 在 IAM 侧的落点（对应 design.md §8.1(g)）
- `TokenVersion` 实体（`token_version` 表，`user_id` 唯一）+ `TokenVersionRepository` + `TokenVersionService`：`currentVersion(userId)`（首访建 1）/ `bump(userId)`（改权限/改密/踢人后 +1）。
- `TokenVersionController` 暴露内部端点供验证端比对：
  - `GET /api/v1/internal/token-version?uid=` → 纯文本当前版本。
  - `POST /api/v1/internal/token-version/bump?uid=` → `{uid, version}`（bump 后）。
- 验证端桥接：`cim-auth-starter` 的 `IamTokenVersionChecker` 按 `cim.auth.token-version.iam-base-url` 配置指向本服务，拉取版本比对令牌 `ver`；IAM 不可达时 fail-open（放行）。

**(b) ap 注册 / 准入接入码下发（`app` 包）** — 边界③（对应 design.md §8.1(e)）
- `AppRegistration`（`app_registration` 表，`app_code` 唯一，`status=ENABLED/DISABLED`）+ `UserAppAssignment`（`user_app_assignment` 表，`(user_id, app_code)` 唯一，`roles` 逗号分隔）。
- `AppRegistrationService`：注册/更新/禁用 ap、分配/撤销用户、计算某用户可进入的 `apps` 集合与各 ap 粗角色组；**任何分配/撤销/禁用都会 `bump` 相关用户令牌版本**，触发已签发令牌即时失效。
- `AppRegistrationController`：`POST/GET /api/v1/apps`、`PUT /api/v1/apps/{appCode}`、`POST /api/v1/apps/{appCode}/users`、`DELETE /api/v1/apps/{appCode}/users/{userId}`、`GET /api/v1/apps/users/{userId}/apps`。

**(c) 令牌签发与 JWKS（`token` 包）** — 边界②
- `RsaKeyService`：生产由 `cim.iam.jwt.private-key-pem/public-key-pem` 注入（KMS）；留空则启动生成临时 RSA-2048 密钥对（仅本地联调）。
- `JwksController`：`GET /.well-known/jwks.json` → `{keys:[{kty:RSA,alg:RS256,use:sig,kid,n,e}]}`（base64url）。
- `TokenIssuerService.issue(req)`：RS256 签名，claim 含 `uid`/`uname`/`apps`/`roles`/`tenantId`/`ver`，`kid` 取自 `cim.iam.jwt.kid`。`TokenIssueController.POST /api/v1/token/issue` → `{token}`。

**(d) 登录流程（`auth` 包）** — M-login（§1 认证源对接 + §2 登录颁发）
- `AuthenticationSource` SPI + 两实现：`LocalAuthenticationSource`（`cim.iam.auth.source=local`，默认启用）、`LdapAdAuthenticationSource`（`=ldap`，JNDI simple bind 对接企业 LDAP/AD）。
- `LoginService.login`：按 `source` 选源 → 认证失败抛 `noAdmission` → 成功先 `bump` 该用户令牌版本（旧令牌即时失效，单会话语义）→ 再签发新令牌（携带最新 `ver`）。
- `LoginController`：`POST /api/v1/login`（入参 `username`/`credential`/`clientSalt`）→ `{token, expiresInSeconds, tokenType}`；`GET /api/v1/login/salt?username=` 下发每用户 `clientSalt`（未知用户返回空盐，防账号枚举）。

**(e) 口令两层派生（`auth/PasswordDerivation`）** — §5.1 口令前端加密 / 服务端二次派生
- 第一层（客户端）：`clientHash = PBKDF2(password, clientSalt, rounds)`，明文口令不出浏览器。
- 第二层（服务端）：`serverHash = PBKDF2(clientHash, serverPepper, rounds)`；入库仅存 `serverHash`，`serverPepper` 为服务端密钥（`cim.iam.auth.password.pepper`，配置 / KMS，**不入库**）。
- `local_credential` 表（V2 迁移）存 `client_salt` + `server_hash` + `enabled`，**不存明文口令**。
- LDAP 模式下 AD 需明文 bind，故不采用客户端散列、改由 `ldaps://` TLS 承载传输安全；两层派生是「本地凭证」策略，按 `source` 切换，互不耦合。

**(f) 刷新令牌（`auth` 包）** — M-refresh（对应 design.md §2 刷新轮换）
- `RefreshToken` 实体（`refresh_token` 表：明文令牌只存 `SHA-256(tokenHash)`，`token_hash` 唯一；`access_token_jti` 关联、`expires_at`/`revoked`/`revoked_at`）+ `RefreshTokenRepository` + `RefreshTokenService`：`issue(userId, accessToken)` 返回明文（库内仅散列）、`rotate(plaintext)` 撤销旧刷新令牌并签发新访问令牌+新刷新令牌（防重放）、`revoke(plaintext)`；`jtiOf(accessToken)` 解析 JWT payload 取 `jti`。
- `LoginController.POST /api/v1/token/refresh`（入参 `refreshToken`）→ `{token, refreshToken, expiresInSeconds, tokenType}`；旧刷新令牌立即失效（再使用 → 403）。

**(g) 登出与黑名单（`token` + `auth` 包）** — M-refresh（对应 design.md §8.1(h)）
- `BlacklistedToken` 实体（`token_blacklist` 表，`jti` 唯一，`user_id`/`expires_at`/`revoked_at`）+ `TokenBlacklistRepository` + `TokenBlacklistService`：`revoke(jti, userId, expiresAt)` 幂等、`isRevoked(jti)` → boolean。
- `TokenBlacklistController.GET /api/v1/internal/token-blacklist?jti=` → 纯文本 `true`/`false`（供验证端 `IamTokenBlacklistChecker` 调用）。
- `LoginService.logout(accessToken, refreshToken)`：解析访问令牌取 `jti+uid` → 拉黑该 `jti`（即时杀当前会话）+ bump 用户版本（兜底杀其余存量令牌）+ 撤销刷新令牌；`LoginController.POST /api/v1/logout`（读 `Authorization` 头 Bearer + body `refreshToken`）→ 200。
- 验证端桥接：`cim-auth-starter` 的 `IamTokenBlacklistChecker` 按 `cim.auth.token-blacklist.iam-base-url` 配置指向本服务，按 `jti` 比对；命中 → 401。与版本机制互补：黑名单精细（单条令牌），版本粗放（用户级全量）。

**(h) 登录失败锁定（`auth` 包）** — M-lockout（暴力破解防护，对应 design.md §8.1(i)）
- `AccountLock` 实体（`account_lock` 表，`username` 唯一小写，`fail_count`/`first_fail_at`/`last_fail_at`/`locked_until`）+ `AccountLockRepository` + `AccountLockService`：`isLocked(username)`（过期懒清理）、`onFailure(username)`（窗口内累计，达 `maxAttempts` 锁定 `lockMinutes`）、`onSuccess(username)`（清零计数）、`remainingAttempts(username)`。
- 滑动窗口：`firstFailAt + windowMinutes < now` 时历史失败清零，避免旧失败永久累计。
- `LoginService.login` 接入：进入即 `isLocked` 预检（已锁定直接抛 `ACCOUNT_LOCKED` 1004，不区分用户是否存在）；认证失败 `onFailure`；成功 `onSuccess`。
- 配置项（默认：`cim.iam.auth.lockout.max-attempts=5`、`lock-minutes=15`、`window-minutes=15`）。

**(i) 管理面鉴权（自签发 JWT + `iam-ap:ADMIN`）** — 边界③管理端点守护（对应 design.md §8.1）
- `IamJwksKeyProvider extends JwksKeyProvider`（`config` 包）：覆盖 cim-auth-starter 默认的 HTTP JWKS 提供器（starter 该 Bean 为 `@ConditionalOnMissingBean`），使 IAM **验证自身签发的令牌走 in-JVM 公钥**（`RsaKeyService.getPublicKey()`），免环回网络依赖、MockMvc 测试无需真实端口即可验签。父类按 kid 缓存，IAM 单密钥场景直接读内存公钥返回。
- `IamSecurityConfig`（`config` 包）：注册 `IamJwksKeyProvider` Bean（顶替默认）；并独立 `CorsFilter`（`@Order(HIGHEST_PRECEDENCE)`）放行 `cim.iam.web.allowed-origins`（默认 `http://localhost:5171`，生产按域名收紧），使 business/iam-ap/web 可调用本服务。
- 管理端点守护：`AppRegistrationController` 全部端点加 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")`。IAM 自签 RS256 令牌的 `authorities`/`roles` claim 含 `iam-ap:ADMIN`（由 `BootstrapAdminRunner` 赋权 / 业务赋权写入）；验证侧 JWT 过滤器解析 claim → 方法级鉴权放行/拒绝。
- 行为：`/api/v1/apps` 无令牌 → 401；持 `iam-ap:ADMIN` 令牌 → 200；持非管理员令牌（如仅 `mds-ap:ADMIN`）→ 403。`AdminSecurityTest`×4 覆盖上述三态。

**(j) 首管理员引导 + 当前用户端点 + 自助改密** — 管理面初始化与账户自助（对应 design.md §8.1）
- `BootstrapAdminRunner`（`ApplicationRunner`，`config` 包）：库内无引导管理员时（`cim.iam.auth.bootstrap.admin-username`，默认 `admin`）自动创建首管理员——① 注册 `iam-ap` 应用（管理端点鉴权依赖 `iam-ap:ADMIN`）② 两层派生建本地凭证（服务端完成 clientHash+serverHash）③ 赋 `iam-ap:ADMIN`。`admin-password` 为空则生成随机 16 位口令并打印日志（仅 dev；生产务必注入强口令）。`bootstrap.enabled=false` 可禁用。重复启动自动跳过。
- `ProfileController`（`auth` 包，`/api/v1/me`）：`GET /me`（需 `isAuthenticated()`）返回 `MeDto{userId,username,tenantId,apps,roles}`（apps/roles 由 `AppRegistrationService` 计算）；`POST /me/password` 自助改密（仅本地凭证账号，AD/LDAP 账号改密在目录侧）。`changePassword` 与登录一致接收客户端第一层派生的 `clientHash`（`oldCredential`/`newCredential`）+ 新随机盐 `newClientSalt`，服务端仅做第二层派生校验旧口令 + 入库新 clientHash/newClientSalt（明文口令不出浏览器）。**改密成功后 `bump` 该用户令牌版本**（见 §4(k)），使全部旧会话即时失效。`LocalCredentialServiceChangePasswordTest`×2 覆盖「正确旧 clientHash 改密成功且新口令可验/旧口令失效」与「错误旧 clientHash 被拒」。
- 测试：`BootstrapAdminTest`×1 端到端验证「引导创建 admin（含 `iam-ap:ADMIN`）→ 用引导口令登录成功拿令牌」。

**(k) 改密后强制作废旧会话（令牌版本校验本体）** — 让「改密 / 踢人 / 改权限」的失效真正在 IAM 自身生效
- 背景：JWT 过滤器链路为「验签 → **版本失效判定(401)** → `apps` 准入(403) → 加载权限 → `SecurityContext`」，其中版本判定由 `TokenVersionChecker` SPI 承担；platform 默认实现 `acceptAll()`（不校验），需接入方顶替方能生效。
- `LocalTokenVersionChecker`（`token` 包，实现 `TokenVersionChecker`）：**进程内直查库**比对令牌 `ver` claim 与 `TokenVersionService.currentVersion(uid)`，不一致即拒绝（过滤器返回 401）。相比 HTTP 回查 IAM 无 TTL 滞后、无网络往返。`ver` claim 由 `TokenIssuerService` 写入、`JwtVerifier` 解析为 `TokenClaims.version()`。
- 注册：`IamSecurityConfig` 以 `@Bean` 注册 `LocalTokenVersionChecker`，凭 `@ConditionalOnMissingBean` 顶替 platform 默认 `acceptAll()`。
- 触发点：`LocalCredentialService.changePassword` 在入库新凭证后立即 `tokenVersionService.bump(userId)`；因用户名即本地账号 `userId`（`ProfileController` 从 `CimUserPrincipal.username()` 取，与令牌 `uname`/`uid` 同源），bump 目标与令牌主体一致。使用者亦可是 `AppRegistrationService` 的分配/撤销（§4(b)）与登出（§4(g)）。
- 行为：改密后**当前会话令牌亦失效**（版本整体 +1）→ 前端 `ProfilePage` 收到成功回执后清空本地会话并跳登录页，用户以新口令重登。`ChangePasswordInvalidatesSessionTest` 端到端验证「改密前 `/me` 200 → 改密 → 旧令牌 `/me` 401 → 新口令重登 → 新令牌 `/me` 200」。

**(l) 管理控制台端点（`admin` 包）+ 审计流水（`audit` 包）** — 管理面可运营、可观测
> 全部端点类级 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")`，非管理员一律 403（`AdminConsoleTest` 覆盖）。

- **审计（`audit` 包）**：`AuditEvent`（`audit_event` 表，V5 迁移）+ `AuditEventRepository` + `AuditService`。`AuditService.record` 采用 **`REQUIRES_NEW` 独立事务**——审计常出现在「业务即将抛异常」的路径（登录失败、锁定拒绝），同事务会随之回滚导致「失败事件查不到」；独立事务确保审计先落地，且审计自身异常不影响业务（内部 try/catch + 日志）。`AuditType` 为受控词表（`LOGIN_SUCCESS/FAILURE/REJECTED`、`LOGOUT`、`PASSWORD_CHANGED/RESET`、`USER_CREATED/ENABLED/DISABLED/DELETED/UNLOCKED`、`APP_REGISTERED/UPDATED`、`ADMISSION_GRANTED/REVOKED`、`SESSION_REVOKED`）。埋点位点：`LoginService`（登录成功/失败/锁定拒绝/登出）、`LocalCredentialService`（创建/启停/重置/删除/自助改密）、`AppRegistrationService`（注册/更新/授予/撤销）、`UserAdminController`/`LockoutAdminController`/`SessionAdminController`（解锁、强制下线）。
- **概览**：`GET /api/v1/admin/overview` → 用户数（启用/禁用）、应用数（启用）、活跃会话数、锁定数、登录成功/失败累计、最近 8 条审计事件。
- **用户账号**（`UserAdminController`，`/api/v1/admin/users`）：`GET` 清单（含启用态、锁定态、准入数、角色组）；`POST` 创建本地账号（收客户端第一层派生的 `clientHash` + `clientSalt`）；`PUT /{userId}/status` 启用/禁用（**禁用即 bump 强制下线**）；`PUT /{userId}/password` 管理员重置口令（**重置即 bump 强制下线**）；`POST /{userId}/unlock` 解锁；`DELETE /{userId}` 删除（**删除即 bump 强制下线**）。
- **在线会话**（`SessionAdminController`，`/api/v1/admin/sessions`）：`GET` 活跃会话（未撤销且未过期的刷新令牌 ≈ 一个登录会话，回填用户名）；`DELETE /{userId}` 强制下线（bump 版本 → 返回 `{uid,version}`）。
- **登录锁定**（`LockoutAdminController`，`/api/v1/admin/lockouts`）：`GET` 当前锁定账号（`lockedUntil`/失败次数/首末失败时间，含过期懒清理）；`DELETE /{username}` 手动解锁。`AccountLockService` 相应新增 `listActiveLocks()` / `unlock()` / `find()`。
- **角色组**（`RoleAdminController`，`/api/v1/admin/roles`）：按接入码聚合**实际在用**的粗角色组与人数（不落独立字典表，避免定义与实际脱节）。
- **审计查询**（`AuditAdminController`，`/api/v1/admin/audit`）：`GET ?limit=&type=` → 最近事件（`AuditEventDto`，不直接暴露含审计列的实体）。
- **系统设置**（`SettingsAdminController`，`/api/v1/admin/settings`）：只读暴露**生效中**的运行时参数（认证源、PBKDF2 轮数、pepper 是否已配（不回显值）、访问/刷新令牌 TTL、issuer/kid、RSA 私钥是否 KMS 注入、锁定阈值/时长/窗口、跨域白名单、JWKS 路径）。刻意不支持在线修改（改配置走发布流程，避免与已签发令牌/验证端漂移）。
- **准入明细**：`GET /api/v1/apps/users/{userId}/assignments` → 该用户各 ap 的角色组（供前端「准入授权」页编辑）。

> 端到端已覆盖（会话生命周期）：`SessionIntegrationTest` 以真实端口启动 IAM，把验证侧 `jwks-uri` 与 `token-blacklist.iam-base-url` 都指向 IAM 自身，跑通「登录 → 刷新轮转 → 登出拉黑 → 验证侧 401 拒绝」全链路；与 `RefreshTokenServiceTest`/`TokenBlacklistServiceTest` 共同覆盖刷新与吊销分支。

> 端到端已覆盖（失败锁定）：`LoginLockoutTest`（`max-attempts=2`）跑通「连续错误 2 次触发锁定 → 第 3 次即便口令正确也 `ACCOUNT_LOCKED`」与「成功登录清零计数后可重试」；`AccountLockServiceTest` 覆盖窗口内计数、滑动窗口重置、成功清零、剩余次数等分支。

> 端到端已覆盖（令牌签发）：`IamApIntegrationTest` 跑通「注册 mds-ap → 分配 u1(ADMIN) → 签发（apps={mds-ap}, ver=1）→ 版本端点下发 1 → bump 后 2 → 重新签发 ver=2 → 撤销分配后 apps={}/ver=3」。
>
> 端到端已覆盖（登录）：`LoginIntegrationTest` 跑通「注册本地用户 alice（两层派生入库）→ 分配 mds-ap(ADMIN) → POST /api/v1/login 返回令牌（uid=alice, apps 含 mds-ap）→ 错误口令 403 → GET /api/v1/login/salt 下发 clientSalt」；`LoginServiceTest` 验证再次登录版本自增（旧令牌随之失效）。

## 5. 待补章节（后续里程碑）

| 章节 | 内容 | 状态 |
| --- | --- | --- |
| §1 认证源对接 | LDAP / AD 接入、绑定、失败锁定 | **首批已落地（local 模式 + LDAP/AD 适配器见 §4(d)；失败锁定见 §4(h)）** |
| §2 令牌颁发与 JWKS | 登录颁发、刷新轮换、即时吊销 | **首批已落地（签发+JWKS 见 §4(c)；登录颁发见 §4(d)；刷新轮换见 §4(f)；登出/黑名单/吊销见 §4(g)）** |
| §5.1 口令两层派生 | 前端加密 + 服务端二次派生 | **首批已落地（见 §4(e)）** |
| §3 准入模型 | `apps` / `roles` 数据模型与分配管理 | **首批已落地（见 §4(b)）** |
| §4 ap 注册接入 | 接入表、密钥 / 公钥登记、scope 约定 | **首批已落地（见 §4(b)）** |
| §5 会话与吊销 | 刷新令牌、黑名单、登出、令牌版本失效 | **首批已落地（版本存储/递增见 §4(a)、验证侧强制失效见 §4(k)；刷新见 §4(f)；登出/黑名单/吊销见 §4(g)；端到端见 SessionIntegrationTest / ChangePasswordInvalidatesSessionTest）** |
| §6 快速开始与部署 | 基于 platform 的启动、部署形态 | 骨架已具备（H2 + Flyway 本地起） |

> 设计合理性已在总文档评审中确认：IAM 只管准入、业务自管内部权限，是 OAuth2 `audience` 与业务 RBAC 的标准分层。
