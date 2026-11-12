# AUDIT.md — cim-ap 审计报告

> 按模块记录变更范围、验证证据、关键修复、风险与待办。配合 [PROCESS.md](./PROCESS.md) 的检查点表使用。

## 2026-10-08 批量提交（建立 PROCESS 机制 + 5 模块收口）

### 1. IAM 统一登录（M-login / M-refresh / M-lockout）
- **范围**：`business/iam-ap/server`（token/app/auth/config 包 + 迁移 V1–V4）、`cim-auth-starter`（SPI 校验链：版本/黑名单检查器 + 过滤器插入）、`cim-spring-support`（`BizCode`/`BizException` 新增 `ACCOUNT_LOCKED`，`GlobalExceptionHandler` 映射）。
- **验证（已跑通）**：
  - iam-ap：`29/29` 测试全绿（M-login 15 + M-refresh 3 + M-lockout 6 + 管理面 `AdminSecurityTest`×4 + 首管理员引导 `BootstrapAdminTest`×1）。
  - platform：`27/27` 测试全绿（cim-auth-starter 含新增 `IamTokenVersionChecker`/`IamTokenBlacklistChecker` 各 4 测 + 集成测试）。
- **关键修复 / 设计决策**：
  - `SecurityAutoConfiguration` 装配顺序陷阱：默认 `acceptAll` Bean 原带 `havingValue=""` 条件，配真 URL 时仍命中 → 改为具体 IAM 实现声明在前、默认仅 `@ConditionalOnMissingBean`（版本/黑名单两处同修）。
  - `jti` claim 接入 + 按 jti 黑名单跨模块契约，经 `SessionIntegrationTest` 真实端口端到端验证（IAM 拉黑 → 验证侧 401）。
  - 刷新令牌轮转（仅存 SHA-256 散列、防重放）+ 用户级 `ver` bump 兜底，与黑名单正交。
  - 登录失败锁定：滑动窗口计数，达阈值锁定，成功清零；预检不区分用户是否存在（防账号枚举）。
- **风险 / 待办**：AD 真实联调、刷新/锁定 TTL 灰度与监控。（前端登录页 + 第一层派生 JS、`cim-iam-ap` web 端已于 2026-10-09 落地，详见下方新增章节。）

### 2. MDS 主数据设计文档集
- **范围**：`docs/business/mds-ap/server/**`（39 篇设计文档 + 新增 `operating-profile-design.md`、蓝图/backlog/README）。
- **验证**：两轮文档评审收口（非代码测试）。
- **风险 / 待办**：文档对应落地代码尚未开始；`process-flow-design.md` 等为设计态，需与实现对齐。

### 3. cim-mq-starter（统一消息抽象）
- **范围**：`platform/server/cim-mq-starter/**`（outbox / resilience(Resilience4j) / idempotent / provider(Pulsar·Kafka·RabbitMQ) / listener / observability / serialize / spi / autoconfigure + 测试）。
- **验证**：**2026-10-09 全量回归通过**——随 platform 整仓 `mvn install`（13 模块全绿）运行，`cim-mq-starter` 模块 `BUILD SUCCESS`（含其单测），无编译/测试漂移。
- **风险 / 待办**：需补 provider 级集成测试，确认 outbox→broker 投递语义、幂等去重、乱序处理；`cim-mq-starter-usage.md` 同步纳入。

### 4. cim-cache-starter（多级缓存与集群）
- **范围**：`platform/server/cim-cache-starter/**`（cluster / config / guard / multi / support + resources + test）。
- **验证**：**2026-10-09 全量回归通过**——随 platform 整仓 `mvn install`（13 模块全绿）运行，`cim-cache-starter` 模块 `BUILD SUCCESS`（含其单测），无编译/测试漂移。
- **风险 / 待办**：集群模式一致性、guard 限流/击穿防护需补测试。

### 5. platform 底座（server 侧其余模块）
- **范围**：`cim-system`（域模块：rbac/role/user/permission/menu/dict/log/autoconfigure 等，删除 `CimSystemMarker` 占位）、`cim-jpa-starter`（Flyway 多目录 locations 定制、`DbCapabilities` 方言派生）、`cim-bootstrap`（装配 + `application.yml.example`）、根 `pom.xml`、平台文档 `docs/platform/server/README.md`、`docs/repo/{roadmap,structure}.md`。
- **验证**：**2026-10-09 全量回归通过**——platform 整仓 `mvn install` 13 模块全部 `BUILD SUCCESS`；其中 `cim-system` 16 测（1 跳过）、`cim-bootstrap` 装配冒烟 5 测全绿，确认域模块与 bootstrap 装配通过、Flyway 多目录追加顺序正常。
- **风险 / 待办**：无遗留回归风险。

## 2026-10-09（续）IAM 管理面 + 前端控制台 + 编译修复

### 6. IAM 管理面（server 扩展）
- **范围**：`business/iam-ap/server`
  - 新增 `config` 包：`IamSecurityConfig`（注册 in-JVM 验签 Bean + `TokenVersionChecker` Bean + `CorsFilter`）、`IamJwksKeyProvider extends JwksKeyProvider`（覆盖 starter 默认 HTTP JWKS，验证自身令牌走内存公钥）、`IamWebProperties`（`cim.iam.web.allowed-origins`，默认 `http://localhost:5171`）、`BootstrapAdminRunner`（`ApplicationRunner` 首管理员引导）。
  - 新增 `token/LocalTokenVersionChecker`（实现 `TokenVersionChecker` SPI，进程内直查库比对令牌 `ver` 与 `TokenVersionService.currentVersion(uid)`，不一致 → 401），顶替 platform 默认 `acceptAll()`；`LocalCredentialService.changePassword` 改密成功后 `bump` 该用户令牌版本（旧会话全失效）。
  - 新增 `auth/ProfileController`（`GET /api/v1/me`、`POST /api/v1/me/password`）。
  - `AppRegistrationController` 全端点加 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")`；`IamAuthProperties` 增 `bootstrap` / 原 `web` 配置；`LocalCredentialService` 增 `registerLocalUser` / `changePassword`。
  - `pom.xml`：`maven-compiler-plugin` 显式 `<version>3.13.0</version>` + `<compilerArgs>` 强制 Lombok 处理器（见关键修复①）。
- **验证**：iam-ap `36/36` 绿。`AdminSecurityTest`×4 覆盖「无令牌 401 / `iam-ap:ADMIN` 200 / 非管理员 403」三态；`BootstrapAdminTest`×1 端到端验证「引导创建 admin（含 `iam-ap:ADMIN`）→ 用引导口令登录成功拿令牌」；`LocalCredentialServiceChangePasswordTest`×2 覆盖「正确旧 clientHash 改密成功且新口令可验/旧口令失效」「错误旧 clientHash 被拒」（改密接口已切到 clientHash，与登录一致）；`LocalTokenVersionCheckerTest`（版本比对单元）+ `ChangePasswordInvalidatesSessionTest`（端到端：改密前 `/me` 200 → 改密 → 旧令牌 `/me` 401 → 新口令重登 → 新令牌 `/me` 200）覆盖「改密强制作废旧会话」。
- **关键修复 / 设计决策**：
  1. **Lombok SPI 非发现（编译层，阻塞级）**：沙箱 JDK21 + `maven-compiler-plugin` 下，Lombok 经 `annotationProcessorPaths` 进 `-processorpath` 后，javac **未通过 SPI(META-INF/services) 自动发现**该处理器，导致 `@Getter/@Setter/@Slf4j` 静默失效、满屏「找不到符号」。根因非版本问题（pin 3.13.0 未解）。**修复**：在 `maven-compiler-plugin` 显式 `<compilerArgs><arg>-processor</arg><arg>lombok.launch.AnnotationProcessorHider$AnnotationProcessor</arg></compilerArgs>` 强制触发。BUILD SUCCESS。
  2. **`IamJwksKeyProvider` 继承关系**：`JwksKeyProvider`（cim-auth-starter）是**具体类**非接口，原 `implements` 编译报错 → 改为 `extends`，`super(() -> "{\"keys\":[]}", Duration.ofHours(1))` 占位、实际公钥由 `getPublicKey` 读 `RsaKeyService` 内存公钥返回。
  3. **首管理员引导模型**：`bootstrap.admin-password` 为空 → 生成随机 16 位口令并打印日志（仅 dev）；`enabled=false` 禁用；重复启动按 `admin-username` 存在性跳过。引导创建「注册 `iam-ap` 应用 → 两层派生建本地凭证 → 赋 `iam-ap:ADMIN`」三步。
  4. **CORS**：独立 `CorsFilter`（`@Order(HIGHEST_PRECEDENCE)`）先于安全链放行前端开发源，使 `business/iam-ap/web`(5171) 可跨域调用。
  5. **令牌版本失效的验证侧落地**：platform 的 `TokenVersionChecker` 默认 `acceptAll()`（不校验），故「改密/踢人」在 IAM 自身并不生效。新增 `LocalTokenVersionChecker`（进程内直查库，无 HTTP/TTL 滞后）经 `@ConditionalOnMissingBean` 顶替，使 `JwtAuthenticationFilter` 的「版本失效判定 → 401」在本服务内真正生效。`ver` claim 由 `TokenIssuerService` 写入、`JwtVerifier` 解析为 `TokenClaims.version()`。
- **风险 / 待办**：
  - ✅ `/me/password` 已改为接收客户端第一层派生的 `clientHash`（`oldCredential`/`newCredential`）+ 新随机盐 `newClientSalt`，明文口令不出浏览器，与登录对齐；前端 `ProfilePage` 同步改为先取盐派生后提交。
  - ✅ 改密后强制旧会话失效已落地：`changePassword` 入库后 `bump` 令牌版本 + IAM 注册 `LocalTokenVersionChecker` 强制校验；前端改密成功即清态跳登录。
  - AD 真实联调、刷新/锁定 TTL 灰度与监控仍待补。

### 7. IAM 前端控制台（web 新建）
- **范围**：`business/iam-ap/web`（新建，React18 + TS5.6 + Vite5）。核心文件：`src/lib/crypto.ts`（第一层 PBKDF2 派生，与 server `PasswordDerivation` 对齐）、`src/lib/api.ts`（axios 拦截器解包 `Result<T>` + 401 清态跳登录 + `postRaw` 透传裸对象）、`src/store/authStore.ts`（zustand persist localStorage）、`src/pages/{LoginPage,AppMgmtPage,TokenVersionPage,ProfilePage,DashboardPage}.tsx`、`src/components/{ProtectedRoute,Layout}.tsx`。
- **验证**：`tsc -p tsconfig.json` 类型检查 EXIT=0；`vite build` 产出 `dist/`（113 模块，EXIT=0）。`package-lock.json` 入库，`node_modules/`、`dist/` 由根 `.gitignore` 忽略。
- **关键修复**：
  - TS 工程配置：删除冲突的 `tsconfig.node.json`（`composite:true`+`noEmit:true` → TS6310），`tsconfig.json` 去 `references`，`build` 改为 `tsc -p tsconfig.json && vite build`。
  - TS5.7 `Uint8Array<ArrayBufferLike>` 与 `BufferSource` 不兼容 → `crypto.ts` 的 `utf8Bytes` 返回值显式标注 `Uint8Array<ArrayBuffer>`。
  - npm 缓存损坏 → `npm install --ignore-scripts --cache "E:/Software/tmp/npm-iam-cache" --prefer-online` 重建。
- **范围说明**：仅承载「登录门户 + 准入/粗角色管理 + 令牌踢人 + 自助改密」；业务菜单/按钮管理仍由各业务 ap 前端负责（与 IAM 边界一致）。

### 8. IAM 前端 UI 重构（工业级控制台 v2）
- **动因**：初版为「顶部导航 + 单列卡片」的扁平布局，信息密度低、缺乏工程感，不符合半导体 / CIM 场景对表格密度与编码可读性的要求。
- **范围**：`business/iam-ap/web/src`（`styles.css` 全量重写 + `Layout.tsx` 重写 + 页面重排 + 新增 3 文件）。
- **界面外壳**：改为标准四段式 —— **顶部 header**（模块标题 + 面包屑路径 + 环境标识 + 主题切换 + 用户菜单）、**左侧可收缩菜单**（232px ⇄ 64px，收缩态持久化于 `localStorage`；`≤900px` 转抽屉式）、**内容区**、**底部 footer**。
- **设计体系（v2）**：
  - **等宽字体承载编码类信息**：接入码、用户 ID、租户、角色组、算法名、版本号等统一走 `--font-mono` 栈；UI 正文走无衬线栈。
  - **数字对齐**：全局 `font-variant-numeric: tabular-nums`；数据表数字列右对齐。
  - **小号大写字母标签**：表头 / 字段标签 / KPI 标签 `uppercase + letter-spacing`。
  - **信息密度**：正文 13px，表行高 8px 内边距，卡片留白压缩（一屏信息量显著提升）。
  - **浅深双主题**：全部走 CSS 变量令牌，`<html data-theme>` 驱动；`index.html` 内联脚本首屏抢先应用，**消除主题闪烁**。
  - **组件化**：新增 `components/Panel.tsx`（统一面板标题栏 + 内容区）、`components/Icons.tsx`（内联 SVG 图标集，**零图标库依赖**）、`lib/theme.ts`（主题读写）。
- **登录页**：保留品牌分栏版式并精修交互 —— 记住用户名（仅存用户名，**口令从不落盘**）、口令可见性切换、Caps Lock 提示、回车提交、按是否记住用户名自动聚焦、失败自动聚焦并全选口令框。
- **验证**：`tsc -p tsconfig.json` EXIT=0；`vite build` 116 模块、CSS 19.8 kB / gzip 4.9 kB、JS 252 kB / gzip 84 kB，EXIT=0。另用无头 Chrome（CDP pipe）实拍浅色/深色/收缩态/移动端 9 张界面截图做视觉回归，发现并修复 2 处渲染缺陷（原生复选框继承全局 `input` 样式被撑成实心块 → 改为 `appearance:none` 自绘；行内表单字段无上限拉伸 → 加 `max-width`）。截图与临时预览工程均未入库。
- **范围说明**：仅改前端表现层与交互，**未改动任何后端契约与 API 调用**（`/api/v1/*` 路径、请求体、`Result<T>` 解包逻辑保持原样）。

### 9. IAM 功能全景梳理与管理控制台（IA v3 + 审计/用户/会话后端）
- **动因**：菜单为硬编码 4 项（概览 / 应用与准入 / 令牌踢人 / 我的），功能面不完整 —— 无「用户账号管理」「审计日志」「在线会话」「登录锁定」等身份域必备能力；信息架构缺少分组与权限可见性。
- **设计（用户使用习惯导向）**：功能域 = 概览 → 身份管理（用户账号、登录锁定）→ 接入管理（应用注册、准入授权、角色组）→ 安全与会话（在线会话、审计日志、系统设置）→ 我的。核心流程：入职（建号 → 授权 → 通知登录）/ 停用离职（禁用即强制下线 → 撤销准入）/ 排障（审计定位 → 解锁 / 强制下线）/ 自助（改密）。
- **后端范围（`business/iam-ap/server`）**：
  - 新增 `audit` 包：`AuditEvent`（`audit_event` 表，V5 迁移 h2+mysql）+ `AuditEventRepository` + `AuditService` + `AuditType`（16 种受控类型）+ `AuditEventDto`。**关键决策**：`AuditService.record` 用 `Propagation.REQUIRES_NEW` —— 审计埋点常位于「业务即将抛异常」的路径（登录失败、锁定拒绝、参数校验失败），若与业务同事务，业务回滚会把审计一并回滚，导致「失败事件查不到」；独立事务保证审计先落地，且审计自身异常经内部 try/catch + 日志隔离，不影响业务。
  - 新增 `admin` 包 7 个控制器（全部类级 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")`）：概览 / 用户账号 / 在线会话 / 登录锁定 / 角色组 / 审计查询 / 系统设置（只读）；`AppRegistrationController` 增 `GET /apps/users/{userId}/assignments`。
  - 服务扩展：`AccountLockService.listActiveLocks/unlock/find`、`LocalCredentialService.listAll/findByUserId/setEnabled/resetPassword/deleteUser`、`RefreshTokenService.listActiveSessions`、`AppRegistrationService.roleAggregate/listAllAssignments/listAssignmentsForUser`。
  - 安全语义：**禁用 / 重置口令 / 删除账号 / 撤销准入 / 强制下线均 bump 令牌版本**，配合 `LocalTokenVersionChecker` 使存量令牌即时 401。
- **前端范围（`business/iam-ap/web`）**：
  - 菜单改为**注册表驱动**（`lib/menu.tsx`：分组 + `admin?` 可见性；`lib/permissions.ts`：`isIamAdmin()`；`components/RequireAdmin.tsx`：路由守卫），`Layout` 按权限过滤分组并显示 `ADMIN` 标识。
  - 新增 7 页：用户账号 / 登录锁定 / 准入授权 / 角色组 / 在线会话 / 审计日志 / 系统设置；概览按权限二分（管理员=平台态势 + 最近审计；普通用户=个人准入矩阵）；应用页拆分出「准入授权」；原「令牌踢人」并入「在线会话」（列表 + 按 uid 强制下线）。
  - 内容区 `.content` 去掉居中 `max-width` 上限（改为铺满 + 14px gutter），消除宽屏两侧留白。
- **验证**：iam-ap **41/41 绿**（新增 `AdminConsoleTest`×5：① 管理员可视用户清单并创建账号；② 禁用后该账号登录 403；③ 锁定清单可见且可解锁；④ 审计记录登录成功 + 概览/设置/角色组/会话端点可用；⑤ 非管理员访问管理台 403）。web `tsc -p tsconfig.json` EXIT=0；`vite build` 126 模块、CSS 20.76 kB / gzip 5.05 kB、JS 279 kB / gzip 91 kB，EXIT=0。
- **视觉回归**：以独立验证实例（后端 :8082 + 临时 vite :5172，代理指向该实例）种子化数据（3 应用 / 3 账号 / 5 条准入 / 若干审计事件与 1 次锁定），用无头 Chrome CDP 实拍浅色与深色全页截图；并以 CDP 量化检测**无横向溢出**（4 个页面 `document.scrollWidth === clientWidth`，概览 6 张 KPI 卡各 192px、末卡右边界 1446 ≤ 内容右边界 1460）。修复 2 处问题：系统设置页说明文案误用 Markdown `**` 字面量（改为 `<b>`）；用户列表操作列过窄致按钮竖排（列宽 240→300）。临时验证工程（`vite.visual.config.ts`）与截图、种子脚本**均未入库**。

### 10. IAM 身份目录与组织架构（Wave 0：用户创建 + 组织架构 + 同步到业务系统）
- **动因**：IAM 此前只有「登录 + 准入」，缺最基础的一环 —— **人在哪个组织、组织如何批量准入、业务系统如何拿到人员/组织数据**。设计稿 `docs/business/iam-ap/server/identity-directory.md` 的 4 项分叉经用户拍板后**同日落码**。
- **决策定稿（原 §9「待确认问题」全部关闭）**：
  1. **多归属存在** → `user_org` **保留一对多**（多能工 / 跨线支援），不简化为 `user_profile.org_id`；`is_primary` 仅作主属标记，不限制行数；多归属取**并集**。
  2. **本地/沙箱无 AD 可连** → 同步器**未配置即跳过**：`AdDirectorySyncProperties.isConfigured()` 为假时 `syncNow()` 返回 `SyncResult{skipped:true}`，**不连接、不报错、不阻塞启动**；`@EnableScheduling` 由 `@ConditionalOnProperty(cim.iam.directory.ad.enabled)` 门控（未启用连定时任务都不注册），**纯 IAM 自建照常可用**。
  3. **MES 侧编码** → 经查 `docs/business/mes-ap/` **仅有骨架 README、无既定编码规则** → `org_node.code` **即唯一编码载体**，未来 MES 直接复用，**不发明第二套**。
  4. **服务身份选型** → **方案 A 起步**（`X-Directory-Key` + `MessageDigest.isEqual` 常量时间比较）、**方案 B（OAuth2 `client_credentials` / NHI 服务令牌）作为后续替换**，**接口契约不变**（只换认证头）。
- **范围（`business/iam-ap/server`）**：
  - **迁移**：`db/migration/{h2,mysql}/V6__identity_directory.sql` —— 5 张新表：`org_node`（`parent_id` + **物化路径 `path`** `/rootId/.../selfId/` + `source` + `external_id`，索引 `uk(source,code)`/`idx(parent_id)`/`idx(path)`）、`user_profile`（`user_id` 与 `local_credential.user_id`/令牌 `uid` **同源**）、`user_org`（多归属，`uk(user_id,org_id)`）、`org_app_assignment`（`uk(org_id,app_code)`）、`directory_watermark`（`scope` `USER`/`ORG`，`uk(scope)`）。
  - **新增包**：`common`（`DataOrigin`）、`org`（`OrgNode`/`OrgNodeType`/`OrgStatus`/仓储/`OrgNodeService`/`UserOrg`/`OrgAppAssignment`/`OrgGrantService`）、`profile`（`UserProfile`/`UserStatus`/仓储/`ProfileService`）、`watermark`（`WatermarkScope`/`DirectoryWatermark`/仓储/Service）、`directory`（`DirectoryApiProperties`/`DirectoryApiKeyFilter`/`DirectoryDtos`/`DirectoryService`/`DirectoryController`/`AdDirectorySyncProperties`/`DirectorySyncConfig`/`AdDirectorySyncService`）。
  - **改动**：`app/EffectiveAccessResolver`（**新增，核心**）+ `TokenIssuerService` 改走它；`admin` 新增 `OrganizationAdminController`/`DirectoryAdminController`，`UserAdminController` 列表改为「本地凭证 ∪ 用户档案」并集并带来源/档案/组织列（新增档案字段与 `profileService.setStatus`），`AdminOverviewController`/`SettingsAdminController` 扩展；`auth/ProfileController` 的 `MeDto` 增档案摘要 + 组织归属；`audit/AuditType` 增 10 种类型。
- **关键设计（务必保持）**：
  - ⚠️ **同树混源隔离**：`org_node.source ∈ {AD_SYNCED, IAM_MANAGED}`；同步器**只写 `AD_SYNCED`**（`upsertAdSynced`），`guardManaged` 使对 AD 节点的本地改/删/移抛 `PARAM_INVALID`（测试断言用 `extracting("bizCode")`，因 `BizException.paramInvalid(msg)` 走 varargs 构造、`getMessage()` 返回默认文案）。
  - ⚠️ **准入解析「运行时展开、不落派生行」**：`effectiveApps/effectiveRoles(uid) = 个人授予 ∪ 组织授予`；祖先判定为 `userOrg.path.startsWith(grantedOrg.path)`（**`path` 是从根算起的绝对路径** —— 曾误用 `startsWith("/"+orgId+"/")` 导致祖先链不生效、`mds-ap` 缺失，已修）。
  - ⚠️ **失效闭环复用既有机制**：组织移动/删除、组织授予变更、归属变更、档案停用 → `TokenVersionService.bump` → 旧令牌经 `LocalTokenVersionChecker` 即时 401，**无新增失效机制**。
  - ⚠️ **目录 API 安全默认拒绝**：`DirectoryApiKeyFilter` 未配置密钥 → **503**（不是放行）、密钥不匹配 → 401、比较用**常量时间**比较。
  - **路径索引与跨源改写**：`move`/`upsertAdSynced` 改父节点时先存 `oldPath` 再按前缀重写整棵子树（两者实现保持一致）。
- **前端范围（`business/iam-ap/web`）**：IA v4 —— 身份管理改为「**组织架构 → 用户与档案 → 登录锁定**」（先建组织再挂人），接入管理新增「**组织授权**」；新增 `OrgPage`（组织树 + 节点详情 + CRUD/move + 挂人，AD 节点标只读）、`OrgGrantsPage`（组织授予清单 + 授予表单 + 受影响人数 + 与个人准入的分工说明）；`UsersPage` 重写（来源/档案/组织列 + 多面板 + 归属多选）、`DashboardPage`（8 KPI + 我的组织归属）、`ProfilePage`（档案 + 组织标签）、`SettingsPage`（目录 API 与 AD 同步面板 + 立即同步）；`types.ts`/`format.ts`/`menu.tsx`/`App.tsx`/`styles.css` 同步（新增 `.split`/`.tree*`/`.kv`/`.org-pick` 等）。
- **验证**：iam-ap **48/48 绿**（新增 `IdentityDirectoryTest`×7：① 组织授予经祖先链展开到成员；② 跨源保护使 AD 节点只读；③ 组织变更 bump 令牌版本 + 水位；④ 未配置 AD 时同步跳过；⑤ 目录 API 强制服务密钥；⑥ 批量用户与组织/档案管理端点；⑦ 组织授予变更使存量令牌失效）。web `tsc -p tsconfig.json` EXIT=0；`vite build` 128 模块、CSS 22.60 kB、JS 309.01 kB，EXIT=0。
- **视觉回归**：独立验证实例（后端 :8082 + 临时 vite :5172）种子化数据（3 应用 / 组织树 8 节点 / 5 账号含档案 / 多归属 / 3 条组织授予 / AD 同步返回 `skipped:true`），以 CDP 实拍**浅/深双主题共 11 页 + 登录页**；并量化检测**全部 11 页零横向溢出**（`scrollWidth === clientWidth`，越界元素计数 0）。**额外**对组织树做层级几何核验：`padding-left` 依次 8 / 25 / 42 / 59px（每级 +17px），`薄膜车间` 与 `蚀刻车间` **同为 25px（证实同级）**、`B厂` 回到 8px —— 量化纠正了此前对低分辨率截图的误读（曾误判 `薄膜车间` 缩进多一级）。

## 2026-10-09（续）IAM 控制台配置化（W4b：存量页面正文文案 i18n 迁移）

### 13.1 后端（server）
- **范围**：`business/iam-ap/server`
  - `IamConsoleCatalog.texts()` 补 12 个存量页命名空间双语 `TextDef`：`iam.users.*` / `iam.orgs.*` / `iam.dashboard.*` / `iam.settings.*` / `iam.orgGrants.*` / `iam.profile.*` / `iam.appMgmt.*` / `iam.sessions.*` / `iam.admissions.*` / `iam.lockouts.*` / `iam.audit.*` / `iam.rolesView.*`（注：`RolesPage` 用 `rolesView` 命名空间，区别于配置页 `rolesAdmin`）。
  - 枚举/相对时间标签全部落到 `TextDef`：`iam.audit.type.*`（24 个审计类型）、`iam.org.type.*`（6 种组织类型）、`iam.source.*`（AD_SYNCED/IAM_MANAGED）、`iam.common.{justNow,minutesAgo,hoursAgo,daysAgo,dash}`（含 `{{n}}` 插值）。
  - 新增 `ConsoleTextCatalogTest`（3 方法）：① 每个 `TextDef` 的 zh/en 均非空 + code 唯一；② `en` 文本不得含 CJK（否则 en-US 包混入中文）；③ W4b 12 个命名空间齐备（缺任一→对应页切 en-US 回落 `humanize(key)`）。
- **验证**：随 iam-ap 回归 **71/71** 绿（68 + 本测试 3 方法）。

### 13.2 前端（web）
- **范围**：`business/iam-ap/web`
  - `lib/format.ts`：移除 `AUDIT_LABELS`/`ORG_TYPE_LABELS` 常量对象，`auditLabel`/`orgTypeLabel`/`originLabel`/`fmtDateTime`/`fmtRelative` 全部改走 `i18next.t` 内部翻译（页面**不得**二次包裹其返回值，否则双重 key）；`AuditPage` 同步改引 `AUDIT_TYPES` 字符串数组（因 `format.ts` 不再导出 `AUDIT_LABELS`）。
  - 17 个存量页（UsersPage/OrgPage/DashboardPage/SettingsPage/OrgGrantsPage/ProfilePage/AppMgmtPage/SessionsPage/AdmissionsPage/LockoutsPage/AuditPage/RolesPage 等）UI 中文改 `t('iam.<page>.*')`；动态串用 `{{var}}` 插值；`window.confirm`/`flash` 提示一并包裹；少量子代理补的 key 统一落在 W4b 段（`iam.admissions.noRole`/`iam.audit.scopeSub`/`iam.settings.v.*`/`iam.orgGrants.{typeRef,localMaintain}`/`iam.profile.pwdUpdatedNotice`）。
  - `test/render-contract.tsx`：扩至 12 存量页，配 `MemoryRouter` 上下文 + `ALL_PERMS` 闸门（`getServerSnapshot` 改写）包裹，断言 `assertNoChinese`（正则 `/[一-鿿]/`）零残留；`run.mjs` 仍是 esbuild SSR 运行器。
- **验证**：`tsc -p tsconfig.json` EXIT=0；`vite build` 161 模块、JS 414.14 kB EXIT=0；**渲染契约 33/33**（4 配置页 + 12 存量页切 en-US 无中文残留、零裸 key）。
- **关键设计决策**：
  1. **翻译内聚于 `format.ts`**：枚举标签由 `format.ts` 内部 `i18n.t` 完成，调用方只消费返回值，避免「`{auditLabel(t)}` 外包 `t`」双重 key 缺陷（曾导致 `AuditPage` 编译报错，已修）。
  2. **目录单一事实源**：`TextDef` 是 zh/en 成对种子，前端 `t()` 运行时从平台 `/api/i18n/messages` 拉取；en 含 CJK 由 `ConsoleTextCatalogTest.englishTextContainsNoCjk` 兜底，从数据源根绝「en-US 包混中文」。
  3. **SSR 契约测试上下文修复**：`DashboardPage`/`ProfilePage` 用 `useNavigate` 需 `MemoryRouter` 包裹，否则 `renderToStaticMarkup` 抛 `useNavigate() may be used only in the context of a <Router>`；`useLayoutEffect` SSR 警告无害。
  4. **注释可保留中文**：仅渲染文本须 `t()`，代码注释不进 UI，不强制 i18n。

## 2026-10-09（续）IAM 控制台配置化（W4：菜单/权限/角色/多语言自助闭环）

### 11. 后端（server）
- **范围**：`business/iam-ap/server`
  - 新增 `console` 包：`IamConsoleCatalog`（菜单树 `MenuDef` + 界面文案 `TextDef` 种子；菜单实体只存 `i18n_code` 与图标注册表键 `icon`、`visible`/`permCode`/`type`，**不存名称与图标组件**）、`IamConsoleSeedRunner`（`ApplicationRunner` 首启种子）、`IamConsoleSeedService`（幂等 seed：菜单 + 文案 + 权限目录 + 角色授权）、`IamConsoleProperties`（开关与超时）。
  - 新增 `support/IamPermissionCodes`（细粒度 `iam:*` 权限码常量，取代旧两段式 `iam-ap:ADMIN`）。
  - 新增 `admin/I18nAdminController`（`/api/v1/admin/i18n/**`：语种目录 GET、译文分页 GET、缺失汇总 GET、保存/删除/缺失清除 PUT/DELETE）。
  - **双闸门鉴权重构**：`admin/*` 全部控制器类级 `@PreAuthorize("hasAuthority('iam:console:admin')")` 兜底 + 方法级 `@PreAuthorize("hasAuthority('iam:<域>:<动作>')")`；`IamSecurityConfig` javadoc 同步。
- **验证（已跑通）**：iam-ap **68/68** 绿。
  - `ConsolePermissionI18nTest`×9：`/sys/menus/tree`、`/sys/permissions/page`、`/sys/roles/{id}/permissions|menus` 对 admin 返回 200（钉死双闸门前提）；`IAM_OPERATOR`（非超管）实测持有 `iam:menu:list`+`sys:menu:list`+`sys:permission:list`+`sys:role:list`（种子补授平台只读码）。
  - `UserListProjectionTest`×9：覆盖「本地凭证 ∪ 用户档案」并集投影的 service/repository 两层；**实测钉死**「方法级 `@PreAuthorize` **覆盖**类级（非 AND）」——只持 `iam:user:list`、无 `iam:console:admin` 亦返回 200。
  - `ConsoleMenuIconContractTest`×2：跨端契约，直接读前端 `lib/iconRegistry.ts` 解析键集比对 `IamConsoleCatalog.menus()` 的 `icon`，断言种子图标全部命中注册表、注册表键不重复且解析非退化。
- **关键设计决策**：
  1. **双闸门（写操作必备）**：菜单/权限/角色三页数据面复用平台 `cim-system` 的 `sys:*` 端点，而 IAM 的 `iam:*` 仅控制**入口显隐**；二者同时满足才可读写 → `能读/写 ⟺ 持 IAM 控制台码 ∧ 持对应平台码`。前端用 `<Perms codes={[IAM码, SYS码]}>`（AND）+ 页级 `useHasAllPermissions([...])` 判读，缺平台码即整页提示、不发起注定 403 的请求。`/i18n` 页单闸门（数据面即 IAM 自己的端点）。
  2. **方法级覆盖类级（反直觉）**：Spring Security 的方法级 `@PreAuthorize` **覆盖**类级而非 AND 叠加；故「只持细码、无伞码」仍 200。已用测试钉死该语义，避免误以为「类级伞码提供兜底 AND」。
  3. **菜单不存名称/图标组件**：`sys_menu` 仅 `i18n_code` + `icon`（注册表键）；名称由 `t(i18n_code)`、副标题 `t(i18n_code+'.desc')`、图标由 `iconRegistry` 解析，未知回落 `IconAlert`。新增菜单后须去「多语言」页补译文。
  4. **seed 幂等**：`IamConsoleSeedService` 按 `i18n_code`/`code` 存在性 upsert，重复启动不重复插入。

### 12. 前端（web）
- **范围**：`business/iam-ap/web`
  - 四配置页：`MenusAdminPage`（树面板 + 编辑表单，含 `BUTTON` 必须绑 `perm_code`）、`PermissionsAdminPage`（按模块分组 + 三段校验 `module:res:action`）、`RolesAdminPage`（角色 CRUD + 两个独立授权面板：权限勾选 + 菜单树勾选）、`I18nAdminPage`（语种/译文/缺失汇总 + 单闸门）。
  - 基础设施：`components/Perms.tsx`（新增 `codes?: readonly string[]` 多码 AND 闸门）、`components/RequirePerm.tsx`、`lib/permCodes.ts`（`SYS` 镜像平台 `PermissionCodes`）、`lib/iconRegistry.ts`、`lib/usePermission.ts`、`lib/i18n.ts`（整包本地缓存 + `initImmediate:false` + 不用 Suspense + 中文兜底四层）、`components/BootGate.tsx`（权限就绪再渲染路由）。
  - 删除：`components/RequireAdmin.tsx`、`lib/menu.tsx`（硬编码菜单/守卫，由注册表驱动 + `Perms`/`RequirePerm` 取代）。
- **验证**：`tsc -p tsconfig.json` EXIT=0；`vite build` 全绿。**渲染契约 21/21**（`test/run.mjs` + `test/render-contract.tsx` + `test/setup.ts`）：用 `react-dom/server` 的 `renderToStaticMarkup` 在 Node 断言「给定权限码集时页面渲染哪些元素、请求哪些文案键」——**无需浏览器**；关键断言即双闸门（只持 `iam:menu:list` 缺 `sys:menu:list` → 仅无权限提示；两码齐备 → 渲染主体与写按钮），`/i18n` 反向断言单闸门。
- **关键设计决策**：
  1. **zustand SSR 快照坑**：`useStore` 的 `getServerSnapshot` 默认读初始快照，`setState` 对 SSR 不可见 → 测试须就地改写 `getServerState`。已固化在 `test/setup.ts` 注释。
  2. **esbuild 产物 CJS 动态 require 坑**：`react-dom` 等运行期 `require('stream')` 撞 esbuild「Dynamic require not supported」→ 在 `run.mjs` 顶部用 `createRequire` 注入真实 `require`。
  3. **i18next 插值 `{{n}}`**（非 `{n}`）：节点计数/缺失计数用 `{{n}}`，修复「显示为字面量」缺陷。

### 13. 文档与验证
- `docs/business/iam-ap/web/README.md` §3.1（配置化闭环 + 双闸门表 + 实现口径）、§3.2（四层验证与渲染契约测试设计）。
- `docs/business/iam-ap/server/console-menu-perm-i18n.md` §7.4（双闸门数据面/入口闸门表）、§9（权限族/双闸门风险行）、§11（风险表新增双闸门行 + 图标契约测试已补）。
- `docs/business/iam-ap/server/README.md`/`identity-directory.md`：把 `iam-ap:ADMIN` 旧称统一改为 `iam:console:admin` + 方法级 `iam:xxx:yyy`，并澄清 `iam-ap:ADMIN` 仅作准入角色 claim、非控制台权限。
- 验证产物（`.verify/` 脚本与截图、`vite.smoke.config.ts`）**未入库**，已清理。

### 14. 风险 / 待办
- ~~**W4b 存量页面 i18n 迁移**：4 个配置页已 i18n，但 17 个存量页面（OrgPage/UsersPage/Dashboard/Settings 等）约 537 行硬编码中文，**未纳入本次**，列为后续 wave。~~ **已于 2026-10-10 完成**，见下方「2026-10-10 W4b」章节。
- ~~平台 `cim-system` 迁移版本号 V1→V1000（ADR-10）等早年会话产物的未提交改动仍在工作区。~~ **已于 2026-10-10 收口**：`cim-i18n-starter`(ca95b8d) + `cim-system`(6a46448) + `cim-jpa-starter`/`cim-bootstrap`(ffea12a) 三笔 commit 入库，构建验证全绿；工作区已无 platform 底座未提交改动。（注：`cim-mq-starter`/`cim-cache-starter`/MDS 文档此前已随各自 commit 入库，本行原"未提交"表述系当时误判。）

## 2026-10-09 全量回归记录
- **范围**：整仓（platform 13 模块 + iam-ap/server）。
- **命令**：`/tmp/mvnx.sh install`（platform）→ `/tmp/mvnx2.sh <iam-ap/server> test`。
- **结果**：
  - platform：`BUILD SUCCESS`，13/13 模块全绿（含 `cim-mq-starter` / `cim-cache-starter` / `cim-system` / `cim-bootstrap` 等全部子模块）。
  - iam-ap：回归当时 `29/29` 测试全绿，`BUILD SUCCESS`（其后管理面 +4 → 29/29，改密 clientHash +2 → 31/31，改密强制作废旧会话 +3 → 36/36，管理控制台 +5 → 41/41，身份目录 Wave 0 +7 → **当前 48/48**）。
- **结论**：前期会话落地的 mq / cache / platform 三项经整仓回归确认无编译/测试漂移，原「待回归」标记全部解除。

## 2026-10-10 platform 底座收口（cim-i18n-starter / cim-system / cim-jpa-starter / cim-bootstrap）

> 把此前漏提交（PROCESS 表标记"已提交"实际未入库）的 platform 底座落地，按模块拆三笔 commit 收口；构建验证全绿后再入库。

### 15. 平台多语言基础设施（cim-i18n-starter，全新模块）
- **范围**：`platform/server/cim-i18n-starter/**`（33 文件，含 `META-INF/spring/...AutoConfiguration.imports` 自动装配）。
- **内容**：Spring `MessageSource` 由 DB 驱动（`DatabaseMessageSource` + `DbMessageResolver` 顶替默认）；`I18nService#frontendBundle` 按「内置默认 → 内置目标 → DB 默认 → DB 目标」**整包合并兜底**（修复未翻译语言拿到空包 → 界面空白/裸 key）；`I18nSeedLoader` 幂等种子（不覆盖在线维护）；`I18nController`/`LocaleResolverCim`；迁移 `db/migration/i18n/{vendor}/V2000__init_i18n_tables.sql`（ADR-10 版本段 V2000–V2999）；内置 `builtin_zh-CN/en-US.properties`；`I18nIntegrationTest` 绿。
- **关键约束**：⚠️ ADR-12 本模块**不声明** `@FilterDef`（唯一声明点在 `com.cim.system`），否则与系统域模块同名重复声明在 Hibernate 6.2+ 为硬错误（HHH-16581/16803）。
- **验证**：随平台受影响模块 `mvn install` 全绿（SUCCESS）。

### 16. cim-system 域模块（扫描根 / Flyway / @FilterDef / 种子器）
- **范围**：`platform/server/cim-system/**`（12 文件，含 V1→V1000 迁移重命名 `git rename`）。
- **内容**：
  - 扫描根由 `com.cim` 收窄为 `com.cim.system`（ADR-11：避免与 `cim-i18n`(`com.cim.i18n`) 重叠 → Spring Data 仓储注册器不重名去重 → `BeanDefinitionOverrideException`）。
  - `@FilterDef(cimTenantFilter)` **唯一声明点**落在 `com.cim.system.package-info`（ADR-12）。
  - Flyway 迁移 `V1__init_system_tables` → `V1000__init_system_tables`（ADR-10 版本段 V1000–V1999，5 种库产品 dm/h2/mysql/oracle/postgresql）。
  - 新增 `SysPermissionSeeder`（幂等写入 21 条平台权限码，是超管展开为「全库启用码」的基础；前端精确匹配、勿把 `SUPER_ADMIN` 当 `*`）。
  - `CimSystemConfiguration`/`CimSystemProperties` 声明 `EntityScan`/`EnableJpaRepositories` 仅本模块包。
- **验证**：`mvn install` 中 cim-system SUCCESS；`BootstrapAssemblyTest` 实测 `[cim-system] 权限目录种子：写入 21 条平台权限`、`Flyway 迁至 v1000`。

### 17. cim-jpa-starter + cim-bootstrap 装配层
- **范围**：`platform/server/cim-jpa-starter/**`（`TenantFilterApplier` + `db/migration/README.md` 版本段分配表）+ `platform/server/cim-bootstrap/**`（`CimApApplication` + `BootstrapAssemblyTest`）。
- **内容**：cim-jpa-starter 对齐 ADR-12（`TenantFilterApplier` 注释）/ ADR-10（`db/migration/README.md` 新增版本段分配表）；cim-bootstrap 启动类收敛为唯一启动入口，`BootstrapAssemblyTest` 5/5 绿。
- **验证**：平台受影响模块 `mvn install` 全绿（cim-jpa-starter/cim-bootstrap 均 SUCCESS）。

### 18. 文档与验证
- `docs/platform/server/design.md`：补 ADR-10（Flyway 版本段分配）、ADR-11（扫描根互不重叠）、ADR-12（`@FilterDef` 唯一声明点）落地口径与教训；cim-i18n-starter 目录结构、四级兜底链、`frontendBundle` 整包合并说明。
- `PROCESS.md` 里程碑「platform 底座(server)」行：范围补 `cim-i18n-starter`，验证列更新为 2026-10-10 平台模块 `mvn install` 全绿，提交列补三笔 commit 哈希。

### 19. 风险 / 待办
- **遗留（文档）**：`docs/business/mds-ap/server/*` 约 30+ 篇设计稿仍写「`@FilterDef` 放实体包 `package-info.java`」，与 ADR-12（唯一声明点 = `com.cim.system`）相悖；落地 mds-ap 前须按新口径改写，否则启动即 `AnnotationException: Multiple '@FilterDef'`。
- **验证范围**：本次仅对 platform 受影响模块做 `mvn install`（未跑全仓 13 模块整回归），但 cim-i18n-starter 为新建模块、cim-system 等此前已整仓回归；IAM 侧未触碰，不受影响。

## 2026-10-10 W4b 存量页面 i18n 迁移 + SysPermissionSeeder 运行顺序修复

### 20. W4b 后端：文案目录双语种子 + 数据质量门禁
- **范围**：`business/iam-ap/server`
  - `IamConsoleCatalog.texts()` 补 12 个存量页命名空间双语 `TextDef`：`iam.users/orgs/dashboard/settings/orgGrants/profile/appMgmt/sessions/admissions/lockouts/audit/rolesView.*`（注意：`RolesPage` 用 `rolesView`，区别于配置页 `rolesAdmin`）；枚举/相对时间标签全落 `TextDef`：`iam.audit.type.*`（24 审计类型）、`iam.org.type.*`、`iam.source.*`、`iam.common.{justNow,minutesAgo,hoursAgo,daysAgo,dash}`（`{{n}}` 插值）。
  - 新增 `ConsoleTextCatalogTest`×3：① zh/en 成对非空 + code 唯一；② en 文本不得含 CJK；③ W4b 12 命名空间齐备（缺任一 → 对应页切 en-US 回落 `humanize(key)`）。
- **验证**：随 iam-ap 回归 **71/71 绿**（68 + 本测试 3）。测试开发期曾抓到 1 处目录缺陷：`iam.dashboard.th.type` 被定义两次（审计表与组织表各一），去重修复——门禁测试立功。

### 21. W4b 前端：17 存量页 UI 文案迁移
- **范围**：`business/iam-ap/web`
  - `lib/format.ts`：删除 `AUDIT_LABELS`/`ORG_TYPE_LABELS` 常量，`auditLabel`/`orgTypeLabel`/`originLabel`/`fmtDateTime`/`fmtRelative` 改走 `i18next.t` **内聚翻译**（调用方不得二次包裹，否则双重 key）；`AuditPage` 同步改引 `AUDIT_TYPES` 数组。
  - 12 个页面文件（UsersPage/OrgPage/DashboardPage/SettingsPage/OrgGrantsPage/ProfilePage/AppMgmtPage/SessionsPage/AdmissionsPage/LockoutsPage/AuditPage/RolesPage）：UI 中文改 `t('iam.<page>.*')`、动态串 `{{var}}` 插值、`window.confirm`/`flash` 一并包裹；代码注释不渲染、保留中文不强制。
  - `test/render-contract.tsx`：扩至 12 存量页 + `assertNoChinese`（`/[一-鿿]/`），配 `MemoryRouter`（`useNavigate` SSR 需要）+ `ALL_PERMS` 闸门。
- **验证**：`tsc` EXIT=0；`vite build` 161 模块 / JS 414.14 kB EXIT=0；**渲染契约 33/33**（4 配置页 + 12 存量页切 en-US 零中文残留、零裸 key）。

### 22. fix(platform)：SysPermissionSeeder 显式 @Order（顺带修复一处既有红测）
- **动因（W4b 验证期发现）**：iam-ap 全量回归 71 测中有 1 失败 —— `ConsolePermissionI18nTest` 断言 `IAM_OPERATOR` 应持有 `sys:menu:list/sys:permission:list/sys:role:list`，实测缺失。**git stash 对照实验证实该失败在 W4 提交态（改动 stash 后）即复现，属既有缺陷、非 W4b 引入**（W4 当时「68/68 绿」的记录与实际不符，应为验证遗漏）。
- **根因**：`seedGrants` 对库里查不到的权限码**静默跳过**且事后不补授（角色「尚无任何授权」才写授权）；`IAM_OPERATOR` 的 `sys:*` 码依赖平台 `SysPermissionSeeder` 先落库。而该 seeder **无 `@Order`**（缺省 = `LOWEST_PRECEDENCE`），与 `IamConsoleSeedRunner`（同为 `LOWEST_PRECEDENCE`）**排序不确定**——实际按 bean 名字母序 `iamConsoleSeedRunner` 先跑 → 授予时 `sys:*` 尚不存在 → 角色永久缺失平台码。生产影响：操作员角色看不到菜单/权限/角色页面。
- **修复**：`SysPermissionSeeder` 加 `@Order(Ordered.LOWEST_PRECEDENCE - 1000)`——仍属「启动晚期」但在一切缺省顺序的 ap 种子**之前**；javadoc 写明理由，防止将来被「顺手去掉」。
- **验证**：`mvn install -pl cim-system -am -o` 全绿（含 cim-auth-starter 27 测 + cim-system 16 测）后重装本地仓库，iam-ap 全量 **71/71 绿**，`ConsolePermissionI18nTest` 恢复通过。

## 2026-10-12（续）统一登录到 8081 后端单一登录页（方案 C 收口）

### 23. feat(auth)：IAM 控制台与 RMS 共用 8081 后端单一登录页
- **范围**：
  - `business/iam-ap/server`：`sso/SsoLoginPageController` 重写登录页 HTML —— 双栏版式（左品牌/主视觉、右表单，与 5171 控制台登录页同源设计令牌）、内联 IAM 设计令牌（浅/深随系统）、通过公开端点 `GET /api/i18n/messages?lang=` 运行时拉取 `iam.login.*` 文案（含 zh/en 切换），拉取失败回退内置中英词典；保留 salt→PBKDF2-HMAC-SHA256 第一层派生→`POST /api/v1/login`→整页回跳 `redirect?token=&refreshToken=` 契约。
  - `business/iam-ap/web`：`App.tsx` 的 `/login` 改为整页中继到 IAM 后端（8081）`/login?app=IAM&redirect=<origin>/sso-callback`（已登录且有外部回跳参数则直接带 token 弹回）；新增 `/sso-callback` 路由由 `pages/SsoCallback.tsx` 收口（写入 authStore 跳首页）；删除旧 `pages/LoginPage.tsx`（控制台内不再渲染登录表单）。
  - `business/rms-ap/web`：`pages/LoginPage.tsx` 中继目标统一指向 8081 后端登录页（`VITE_IAM_LOGIN_BASE`/`VITE_IAM_BASE` 可覆盖），`RMS` app 码 + 回跳 `<origin>/sso`，`/sso` 由既有 `SsoCallback` 收口。
- **验证（已跑通）**：
  - iam-ap 后端：`71/71` 测试全绿（含 `IamApIntegrationTest` 等），`mvni.sh -o test` BUILD SUCCESS。
  - iam-ap web：`tsc -p tsconfig.json` EXIT=0 + `vite build` 165 模块 / JS 428.12 kB / gzip 123.31 kB，built in 3.31s。
  - rms-ap web：`tsc` EXIT=0 + `vite build` 146 模块 / JS 330.94 kB / gzip 103.42 kB，built in 3.33s。
- **关键设计决策 / 核查**：
  - **文案契约已闭环**：登录页拉取的 `GET /api/i18n/messages` 属 `cim-i18n-starter` 的 `I18nController`（无 `@PreAuthorize`，而安全链 `authorizeHttpRequests(a -> a.anyRequest().permitAll())` 放行所有请求、细粒度由方法级 `@PreAuthorize` 控制）→ 该端点对匿名请求公开；返回 `Result<I18nBundle>`，`I18nBundle.messages` 即 `Map<String,String>`，与登录页 JS 读取的 `j.data.messages` 一致。故「登录页文案随控制台 i18n 实时同步」非仅文档宣称、而是真实生效（FALLBACK 仅作 i18n 服务不可达时的兜底）。`iam.login.*` 键由 `IamConsoleCatalog.texts()` 在启动种子写入 `sys_i18n`，经 `frontendBundle` 合并兜底后出现在 bundle 中。
  - **消除「两套登录」**：既有实现为 8081 后端直出页（方案 C）与 5171 控制台 React 登录页并存；本次把控制台 `/login` 也改为中继到 8081 同一页，所有客户端（IAM 控制台、RMS、未来 MDS/MES）落点同一登录页，视觉/文案单一真相源。
  - **循环跳转根除**：`rms-ap` `BootGate` 在 boot 失败（典型 403 无本 ap 准入）时清态并带 `?error=admission` 回 `/login`；登录页见 `error` 即停自动中继，避免 IAM 侧会话仍在导致两端登录页无限往返（同见 `99d2631` 的循环保护）。
- **风险 / 待办**：
  - IAM(8081)+RMS(8084) 实际联调 SSO 登录→回跳→准入已于本会话前序完成（10/10 通过）；**RMS 真实 RBAC 替换临时 Loader 见 §24**（E2E 8/8）。
  - 登录页 `crypto.subtle` 要求安全上下文：localhost 视为安全上下文可用，生产须 HTTPS。

## 2026-10-12（续2）RMS 接入 cim-system 真实 RBAC（替换临时 RmsAuthorityLoader）

### 24. feat(rms)：RMS 业务权限由「令牌 roles 短路」转「本 ap 库内 sys_* RBAC」
- **范围**：
  - `business/rms-ap/server/pom.xml`：新增 `cim-system` 依赖（与 iam-ap 同款）。
  - `RmsApApplication`：`scanBasePackages={"com.cim.rms.server","com.cim.system"}`；保留本包 `@EntityScan`/`@EnableJpaRepositories`，cim-system 自带扫描根 `com.cim.system`（互不重叠，符合 ADR-11）。
  - `security/RmsAuthorityLoader.java`：**删除**（W2 临时占位）；由 `cim-system` 的 `DbLocalAuthorityLoader` 经 `@ConditionalOnMissingBean` 自动顶替默认 `ClaimLocalAuthorityLoader`。
  - 新增 `console/` 包（镜像 iam-ap `IamConsoleCatalog`/`IamConsoleSeedService`/`IamConsoleSeedRunner`/`IamConsoleProperties`）：`RmsConsoleCatalog`（18 个 `rms:*` 权限码 + 3 角色 RMS_ADMIN/RMS_OPERATOR/RMS_VIEWER + 角色→权限授予）、`RmsConsoleSeedService`（幂等：权限→角色→授权→管理员档案 `externalId=username="admin"`）、`RmsConsoleSeedRunner`（`@Order(LOWEST_PRECEDENCE)`，晚于平台 SysPermissionSeeder）、`RmsConsoleProperties`（`cim.rms.console.*`）。
  - `security/MeController`：注释更新（权限源改为 DbLocalAuthorityLoader 查库）。
  - `application.yml.example`：补 `cim.system.enabled/seed-permissions: true`（默认即 true，显式消歧义；`application.yml` 本身被 .gitignore，仅模板入库）。
  - 前端：`lib/permCodes.ts` 澄清 RMS_ADMIN 是角色码非权限码；`lib/permissions.ts` 导出 `SUPER_ADMIN` 信号；`components/Layout.tsx` 的 `admin` 判定由失效的 `rms:admin` 占位改为 `permissions.includes('SUPER_ADMIN')`（真实 RBAC 下超管角色会被展开为含 SUPER_ADMIN）。
- **验证（已跑通）**：
  - rms-ap 后端：`15/15` 测试全绿（含 cim-system 装配、Flyway V1/V2+V1000(system)+V2000(i18n) 迁移、种子器），`mvnrms.sh -o test` BUILD SUCCESS。
  - rms-ap web：`tsc -p` + `vite build` 145 模块 / JS 330.92 kB / gzip 103.40 kB。
  - 实时 E2E（IAM 8081 + RMS 8084 双后端，复刻浏览器第一层 PBKDF2 登录）：`e2e-rbac.mjs` **8/8 通过** —— 登录拿令牌→解码 JWT 确认 `roles` 含 `rms-ap:ADMIN`、**`authorities` claim 不含任何 `rms:*`（证明非 IAM 注入）**；`GET /api/v1/rms/permissions`（Bearer）返回 18 个 `rms:*`（全部来自本 ap `sys_role_perm` 库内解析）+ 21 个平台 `sys:*` + `SUPER_ADMIN`（共 40，即 DbLocalAuthorityLoader 超管短路「库内全部启用权限码」语义，完全符合 SysPermissionSeeder 设计）；无令牌→200 空集（MeController 无方法级鉴权，与 /me 同源）。
- **关键设计决策 / 核查**：
  - **真实 RBAC 已生效的铁证**：令牌 `authorities` 仅含 `iam-ap:ADMIN`/`rms-ap:ADMIN`（IAM 签发时 roles 与 authorities 同为 `appCode:ROLE` 限定形态），但 RMS `/permissions` 返回 18 个 `rms:*`。二者矛盾只能解释为 RMS 在本地查 `sys_user→sys_role→sys_role_perm` 解析出权限，而非读令牌——正是「业务权限各 ap 自管」落地的接缝。
  - **管理员对齐**：`RmsConsoleSeedService.seedAdminProfile` 建档 `sys_user(externalId=username="admin")`，与 IAM bootstrap 首管理员同名；`DbLocalAuthorityLoader`→`LocalUserResolver` 按 `username="admin"` 命中（令牌 `uname=admin`），故登录即获 RMS_ADMIN 超管角色。
  - **权限码单一真相源**：`RmsConsoleCatalog` 复用 `RmsPermissions` 常量，前后端 `permCodes.ts` 镜像，避免「按钮可见但接口 403」。
- **风险 / 待办**：
  - 当前仅种子管理员（RMS_ADMIN 超管）；RMS_OPERATOR/RMS_VIEWER 角色已定义并授予对应 `rms:*`，但尚无「在 IAM 侧为非管理员用户授予 rms-ap 准入 + 在 RMS 侧将其 `sys_user` 关联到 RMS_OPERATOR/VIEWER」的运行时开通链路（属 W3 多角色开通，待 IAM 用户/组织授权与 RMS 用户建档打通后联调）。
  - 菜单树（`sys_menu`）未种子：RMS 前端菜单为硬编码 + permCodes 门禁，本轮未引入后端菜单树；若后续采用后端驱动菜单，须补 `sys_menu` 种子与 `sys_role_menu` 授权。

## 2026-10-15 approval-ap 全量落地 + IAM SSO + RMS W3–W11

> 2026-10-12 之后至 2026-11-11 的 61 个提交此前未登记（唯一空档）。本节起补记，证据取自当日工作日志与提交内容，不补造未跑过的验证。

### 25. feat(approval/iam/rms)：审批流平台建成 + 跨 ap 单点登录 + RMS 业务全量功能

- **范围**：
  - `business/approval-ap/**`（新建）：Flowable 引擎集成、流程设计器（拖拽连线 + BPMN 双向转换）、前端 8 页（`Dashboard`/`Definitions`/`Todo`/`Done`/`Initiated`/`Callbacks`/`Audit`/`Designer`）、`sdk/`（`ApprovalClient` + `CallbackVerifier` + 12 个 DTO record）。
  - `business/iam-ap`：方案 C 跨 ap SSO（会话 Cookie 共享）`dff840b`。
  - `business/rms-ap`：W3–W11 全量功能（配方治理 / 机台授权 / 签核 / 导入导出 / 实验通道）`fc9ee5e`。
  - 两处 `React.StrictMode` 移除（approval 17:40、rms 17:45）`8b7ef1a`。
- **验证（已跑通）**：
  - `ApprovalFlowIntegrationTest` + `ApprovalApApplicationTests` **8 用例全绿**（会签 / 回退 / 加签 / 改派 / 回调 HMAC+重试+幂等 / 审计哈希链 / 多租户）。
  - 8085 HTTP 冒烟：`definitions → start → todo → complete → audit` 端到端通过，审计 `prevHash → hash` 衔接正确、完成后待办清空。
  - 设计器 E2E 闭环：`gen-sample-bpmn.mjs` 用**真实 `toBpmn.ts`** 生成覆盖全节点类型的 `richApproval` → `POST /processes/deploy` 200 → `/definitions` 出现 → `start(amount=2000)` → alice 待办「主管审批」正确；`npm run build` 150 模块。
  - `verify-hardening.mjs` **15/15**（统一返回契约 + 撤回/驳回策略）、`journey` **13/13**。
  - 菜单切换双发修复后无头浏览器量化：`/todo`、`/initiated` 各**仅 1 次**请求。
- **关键修复 / 设计决策**：
  - `completeTask` 必须在 `taskService.complete` **之前**读 `tenantId` 流程变量——流程结束后 execution 被删，再 `getVariable` 抛 `FlowableObjectNotFoundException`。
  - **安全链拆两个互斥配置**：原 `ApprovalSecurityConfig` 挂 `@ConditionalOnProperty(enabled=true)`，`enabled=false` 时**不加载任何链** → Spring 回退默认链要求认证 → 全部 `/api/**` 401（集成测试直调 service 层绕过 HTTP 安全，才一直没暴露）。改为认证链 + `ApprovalDevSecurityConfig(matchIfMissing=true)` 放行链。
  - BPMN schema 两条硬约束：`multiInstanceLoopCharacteristics` 必须是 `userTask` **直接子元素**（放 `extensionElements` 内 → SAXParseException 部署 500）；`condition`/`completionCondition` 表达式必须 `esc()` 转义 `<`/`>`。
  - 设计器 SVG：presentation attribute **不支持 `var()`**（`stroke="var(--accent)"` 静默变 `stroke:none`，线看不见；marker fill 回退黑色 → 悬空黑箭头），颜色一律走 `style={{}}`；marker 加 `markerUnits="userSpaceOnUse"`；连线命中加 24px 容差（`HIT_PAD`）。
  - 统一返回：全控制器包 `Result.ok()`，删自造 `PagedView` 改平台 `PageResult`（**页号 1 起**），前端 axios 拦截器统一解包（`body.code !== 0` 抛错）。
  - 业务加固：撤回策略（首节点未审批前，audit 存在 `COMPLETE_*` 即拒）、驳回意见必填（前后端双校验）、附件相干性校验、全部写操作 `@Transactional`（Flowable + 审计同事务，修孤儿实例）。
- **风险 / 待办**：节点级审批人类型（依赖 IAM 组织树）、节点级超时策略、定义列表「节点数 / 发起次数」列、邮件通道（SMTP）；RMS 实际回调接线见 §26 之后的 D13（2026-11-09 挂起）。
- **坑**：`BizException` 双参 `(code, msg)` 的 msg 会被 `MessageSource` 当 i18n 键吞掉（平台 handler 只用 `i18nKey + args`）。

## 2026-10-19 approval 三切片 + G2 网关 + M3/M4 契约 + 进度可视化 + 设计器多结束

### 26. feat(approval)：G1/G3/G9 安全加固、G2 二次确认、G4/G5 契约、G6 选人、流程进度可视化

- **范围**（10 提交）：切片1 退回-驳回与重提 `c46730c`、切片2 通知 SPI + 企微免登 `fa37f2e`、切片3 权限与业务域 `f4d4893`、文档与前端联动 `1024e06`、G2 网关 `5eeb6ff`、M3 契约 `8dfe808`、M4 选人 `6fe5203`、进度节点条 `679092c` + 链路扩展 `1b034a8`、设计器多结束 `701901b`。
- **验证（已跑通）**：
  - `verify-security` **11/11**（合法签名 200 / 缺错签名 403 / 超重放 403 / 跨域 403 / 限流 403）；`verify-g1` **6/6**（第二实例 8086 `dev-enforce`：超管通过、非超管 403）；`verify-wecom` **12/12**（无 app-key 调用透传，回归无破坏）。
  - `verify-g2` **14/14**、`verify-hardening` **15/15**（此前 `callback-test` 因下述 ACTOR NOT NULL 缺陷失败，修复后 DEAD 态正常）。
  - `verify-g5` **15/15**（显式 / 派生 / 空三态 + 待办卡片 + G4 BPMN 内容校验）；M4 集成测试 **9/9**、`verify-g6` **14/14**。
  - `verify-multi-end.mjs` **8/8**（设计器多结束节点）。
- **关键修复 / 设计决策**：
  - G9 签名 = `HMAC-SHA256(timestamp|method|path|body)`，常量时间比较；重放窗口 5min、IP 白名单、按 appCode 限流、跨域 403 并 `WebhookNotifyService` 告警。
  - G3 审计表加 `app_code` 列（从流程实例 `tenantId` 解析），定义/实例/任务/审计/回调五类查询按 appCode 收敛，`SecurityGuard.assertAppCodeScope` 统一拦 403（**不是 404**，避免把「无权」伪装成「不存在」）。
  - G2 二次确认：首调签发 5min TTL 令牌且**不覆盖**，回填令牌才覆盖 `baseUrl/secret`；注册/变更补入哈希链审计（原先只有 deliver 有）；`confirmToken` 走 `@RequestParam` 而非改 request record（避免 Jackson 反序列化破坏）。
  - ⚠️ **`approval_audit.ACTOR` 为 NOT NULL**：G2 新增审计在 dev 未传 actor 时传 `null` → `DataIntegrityViolationException`。service 内兜 `actorName = actor 为空 ? "system" : actor`，与既有 `CALLBACK_SENT/FAILED` 的硬编码口径一致。
  - G4 枚举迁移：BPMN 终态 `result` 与撤回回调由 `APPROVE/REJECT/WITHDRAW` → `APPROVED/REJECTED/WITHDRAWN`（任务级 `outcome`、`status`、审计动作**不变**）；集成方需兼容双套值至迁移完成。
  - G5 `displayFields` 落地：持久化为变量 `displayFieldsJson`，`resolveDisplayFields` 三档兜底（显式 → `formSchema.display:true` 派生 → 空），注入三个视图 DTO；前端 `DisplayFields.tsx` 前 4 项 + 24 字符截断 + `+N`。
  - 进度可视化：`flowNodes` = BPMN `userTask` 文档序 × `PENDING/ACTIVE/DONE/SKIPPED`（历史取最近一轮）+ `__start__`/`__end__` 哨兵；⚠️ **终态判定不能依赖 `result` 变量存在**（自定义 BPMN 无终态事件时结束节点会被误判 PENDING）。
  - 设计器多结束：`validateModel` 放开单结束并新增「end 无入口」校验；`toBpmn` 的 end id **按实例生成**（原硬编码 `id=end` 多终点冲突）；`fromBpmn` 显式驳回终点不再被当自动 `reject_*` 丢弃。
  - ⚠️ `deployProcess` **不改写** BPMN `<process id>` → 部署时 processKey 必须与 XML 内 process id 一致，否则发起按 key 查不到（404）。
  - `completeTask` 对候选任务办理时 `setAssignee` 认领留痕（否则历史 `assignee=null`）；节点 outcome 从审计 `COMPLETE_APPROVE/COMPLETE_REJECT` 映射（审计 action 不是裸 `APPROVE`）。
- **风险 / 待办**：① `verify-return`/`verify-wecom` 共用 `default` 租户且 `todo()` 全量查询、断言未按 businessKey 过滤 → 跨脚本残留会误报并连锁污染，须重启清库后运行（应改为按实例过滤）；② 设计器错误横幅只显示 `errs[0]`，多条错误时首条抢占。

## 2026-11-09 平台治理批次（notify 平台化 / SQL 日志 / 配置键 / 模块治理 / MDS DDL）

### 27. refactor(platform)：7 个主题提交 + 2 个仓库卫生修正

- **范围**：`12372b8` SSE 通知平台化（新 `cim-notify-starter` + `platform/web/cim-notify-client` + 两 ap 接入，46 文件）、`e04460a` 可执行 SQL 日志（15 文件）、`a200b5a` 配置键对齐 Spring + workerId 外部化（28 文件）、`e6d29f3` 模块治理（删 `cim-business`、enforcer 真正生效）、`11f206c` MDS 主数据基线 DDL（5 文件）、`ce9a7e2` verify-journey 适配统一 SSE、`5c1a734` 文档同步；`e3c97af` `.gitignore` 卫生修正、`80ea8aa` 决策 D13。
- **验证（已跑通）**：
  - notify：**10 → 11 例全绿**（新增 `NotifyEndToEndTest` 走真实 `/sys/notify/stream` 收信封；`NotifyRedisFanoutTest` 真实 Redis 扇出）。
  - jpa SQL 日志：**34 → 41 例全绿**；approval-ap jar 真机（H2，8085→18085）端到端——关闭态 `[SQL]` 行数 0 → `POST /sys/sql-log?enabled=true` 立刻输出内联参数 SQL → 再关闭后打 3 次接口行数 3→3 不再增长。
  - 平台 `mvn -o test` **13/13**（166 用例，7 skipped = 4 broker + 1 redis + 2 DdlExport）；admission rms-ap **90/90**、iam-ap **72/72**、approval-ap **10/10**。
  - 全平台 `mvn -o install -DskipTests` **12/12 SUCCESS**；干净副本 `git archive HEAD` → `E:/tmp/fresh-check` 跑 `cim-system` 16/16、`cim-jpa-starter` 43/43。
  - 主键外部化：`IdEnvInjectionTest` 2 用例（env 7/3 → 绑定 7/3，缺省 1/1）+ 真机 `CIM_WORKER_ID=7 CIM_DATACENTER_ID=3` 日志 `SNOWFLAKE (workerId=7, datacenterId=3)`。
- **关键修复 / 设计决策**：
  - ⚠️ **生产级 bug**：`NotifyAutoConfiguration` 的 `@ConditionalOnBean(StringRedisTemplate.class)` 与 `RedisAutoConfiguration` 存在排序竞态——先于 Redis autoconfig 求值时 fan-out Bean 不装配，**集群扇出静默退化成 `LocalSseFanout`**。加 `@AutoConfigureAfter(RedisAutoConfiguration.class)`。
  - ⚠️ **enforcer 静默空转**：`bannedDependencies` 配 `includes` 白名单（`com.cim:*`）会导致所有 `com.cim` 依赖被跳过，规则实测不生效；只写 `excludes`。反向验证：给 cache 注入 obs 依赖 → BUILD FAILURE，恢复后通过。
  - ⚠️ **`@Scheduled` 的 `*DelayString` 不认 `5m`**：Spring 6.1 只接受纯数字毫秒或 ISO-8601，写 `5m`/`60s` 启动即 `NumberFormatException`（approval-ap 10 个用例全红）。改 `PT5M`/`PT60S`/`PT3S`；**注解属性是 Duration 统一的唯一例外**，已写死进 `docs/platform/server/design.md §5.1`。
  - ⚠️ **`.gitignore` 的 `**/application.yml` 过宽**：把 5 个模块的 `src/test/resources/application.yml` 一起忽略且无兜底 → 本地全绿只因文件在磁盘上，**新克隆跑 `mvn test` 会缺数据源/Flowable/认证夹具**。根与 `platform/server/` 两级 `.gitignore` 各补 `!**/src/test/resources/application.yml`（`git check-ignore` 正反两面验过）。
  - SQL 日志两级开关：`cim.jpa.sql-log.enabled` 管「装不装代理」，是否输出在 `getConnection()` 时实时判 `logger.isDebugEnabled()` → 可 HTTP 动态开关，关闭时直接返回真实连接（零开销）。
  - notify 解耦 obs：新增 `NotifyMetrics` 门面（只依赖 micrometer-core，`ObjectProvider<MeterRegistry>` 可选注入），去掉 `cim-obs-starter` 依赖。
  - 配置键铁律：同一概念只留一套键，能映射 Spring 标准键就不自造 `cim.*`（删 `cim.jpa.flyway.*`、`cim.jpa.naming.lowercase-snake`、`cim.obs.metrics.common-tags.*`、`cim.obs.trace.enabled`），框架只在用户未显式配标准键时补默认值。
  - 真集成测试门控：mq 的 Rabbit/Pulsar、notify 的 Redis 改**类级 `@EnabledIf` 端口探测**——这些测试在上下文启动阶段就连 broker，方法内 `assumeTrue` 来不及，会把构建判红。
- **风险 / 待办**：跨实例 Redis 扇出与真实 JWT 鉴权拒绝路径需含 Redis/真令牌环境确认；`BearerTokenQueryParamFilter` 在真实 `JwtAuthenticationFilter` 链下的拒绝路径待验；**approval-ap 原本靠 notify→obs 传递拿到 actuator，现已没有**——将来要 health probe 须显式加 `spring-boot-starter-actuator`。

## 2026-11-11 前端组件中台 P0–P20（5 个包 + 三应用页面全量迁移）

### 28. feat(web)：`@cim/{theme,ui,data,shell,i18n}` 建成，RMS 19/19 + IAM 16/16 + approval 9/9 收敛

- **范围**（33 提交）：`platform/web/packages/{theme,ui,data,shell,i18n}` 五个包；三应用 `Layout`/页面/共享弹窗改为薄适配层；`docs/platform/web/component-platform-design.md`（P0–P20 设计稿 + 逐批实施记录）。
- **验证（已跑通）**：
  - 每批均 `tsc -p tsconfig.json --noEmit`（strict + `noUnusedLocals/Parameters`）零错误 + `vite build` 通过。
  - 契约桩 + 无头 Chrome 自驱动探针，**累计 100+ 场景**：RMS 17 场景 / 49 项断言（§10.10–§10.15）、IAM 55 场景（P14–P16d）、approval 47 场景（P17 13 + P18 16 + P19 10 + P20 8），**0 未捕获异常**。
  - 关键量化证据：已办/我发起翻页**不再发请求**（全量取回 + 本地分页），关键字在全 25 条中命中 6 条并回第 1 页；工作台 `?route=stats` 注入失败后 KPI 只剩 5 项 + 「其中 1 / 6 项数据未取到」；发起弹窗 XML 请求**恰好 1 次**（自激循环修复）；详情内重提使 detail 请求 **2 → 1**；待办表格 `scrollWidth 1325 / clientWidth 745` 判定为列内换行而非溢出。
- **关键修复（迁移过程中暴露的真缺陷）**：
  - `DeviceRepository.search` 的 keyword 只匹配 `code`/`name`，列表却有 IP 列 → **IP 搜索从来没工作过**（服务端已丢弃不匹配行，前端那段客户端过滤是死代码）。JPQL 补 `ip` + `DeviceAdminTest` 一条断言。
  - 已办/我发起「关键字只过滤当前页」：第 3 页的单号永远搜不到却显示「没有」→ 改全量取回 + 本地分页。
  - 工作台 `Promise.all` + `try/catch` 把「没取到」和「真没有」混成 0 → `Promise.allSettled`，单路失败只让该格显示 `—`。
  - 发起弹窗角色节点解析把每次 render 新建的 Map 放进 effect 依赖 → **自激请求循环**（渲染→请求→setState→再渲染）。
  - 草稿按「第一个 `approval.draft.v1:*` 槽」加载 → 两个流程都有草稿时打开 A 会加载 B 并把 `processKey` 切走。
  - 待办刷新后旧勾选残留 → 「批量通过 3 条」实际通过 0 条还报成功。
  - 设计器「复制成功」提示先于实际 `writeText`（剪贴板被拒也报成功）。
  - 回调事件类型只登记 1/5，其余 humanize 成半截英文。
  - 语义色第 6 色（`info`）：`StatusTone` 只有 `ok/warn/err/run/idle` 五档，`info` 一律映射 `run`。
  - 详情弹窗取数失败 `catch { toast; onClose() }` → 关掉弹窗等于把错误一起关掉；改为错误留窗内 + 可重试。
- **风险 / 待办**：§12 待定项 #2（HTTP 传输层是否合并）、#3（字典 i18n 化）、#5（列定义后端下发）、#8（基础令牌加 `--cim-` 前缀）、#9（译文灌入 `sys_i18n`）**仍开放**，均有明确触发条件；`useDict`/`EnumTag` 已实现但**三应用未注入 `UiDictProvider`**（当前仅内置兜底形态）；a11y 按 WCAG 2.1 AA 基线设计，**尚未做自动断言**（前端目前无单元测试设施，验证靠 tsc + build + 探针）。

## 2026-11-12 统一响应契约（Result 强制 + 例外标注）与消息分发模式

### 29. feat(spring-support)：`ApiContractVerifier` / `@RawResponse` / `CimMessageDispatcher` 落地，9 个非 Result 端点整改

- **范围**（5 提交 + 本文档）：`cim-spring-support`（`web/RawResponse`、`web/ApiResponseProperties`、`web/ApiContractVerifier`、`dispatch/` 5 类、`config/CimDispatchAutoConfiguration`、自动装配 imports）、`cim-auth-starter`（两个 IAM 校验器的信封兼容）、`iam-ap`（4 端点改 Result + 2 处例外标注 + 2 集成测试）、`rms-ap` / `approval-ap` / `cim-notify-starter`（各 1 处例外标注）、`iam-ap/web` + `rms-ap/web`（删 `postRaw` 旁路）、`docs/platform/server/api-convention.md`（新规约）。
- **动因**：参考外部工程 `CustomAutoProcessorController` 的通配分发实现。取其「约定优于逐个 `@PostMapping`」的意图，但补齐它的三处结构性弱点（信封靠处理器自己 `return`、处理器泛型与 DTO 不绑定校验、通配 URL 把内部类名写进对外契约），并把「返回必须是 `Result`」从口头约定变成启动即失败的机制。
- **验证（已跑通）**：
  - 平台 `mvn -o test` **13/13 SUCCESS**；`cim-spring-support` **22/22**（新增 `ApiContractVerifierTest` 6 例 + `CimMessageDispatchTest` 7 例，钉住：全 Result 通过 / 裸类型严格模式失败且清单含 `BadController#bad()` / 非严格只告警 / 类级与方法级 `@RawResponse` 均放行 / `@ExceptionHandler` 不参与 / `verify=false` 跳过；路径派生与归一化 / 重复路径失败并含双方类名 / 泛型错绑失败 / 成功包信封 / 未知路径 `PARAM_INVALID` / 坏报文指明类型 / 空报文给空实例）。
  - `cim-bootstrap` **5/5**（证明分发入口不再被宿主 `scanBasePackages="com.cim"` 误扫——首版用 `@RestController` 时 5/5 全红于 `No qualifying bean of type CimMessageDispatcher`）。
  - iam-ap **72/72**、rms-ap **90/90**、approval-ap **10/10**（后两者 `-Dcim.notify.cluster.enabled=false`，本机无 Redis）、auth-starter **29/29**（含信封新旧双形态用例）。
  - 前端：iam-web `tsc` + `vite build` 通过；rms-web `tsc --noEmit` 零错 + `vite build` 通过（10.11s）。
- **整改清单（9 个非 `Result` 端点 → 4 改信封 / 5 标例外）**：
  - 改 `Result<T>`：`token-version.current`（原 `ResponseEntity<String>` + `TEXT_PLAIN`）、`token-version.bump`（原 `Map`）、`blacklist.isRevoked`（原 `String`）、`issue.issue`（原裸 `IssuedToken`）。
  - 标 `@RawResponse`：`jwks`（`STANDARD_PROTOCOL`）、`/login`（`HTML`）、recipe `export`（`DOWNLOAD`）、approval `attachment download`（`DOWNLOAD`）、notify SSE（`STREAM`）。全表见 `api-convention.md` §4。
- **关键修复 / 设计决策**：
  - **`@RestController` 是 `@Component`**：库里的分发控制器若标 `@RestController`，宿主 `scanBasePackages="com.cim"` 会把它扫进容器，而自动装配关闭时依赖 Bean 不存在 → 启动失败。改 `@RequestMapping`(type) + `@ResponseBody`：MVC 认得、组件扫描不认。
  - **校验器必须能排除框架出口**：首版把 `BasicErrorController#error() -> ResponseEntity` 也判违规（cim-jpa-starter 17 个测试全红），加 `cim.api.response.include-packages`（默认 `[com.cim]`）收口。
  - **静态解析不可靠，靠启动期校验兜底**：我按正则扫 150 个映射方法得出「7 个非 Result」，实跑校验器抓出第 8 个 `AttachmentController#download`（签名跨行 + 文件名不匹配 `*Controller`），复核后为 9 个。这条本身就是「规约要长牙」的证据。
  - **滚动发布**：内部 S2S 端点改形态时消费侧必须双兼容，否则先升级任一侧都会让版本/黑名单校验**静默 fail-open**——`IamTokenVersionChecker`/`IamTokenBlacklistChecker` 按响应首字符分流，`code != 0` 视为失败而非放行。
  - **测试夹具不套例外**：`cim-auth-starter` 的 `TestApi` 六个端点故意裸返回以验证安全过滤链，给它们写 `@RawResponse` 理由不成立 → 改在该测试 `application.yml` 关 `verify`。同文件原已有一个顶层 `cim:` 块，追加会造成 SnakeYAML 重复键报错，已并块重写。
- **补完（同日）**：
  - `cim.api.response` / `cim.api.dispatch` 已写入 4 个配置模板（`cim-bootstrap` + `iam`/`rms`/`approval` 的 `application.yml.example`），并用 SnakeYAML 实解析验证四份 YAML 均合法；6 个键确认都在 `spring-configuration-metadata.json` 里（键不在元数据里 = 没被 `@ConfigurationProperties` 承载）。
  - **IAM 渲染契约恢复 33/33**：两条结构性断言随组件层迁移失效（空态类名 `.empty` → `.cim-empty`、树 `.tree` → `.cim-tree`）；12 条"含中文残留"其实是**测试装配缺陷**——未挂 `UiLocaleProvider` 时 `useUiLabel` 全部回落到 `@cim/ui` 内置中文兜底，产品代码并无泄漏。修法：按 `main.tsx` 同构挂 Provider、`t` 走缺键处理器（`⟪key⟫` 标记）+ `hasKey` 恒为 `true`（本测试断言的是"请求了哪些键"，不是"键有没有译文"）；失败信息也从"含中文：共"升级为带上下文片段，便于定位是哪个组件漏键。`api-convention.md §5.4` 记录三个坑。
- **风险 / 待办**：消息分发三应用均未启用（等真实场景再开）；内部 S2S 端点的双兼容分支待旧客户端下线后清理；MDS/MES 两 ap 前端仍是空脚手架，暂无渲染契约闸门。

## 2026-11-12（续）前端组件采用度审计与上提

### 30. refactor(web)：组件采用度实测、权限门静默失效修复、图标与提示块上提

- **范围**（5 提交 + 审计文档）：`platform/web/packages/ui`（`icons.tsx` 34 图标、`data/KpiCard.tsx`、`feedback/InlineAlert.tsx`、`ui.css` 收 `.alert` 段）、`business/{iam,rms}-ap/web`（`main.tsx` 挂 `UiPermissionProvider`、30 处 `Perms`→`Can`、`Icons` 改薄壳、看板改 `KpiCard`）、`business/approval-ap/web`（`styles.css` 清理）、`docs/platform/web/component-adoption-audit.md`（新）。
- **采用度实测（脚本口径，非估算）**：

  | 应用 | 页面 | 用 `@cim/*` | 裸 `<button>` | `.kpi` | `.alert` | `.field` | `.split` | `styles.css` |
  | --- | --- | --- | --- | --- | --- | --- | --- | --- |
  | iam-ap | 21 | 16 | 74 | 0（已迁） | 11 | 65 | 5 | 593 |
  | rms-ap | 23 | 19 | 92 | 131 | 9 | 0 | 1 | 1175 |
  | approval-ap | 10 | 9 | 5 | 40 | 1 | 36 | 0 | 1994 |

  结论：数据层与容器层（`Page`/`Panel`/`DataTable`/`useResource`/`FormGrid`/`CimModal`）已收敛；控件层没收敛——`CimButton` 仅 11 文件，`StatusTag`/`ToneTag`/`CimTooltip`/`CimSplitter`/`useColumns` **0 采用**。
  - ⚠️ **口径更正（同日后补）**：上表 `.kpi` 与 `.field` 两列是按 `className="kpi` / `"field"` **前缀**匹配出来的，把 `kpi-main`/`kpi-label`/`kpi-val`/`field-label`/`field-hint` 这些**子元素**也算进去了，属于口径错误。按「容器元素」精确复核：`.kpi` 容器 **40 处**（非 171）、`.field` 容器 **49 处**（非 101）。裸 `<button>` 的 74/92/5=171 是对的（按元素计）。
- **关键修复（静默失效，本轮最重要的一条）**：`@cim/ui` 的 `<Can>` 与 `<CimButton perm>` 读 `UiPermissionProvider` 注入的上下文，而**三个应用一个都没挂这个 Provider**——未挂时走「一律放行」兜底，界面完全正常、权限门却不起作用，也不会有任何报错。已在 `iam`/`rms` 的 `main.tsx` 补挂（`has` ← `usePermissions`，`ready` ← `permissionsLoaded`），30 处 `<Perms code|codes>` 全量并入 `<Can perm>`（含 9 处字符串字面量写法），删两份逐字节相同的 `Perms.tsx`。
- **上提与去重**：
  - 通用图标 34 个 → `@cim/ui/icons.tsx`：证据是 IAM 34 个是 RMS 37 个的**子集**且 `<svg>` 子节点**逐字节相同**（md5），两份 723 行重复；两应用文件改薄壳（44 / 86 行），页面 `from '@/components/Icons'` 零改动。approval 的 30 个是另一套画法（`IconCheck` vs `IconCheckCircle`），**不强合并**。
  - `.alert` 段 7 条规则 → `ui.css`（三份 `styles.css` 该段逐字节相同），应用侧删 31/36/36 行；`approval` 独有的 `.login-card .alert` 保留。产物 CSS 已核对：7 个选择器均来自 `cim-ui` 包。
  - 新增 `KpiCard`/`KpiGrid`（`.kpi` 手写 178 处，IAM 还自带同款局部组件）与 `InlineAlert`（`.alert` 手写 22 处，刻意不含重试按钮——按钮位置各页现状不同）。
  - 删 4 个**零引用**组件：`components/Panel.tsx` ×3、`rms/components/Drawer.tsx`。（页面是 `pageRegistry` 里 `lazy()` 的，按「谁 import 了它」扫会把全部页面误判成死代码——审计文档已记录这个坑。）
- **验证（已跑通）**：三应用 `tsc -p tsconfig.json --noEmit` 零错 + `vite build` 通过（9.35s / 9.79s / 9.65s）；IAM 渲染契约 **33/33**（该测试同步补挂 `UiPermissionProvider`——否则 `<Can>` 走放行兜底，「无权限不挂载」的断言会**反转成通过**）；产物 CSS 中 `.alert` 7 选择器来自 `cim-ui-*.css`、`.login-card .alert` 覆盖仍在。
- **风险 / 待办**（下列三条均已于同日落地，见 §31）：
  - ~~**171 个裸 `<button>` 未动**~~ → **已按 B 方案全量替换**（`CimButton` 内部改渲染原生 `.btn`，视觉零变化），见 §31。
  - ~~`KpiCard` 尚未推广到 RMS / approval~~ → **已推广**，靠 `KpiCard bare` 承载「整卡是链接」形态，见 §31。
  - ~~`InlineAlert` 余 21 处待逐页替换~~ → 已清零；状态页 6 个统一 `Panel + EmptyState`，见 §31。
  - 仍待办：`LanguageSwitcher`（三份语义不同）/ `NotificationBell`（两份，依赖各 ap 通知 API）见审计文档 §4 的 ROI 排序。
  - 负向清单（明确**不**上提）：`RecipeDetailDrawer` / `SpecEditModal` / `DiffTable` / `DetailModal` / `FlowProgress` / `IdentitySwitcher` / `PermTree` —— 均绑领域模型，避免后来者重复评估。

### 31. refactor(web)：手写控件全量改用组件层（按钮 / 表单 / 提示 / 指标卡 / 状态页）

- **决策依据**：用户指令「**没有用已有组件的都应该用**」。这条同时隐含了 §30 里 A/B 两方案的选择——「都用组件」与「视觉不变」必须同时成立，若 `CimButton` 保持 antd 外观，替换就是全站改版 → 走 **B**：`CimButton` 内部改为渲染原生 `<button className="btn …">`。
- **数量决定方向**：手写 `.btn` 侧 200 处 vs `CimButton`(antd) 侧 45 处。让组件迁就多数派只改一处；让 200 处迁就 antd 是一次全站视觉改版。
- **先补组件、再做替换**（关键顺序，否则每一步替换都是退化）：
  | 缺口 | 补法 | 为什么必须先补 |
  | --- | --- | --- |
  | `CimButton` 只有 antd 外观 | 改渲染原生 `<button>`，新增 `bare`/`btn-text`/`btn-spinner`/原生属性透传 | 否则每一个替换点都是视觉变更 |
  | `icon-btn`/`chip-x`/`tree-toggle`/`user-chip`/`bell-item`/`node-del`/`dcrumb-link` 不是按钮 | `bare`：不套 `.btn` 基类，样式全交给 `className`，组件只给权限门与无障碍 | 套上 `.btn` 会拿到主色底 + 14px 内边距 + 600 字重 |
  | `CimField` 是**空壳**：组件存在但 `ui.css` 里没有任何 `.cim-field` 样式 | 补 `.cim-field*` 全套（原样搬自三份逐字节相同的 `.field` 段） | 这是「组件化不了」的真实根因——不是组件没写，是样式没跟着上来 |
  | `KpiCard` 只能渲染 `<div>` | 加 `bare`（只吐内层 body）与 `err` 色调 | RMS 指标卡整卡是 `<Link>`；approval 早就拼 `'kpi' + (dead>0?' err':'')` 但 `kpi.err` 这条 CSS 从来没写过 |
  | `EmptyState` 只有主副文案 | 加 `icon`/`children`（动作区）/`large` | 403/404/ComingSoon 是整页状态，需要图标与「返回概览」按钮 |
  | `CimField`/`InlineAlert` 无 `style` | 透传 `style` 与其余原生属性 | 存量 21+14 处 `style={{ marginTop: 10 }}` 这类就地微调，否则每处都要包一层 div |
- **替换结果（脚本批量 + 逐页复核）**：
  | 项 | 替换前 | 替换后 | 说明 |
  | --- | --- | --- | --- |
  | 裸 `<button>` | 171 | **0** | `CimButton` 元素数 45 → **248** |
  | `.field` 容器（含手抄 `cim-field`） | 49 + 20 | **0** | `CimField` 元素数 → **79** |
  | 裸 `input`/`textarea.text-input` | 11 类 98 处 | 仅剩 checkbox/radio/file/number | `CimInput` 元素数 → **66**；语义控件不属文本框组件 |
  | `.alert` | 22 | **0** | `InlineAlert` 元素数 → **31** |
  | `.kpi` 容器 | 40 | **0** | `KpiCard` 元素数 → **51**（含 `bare` 链接形态） |
  | 手写状态页 | 6（三套写法） | 0 | 统一 `Panel + EmptyState large` |
  | 三份 `styles.css` 合计 | 3762 | **3347** | 删 `.btn` 62 / `.field*` 79 / `.alert` / `.empty-state*` / `.text-input` |
- **三条坑（都是「不看就会踩」级别）**：
  1. **`.btn` 是联合选择器 `button, .btn`**，不是单纯类。原设计下**所有原生 `<button>` 默认就是主色按钮外观**，`btn-ghost`/`btn-sm` 只是修饰类——页面里大量 `<button className="btn-ghost btn-sm">`（**没有** `.btn` 基类）照样正常。第一版把 `.btn` 单独搬走，导致 166 个按钮退回浏览器默认外观，还在三份 `styles.css` 留下悬空的 `button,` 让后继 `.row` 规则被吞进同一组选择器。已在 `ui.css` 恢复联合选择器并写明原因。
  2. **批量替换必须区分「按钮」与「长得像按钮的控件」**。第一版把 `icon-btn` 全改成 `type="primary"`，视觉全变——**IAM 渲染契约 3 条断言当场抓到**，否则会静默上线。故脚本加 `bare` 分支，回滚全部 `.tsx` 重跑。
  3. **改渲染目标会改变属性顺序**：`{...rest}` 提前展开后标签变成 `title → type → class`，写死相邻属性的断言（`<button class="icon-btn" title="…"`）会因为一个正确的新增属性而失败 → 断言改 lookahead 跨属性匹配（`05482da`）。
- **伴生规则漏搬的回归（`d793fe5` 修）**：`.cim-field + .cim-field { margin-top: 11px }` 原本由 `.grid-2 > .field + .field { margin-top: 0 }` 在网格里禁掉；换成组件后网格容器变成 `.cim-form-grid`，**这条例外没跟着上来** → 2/3 列字段在 11px gap 上又叠 11px margin，整列下移。补 `.cim-form-grid > .cim-field + .cim-field`，`.grid-2/.grid-3` 两条也保留（页面手写网格仍在用）。**教训：上提一段 CSS 时要连它的「例外 / 覆盖」规则一起搬，只搜主选择器会漏。**
- **保留的手写（不是漏网，是「组件表达不了」）**：
  - 9 处 `<Link className="btn …">`：`CimButton` 渲染 `<button>`，`<a>` 里嵌 `<button>` 是非法 HTML。这正是 `.btn` 必须保留联合选择器 `button, .btn` 的另一半理由。
  - `<Link className="kpi">`：整卡是链接，内层用 `KpiCard bare`。
  - `approval` 的 `.field-row`：一行控件的布局工具，不带标签，与 `CimField` 语义不同。
- **验证（全绿）**：三应用 `tsc -p tsconfig.json --noEmit` 零错；`vite build` 通过（13.51 / 13.37 / 13.64s）；IAM 渲染契约 **33/33**；产物 CSS 核对 `.btn-ghost` 仅 1 份且来自 `cim-ui` 包。
- **提交**：`d00227f`（ui 层补齐）→ `dc5d6a1`（iam）→ `72f69fd`（rms）→ `35ed2b3`（approval）→ `05482da`（渲染契约）→ `d793fe5`（网格 margin + compact）→ `5158e2f`（最后 3 处 `.field` 与三份 CSS 段删除）。
- **待办**：`StatusTag`/`ToneTag`/`CimTooltip`/`CimSplitter`/`useColumns` 仍 0 采用（`ToneTag`/`StatusTag` 与 `Tone` 语义重叠，需先合并 API）；`LanguageSwitcher` 三份语义不同；`NotificationBell` 两份依赖各 ap 通知 API。

### 32. refactor(web)：待办清单清账——顶栏控件上提、SplitPane、状态标签收口

§31 收尾时留了 5 项待办，本轮逐项处理（3 项落地、2 项给出「不做」的边界）。

**32.1 三份 `LanguageSwitcher` → `@cim/shell` 的 `LocaleSwitcher` + `useLocaleSwitcher`**

- 三份实测差异：IAM / RMS 结构几乎逐字节相同（只差 tooltip 的键前缀），approval 那份**缺三项通用能力**——切换中不禁用（连点会让译文包与 UI 语言错位）、不支持 `className`、`title` 硬编码 `"Language"`（切中文后仍是英文）。
- 拆成「受控组件 + hook」而不是整块搬：三家的差别全在**数据来源**（IAM 走 `/api/i18n/locales` 远端目录，另两个是内置两语种），把取数也塞进组件就得为一个尚不存在的通用接口建模。`useLocaleSwitcher` 收 `{ currentLang, changeLanguage, loadLocales }` 三个注入函数——三个 ap 的 `lib/i18n.ts` 恰好导出同名函数，无需再写适配器。
- ⚠️ **`runtime` 是调用点内联的对象字面量，每次 render 都是新引用**：直接挂进 `useEffect` 依赖会「拉目录 → setState → 重 render → 再拉」自激，故用 ref 跟进最新引用、effect 只跑一次。
- 结果：1945B / 1455B / 865B → 三个 ~900B 的同构薄壳（各 10 行有效代码）。approval 顺带补上 busy 态与 `shell.language` 键。

**32.2 两份 `NotificationBell` → `@cim/shell`（展示层）**

- ⚠️ **这两份不是同一个东西**：RMS 有持久表 `rms_notification` + 未读数轮询 + 标记已读 + 点击直达；approval **没有通知表**，只有 SSE 收到即入内存的会话级列表（后端注释亦写明「后续按各 AP 自管原则补通知表」）。所以只上提界面与交互，数据语义留在应用。
- 边界画在 props：`unread` / `items` / `labels` / `onRefresh` / `onOpenItem` / `onReadAll` / `connected` / `onOpenChange`。`onOpenChange` 是为 RMS 的 SSE 回调加的——「面板开着才顺手刷新列表」需要知道开合状态，而状态在组件内；把状态提给应用等于让三个应用各写一遍点外关闭。
- 样式同批上提：`.bell-*` 在 RMS 与 approval 各一份、**逐字节相同**（实测），且 approval 那份是**死代码**（页面早改用 `.tab-ctx` 菜单）——这说明这套才是本意。收进 `@cim/ui/shell.css`，两处副本删除（RMS -93 行、approval -93 行）。
- `IconBell` 补进 `@cim/ui/icons.tsx`（取 RMS 画法）。approval 自己的图标集仍保留，因为它还在页内按钮上用（「催办」）——**外壳 chrome 用共享集、页内图标用各自集**是既定规则，不是漏改。

**32.3 `.split` × 6 → `CimSplitPane`**

- `.split` 不只是两列 grid：`.split > .panel { min-height: 0 }`、`.split .panel-body` 要弹性填充并内部滚动、左列 `.panel.scroll-y` 吸顶。这些约束写在 CSS 里看不见，页面手写 div 时少一层 / 多一层包裹，就变成「表格把面板撑破、整页双滚动条」。收进组件后约束随组件走，并在开发态对「子元素不是 2 个」发提醒（不抛错——能正常渲染的页面不该被拦住）。
- **不是为了用而用**：`CimSplitter`（可拖拽、尺寸可上报）一直是 0 采用，`.split`（固定定宽）有 6 处在用。给 6 处真实用法一个组件，比让它们继续手写 div 更有意义；两个组件的分工写进了各自 header（管理页主从 = `CimSplitPane`；两栏都是主工作区 = `CimSplitter`）。

**32.4 `ToneTag` 删除 + `StatusTag` 落地（含两处语义错的修正）**

- `ToneTag` 是 `CimTag tone` 少了 `outline`/`size` 的子集，纯重复 → 删除。真正在用它的只有 ui 内部的 `dict.tsx`（字典驱动的标签），改 `<CimTag tone>`，并**显式补回 `cim-status-tag` 类**（那个类带圆角/字号/内边距，原来由 `ToneTag` 自带，不补就是静默的观感变化）。
- `StatusTag` 一直 0 采用，根因不是「页面不知道有它」，而是 `@cim/theme` 的 `STATUS_TONES` **只登记了 3 个域**，页面要的枚举不在表里，只能各自写 `statusTone()` / `STATUS_TONE`。按后端枚举把域补全后，页面删掉本地映射改 `StatusTag`：业务侧引用 0 → **17**。
- **修出两处真错**：
  1. `approvalState` 原登记 `RUNNING / APPROVED / REJECTED / RETURNED` —— 那是 Flowable 的结束事件语义。服务端实际产出是 `running ? "ACTIVE" : (deleteReason != null ? "WITHDRAWN" : "COMPLETED")`，即 **ACTIVE / COMPLETED / WITHDRAWN**，原表的值全错、还多两个不存在的值。
  2. `RecipeDetailDrawer` 自写 `DRAFT → run`，与表的 `DRAFT → idle`（设计稿 §2.3：idle = 草稿）分叉 —— 注释却写着「与 @cim/theme 的 recipeState 口径一致」。
  另有一处**按语义纠正**：`IntegrationPage.stageTone` 把所有非终态阶段一律给 `warn`（橙），而设计原则是「进行中用 run（蓝）」；改为查表后 `GRANTED / SENT_TO_TOOL / TOOL_ACKED` 变蓝。这是有意的视觉变更——橙在产线语境里等于「需人处理」，给进行中的阶段是误读。
- 新增域：`signoffState`（RMS `SignoffStatus`）、`bypassState`（`Bypass['status']`）、`overrideState`（资格矩阵 / 黄金覆盖共用的审批结论）、`tempReleaseState`（限时放行窗口）。
- **没动的**：RMS `toneOfAction`、IAM `toneOf(type, result)` —— 它们按**动作码模式**判定（`endsWith('_DELETED')`、`startsWith('SIGNOFF_')`），不是枚举字典，塞进 `dict → value → tone` 的表里反而表达不了。这是「模式 vs 枚举」的界线，不是漏改。

**32.5 顺带修掉两处 `bare` 漏改（上一批的回归）**

批处理脚本只认字符串字面量的 `className`，漏了两处**表达式类名**的按钮，被误加了 `type="primary"` → 套上 `.btn` 基类（主色底 + 14px 内边距 + 白字）：
- `rms/NotificationBell` 的通知条目 `className={'bell-item' + …}`；
- `iam/PermTree` 的树展开箭头 `className={'tree-toggle' + …}`。

两处都改 `bare`。**这轮之所以能抓到，是因为回头按「`<CimButton` 有 className 但不是 btn 家族且没有 `bare`」重新扫了一遍**——脚本的盲区不能只靠一次运行就认为清干净了。

**32.6 「0 采用」的两项给出边界、不再反复评估**

- `useColumns`：**不是没用**，是 `DataTable` 内部在用（`DataTable.tsx:18` import、`:167` 调用）。按业务侧引用数统计必然为 0，把它当死代码删掉会直接打断 `DataTable`。
- `CimTooltip`：站内 124 处提示走原生 `title=`，都是一行短文案（图标按钮说明、`.dim` 小字）。原生属性在这些场景更合适（无延迟、不抢焦点、无额外 DOM）；`CimTooltip` 的适用面是**多行/富内容/需要 `placement`**。把 124 处全换掉是净负担，边界写进审计文档 §5.1。

- **验证**：三应用 `tsc --noEmit` 零错 + `vite build` 通过（9.49 / 9.71 / 9.50s）；IAM 渲染契约 **33/33**；`StatusTag` 业务引用 0→17、`LocaleSwitcher` 3→1 份、`NotificationBell` 2→1 份、`.split` 6→0 处手写、`ToneTag` 删除。

## 2026-11-12（续 2）待办残留复扫与再收敛

### 33. 全量复扫：`KpiGrid` 能力缺口、路由守卫去重、以及一处**未经验证**的后端写法

- **触发**：上一轮（§32）报的 6 项待办已清账，但「已组件化」的结论是按**具名函数**（`statusTone`）和**字符串字面量类名**统计的。本轮换判据重扫，发现统计口径漏了两类：① 容器类名（`kpi-grid` 不在上一轮的 `.kpi` 口径内）；② **内联三元**写的状态映射。
- **复扫结果（脚本口径）**：

  | 项 | 数量 | 判定 |
  | --- | --- | --- |
  | 裸 `<button>` | 0 | ✅ 上一轮已清零 |
  | `className="field"` / `"split"` / `"alert"` | 0 / 0 / 0 | ✅ 已清零 |
  | `className="kpi-grid"` | **12** | ❌ 本轮修（见 33.1） |
  | 手写 `<table>` | 1（`RecipeDetailDrawer` 键值表） | 保留：两行 k/v，引 `DataTable` 不划算 |
  | 裸 `input/select/textarea` | 6 / 0 / 0 | 保留：全为 `checkbox`/`radio`/`file`，`CimInput` 刻意不收 |
  | 未用 `useResource` 的页面 | 13 / 54 | 保留：9 个状态页 + `DesignerPage`（画布）/ `ProfilePage`（单对象）/ `LoginPage` |
  | 内联 `tone={x === 'A' ? 'ok' : 'warn'}` | **61** | 部分可入字典（见 33.4） |

**33.1 `KpiGrid` 只收 `children`，页面只能手写**

`KpiCard.tsx` 里 `KpiGrid({ children })` 没有 `className` / `style`，而 12 处手写里有 3 处要压外边距（`DeviceRecipePage` 嵌在 `Panel` 内 `margin: '10px 14px 0'`、`IntegrationPage` / `SignoffPage` 的 `marginBottom: 10`）。组件既不能透传，页面就只能绕开——这跟 `CimField` 当年「组件在、样式没上来」是同一类根因：**组件能力不足，不是页面偷懒**。

补 `className`（与 `kpi-grid` 合并而非覆盖）与 `style` 后，8 文件 12 处全部替换，`<KpiGrid>` 采用 2 → **14**，`className="kpi-grid"` 归零。
⚠️ 批量脚本第一版把配对闭合标签的下标写错了（深度归零时 `q` 没指向那个 `</div>`），子元素被整体挪到 `</KpiGrid>` 之后，且 `style={{…}}` 少了一层花括号。**已 `git checkout` 回滚重做**——这类脚本改完必须先看 diff，不能只看替换计数。

**33.2 `ProtectedRoute` 两份 md5 相同 → `@cim/shell`**

`iam` 与 `rms` 的 `components/ProtectedRoute.tsx` md5 **完全一致**（`f5911ec3`），却各存一份。上提为 `@cim/shell` 的 `ProtectedRoute`，收 `authenticated: boolean` + `loginPath`，token 由应用从自己的 `authStore` 取——与 `LocaleSwitcher` / `NotificationBell` 同口径（**组件收事实，不收状态库**）。两应用文件退化为 7 行薄壳。

**33.3 `RequirePerm` 与权限判定：结构收敛，行为差异显式化**

两应用的 `RequirePerm` 原本是同一概念的两种写法：IAM 用 `useHasPermission` + `<Navigate to="/403" replace>`，RMS 直接读 store + 渲染 `<ForbiddenPage/>`。收敛为：
- 新增 `@cim/shell` 的 `RequirePermission({ allowed, fallback, children })`——判定由应用算好，**未授权呈现走 `fallback` 注入**；
- RMS 改走 `useHasPermission`，与 IAM 同一条读取路径（两应用的 `lib/usePermission.ts` 本就 md5 相同）；
- ⚠️ **两种 403 呈现继续并存，且这是有意的**：跳转 `/403` 会改 URL，就地渲染不改（权限补上后刷新即达原页）。它影响「刷新 / 后退 / 分享」，属产品决策，组件层不替应用统一。若要统一，建议统一到**就地渲染**（可分享、可刷新），需另开一项。
- `permissions.ts` 的三个纯函数（`hasPermission` / `hasAnyPermission` / `hasAllPermissions`）上提 `@cim/ui`：两应用该文件 39 行 ×2，**除末尾应用专属常量外逐字节相同**（IAM `CONSOLE_ADMIN`、RMS `SUPER_ADMIN`）。应用侧文件保留为「re-export + 本地常量」，6 处引用零改动。

**33.4 未修的两项，附边界**

- **61 处内联 `tone=` 三元**：按字段频次 `status`×4、`result`×3、`versionStatus`×2、`qualificationStatus`×2，其余各 1。重复 ≥2 的可入 `STATUS_TONES`（`qualificationStatus` / `versionStatus` / `auditResult`）；只出现 1 次的是页面局部判定（`reconcileResult === 'DRIFT'`、`severity === 'CRITICAL'`），进字典反而制造无人复用的键。**判据：同一枚举跨页出现 ≥2 次才入字典**。
- **`approval` 的 `Icons.tsx`（235 行 / 32 图标）全在本地**：其中 14 个与 `@cim/ui` **同名**（`IconBell`/`IconChevron`/`IconInbox`/`IconLogout`/`IconMoon`/`IconPlus`/`IconRefresh`/`IconSearch`/`IconShield`/`IconSidebar`/`IconSun`/`IconTrash`/`IconUser`/`IconX`），但逐个比对 `d` 属性**全部不同**——approval 是 24×24 stroke 画法，平台是另一套网格。按「逐字节相同才上提」的判据**不该合并**，合并会改 approval 的图标观感。这是**视觉一致性问题**，不是组件化问题，需单独决策。

**33.5 后端：三处 `@RestController` 与 `@Bean` 双注册（本轮改到一半回退）**

`cim-i18n-starter` 的 `I18nController`、`cim-jpa-starter` 的 `SqlLogAdminController`、`cim-notify-starter` 的 `SseController` 都有两类装配身份：auto-config 里的 `@Bean`（带 `@ConditionalOnProperty`）**加上**类级 `@RestController`（即 `@Component`）。宿主 `scanBasePackages="com.cim"` 时（`cim-bootstrap` 就是）组件扫描会把它们无条件注册，绕过条件——`SseController` 自己的 javadoc 写着「以 `@Bean` 注册（库 jar 不依赖宿主组件扫描）」，注释与实现相反。

按 `CimDispatchController` 的写法（类型级 `@RequestMapping` + `@ResponseBody`，不带 `@Component`）改掉后**实测挂了**：
- `SqlLogAdminControllerTest` 3 例 `404`（`standaloneSetup` 下 `@RequestMapping` 未被当处理器）；
- `NotifyEndToEndTest` 5s 内收不到 SSE 推送（真实 HTTP `/sys/notify/stream` 未注册）。
回退后两者立即恢复（`BUILD SUCCESS`）。

由此得出一条更要紧的结论：**`CimDispatchController` 用的正是这种写法，而它全仓无测试、默认关闭（`cim.api.dispatch.enabled=false`）、无任何调用方**——该「规避扫描」的写法在本仓**从未被 HTTP 级验证过**。
⚠️ 同时更正 §29 / `PROCESS.md` 里「bootstrap 5/5 证明分发入口未被宿主扫描」的表述：`BootstrapAssemblyTest` 实际只断言 `cim-system` 的仓储与控制器被扫描到（第 42–57 行），**未涉及 dispatch**。这句话不成立。

- **结论**：本轮**不动**这三处。正确做法不是换注解，而是先把该写法补上一个「真实上下文 + HTTP 打通」的测试，再逐个迁移；否则等于用未验证的模式替换正在工作的实现。
- **影响面（当前为潜在风险，未触发）**：`iam` / `rms` / `approval` 的 `scanBasePackages` 都是具体包（不含 `com.cim.i18n` / `com.cim.jpa` / `com.cim.notify`），靠 imports 装配，故三 ap 无影响；`cim-bootstrap` 只引入了 `cim-jpa-starter`，是最可能第一个踩到的宿主。

- **验证**：三应用 `tsc --noEmit` 零错；`vite build` 通过（9.85 / 9.96 / 9.75s）；IAM 渲染契约 **33/33**；后端回退后 `SqlLogAdminControllerTest` 4/4、`NotifyEndToEndTest` 1/1 绿。

## 2026-11-12（续 3）组件能力缺口：三处签名太窄 + 六类样式三份重复

前两轮分别按「具名 statusTone 函数」「字符串字面量类名」统计；本轮换判据：**把「页面为什么绕开组件」当成组件缺陷来查**，并做逐条规则文本比对找重复样式。

### 34.1 三处「组件接不住这个用法」（已补签名）

| 现象 | 处数 | 缺口 | 修法 |
| --- | --- | --- | --- |
| `<label className="check"><CimSwitch/>{文案}</label>` | 4 | `CimSwitch` 只有随状态变化的 `onText`/`offText`，没有静态说明文案 | 增 `label`/`labelKey` → `.cim-switch-row` |
| `<label className="check"><input type="checkbox"/>` | 2 | `CimChoice` 只收 `options[]`，单布尔要绕成 `options=[{value:'x'}]` | 新增 `CimCheckbox` |
| `<div className="empty" style={{...}}>` | 4 | `EmptyState` 无 `className`/`style` | 增透传（`className` 合并而非覆盖） |

- ⚠️ `CimSwitch` 的静态文案渲染为 `<span>` 而不是 `<label>`：antd `Switch` 是 `<button>`，`<label>` 包裹**不产生**「点文字切换」，写 label 只有样子没有效果。
- ⚠️ `PermTree` 里两个复选框是**网格单元格**（无配对文案），套不进 `CimCheckbox`，改用平台类 `.cim-check`（与组件内部同一份画法）。
- 这三处印证 §33 的规律：**替换受阻先问「组件能不能表达」**，否则「改用组件」根本改不动。

### 34.2 六类样式三份重复（已上提）

逐条规则文本比对（去注释/去空白）后，三份全等才动：`.hint`（15 处）、`.cell-actions`（26 处）、`@media 900px` 外壳块、`@media 640px` 外壳块、`.grid-2/.grid-3` 收窄断点、`.scrim`。

- ⚠️ **`.scrim` 是反向缺口**：`ConsoleShell` 一直渲染 `<div className="scrim">`（移动端抽屉遮罩），但 `shell.css` 从无这条规则——样式只散在三个 ap 里。新建 ap 漏抄的表现是「抽屉打开后点空白关不掉」。**组件渲染的类名，样式必须在组件包内。**
- 结果：`styles.css` 行数 iam 486→**182**、rms 889→**502**、approval 1679→**1242**（合计 3054→1926，−37%）。
- 顺带清掉两处「同一 ap 内两份同名规则」：rms 的 `.login-card` 同时存在旧登录页版（含 `.lead`/`form`/`button[type=submit]`）与登录屏版，前者是删登录页时的残留；approval 删完登录块后留下 `.login-brand .logo` 与裸 `.modal` 两条孤儿规则。

### 34.3 登录页有三套实现（未动，记录在案）

| 实现 | 是否生效 |
| --- | --- |
| IAM 服务端内联 HTML+CSS（`SsoLoginPageController`，含 900px 断点） | ✅ 唯一实际渲染 |
| iam / approval 的 `.login-*` | ❌ 两 ap 无登录页，纯死代码（**已删**） |
| rms `LoginPage.tsx`（`.login-screen` 另一套设计） | ✅ 仅 rms 准入失败场景 |

服务端那份是手抄 SPA 令牌（注释自述「复刻 styles.css」），改一边不同步。属**单一登录页**的固有代价，要根治需把登录样式抽成独立 css 由服务端 `<link>`——独立决策，未动。

### 34.4 明确不提取（负向清单，写进审计文档 §8.4）

手写 `form-grid` 包 `CimField` 子树（1 处，`FormGrid` 是受控声明式表单接不住）、手写 `<dl class="meta">`（9 处，value 里嵌组件）、两套时间线（rms `.timeline*` vs approval `.detail-timeline*`，类名结构都不同）、`table.data` 遗留表格样式（rms 仍有 1 处手写 k/v 表在用）。

### 34.5 方法坑：死类名判定必须剔注释

本仓大量迁移说明写着「手写 `.modal-backdrop` → `CimModal`」。不剔注释，`.modal*`/`.drawer*`/`table.data` 会被判成活代码——本轮先踩了一次（误判 30 条规则为「仍在使用」）。剔注释后判据才可用；即便如此，尾随在代码行后的 `//` 注释仍会污染，**逐类确认**比批量删安全。

### 34.6 验证

三应用 `tsc --noEmit` 零错；`vite build` 通过（9.88 / 10.75 / 12.84s）；IAM 渲染契约 **33/33**。

## 2026-11-12（续 4）统一登录归位：登录页从 Java 字符串搬成静态资源

起因是「登录页应该在 IAM」这句话。查证结果：**它已经在 IAM 了**（`GET /login`，8081 直出，全仓唯一），
但落位方式是最差的一种——255 行 HTML+CSS+JS 塞在 `SsoLoginPageController` 的 `"""` 文本块里。
本轮改的是**形态**，不是归属。

### 35.1 登录页：Java 文本块 → 三件静态资源

| 项 | 改前 | 改后 |
| --- | --- | --- |
| 载体 | `SsoLoginPageController.LOGIN_HTML`（255 行字符串） | `static/login/{index.html 66, login.css 314, login.js 247}` |
| Controller | 373 行（含整页 HTML） | **138 行**（只剩 SSO 会话判定 + 302 + 交回 Resource） |
| 样式 | 内联 `<style>`，30+ 条规则 | 独立 `login.css`，可 lint/格式化 |
| 文案兜底 | zh + en 两份词典（与 i18n 库各自漂移） | 只留 zh-CN；en 走运行时 bundle |
| 缓存 | 无显式头 | `Cache-Control: no-store` |

- 页面仍随 jar 发布（`classpath:static/login/`），**不依赖 nginx 或前端构建产物**——登录是「未认证也要能开」的页面，不能挂在 React 上。这是「零依赖静态页」而非「vite 第二入口」的理由。
- `login.css` 的 `:root` 是 `tokens.css` 的 **17 变量子集副本**（变量名逐字一致）。已在 `tokens.css` 顶部加反向指针，说明这是唯一的受控例外。
- 静态资源缺失会静默表现为「页面裸奔/按钮没反应」，故 `loginPage()` 在请求期显式检查 `LOGIN_PAGE.exists()` 并抛带指引的 `IllegalStateException`。

### 35.2 中继页：5 处手写 → `<SsoRelay>` + `ssoLoginUrl`

原状：iam 的 `LoginEntry` 与 `SsoCallback` 各写一份内联 `style={{...}}`；rms 的 `LoginPage`（106 行，自带 `.login-screen` 品牌卡片）、`SsoCallback`、`BootGate` 各写一份 `.login-screen` 结构；rms 与 iam 各拼一份 `${IAM}/login?app=&redirect=`。

收进 `@cim/shell`（呈现与 URL 拼装不碰 store，与 `guards.tsx` 同口径）：

- `ssoLoginUrl({iamBase, app, callbackUrl})` / `redirectToSso(...)` —— 参数拼装一处；
- `<SsoRelay mark brand brandSub label hint busy>` —— 整页卡片 + 不确定进度条（`busy=false` 时关掉，错误态不该还在转）。

顺带删掉 rms `styles.css` 的 `.login-*` 共 7 条规则（502 → **464** 行）。

### 35.3 改的过程中撞见的三个真坑

1. **rms 中继页的品牌名是白字白底，看不见。** `.brand-title` 在 `shell.css` 里是为深色侧栏定的 `color:#fff`，rms 却把它放进白色 `.login-card`。`<SsoRelay>` 改用自有的 `.sso-relay-name`，配色显式声明——**组件的类名不能借用另一套配色上下文**。
2. **登录页的 `.caps-hint` 是死规则**，且 `color: var(--warn)` 里的 `--warn` 在页面 `:root` 从未定义；文档还写着「Caps Lock 提示已实现」。已删规则，并在 iam web README 里把这条标注为「未实现」，而不是继续留着假描述。
3. **`iam.login.lead` 写着「进入 IAM 控制台」**——这页是所有 ap 共用的，RMS 用户会读到自己「要进 IAM 控制台」。改为中性的「使用统一身份继续」。

### 35.4 顺带修掉一个死路

rms 准入失败时，页面渲染的按钮文案是 `t('rms.login.redirecting')`（「正在跳转至统一身份认证中心…」），点击后**重跑同一段 SSO 中继**——IAM 侧会话还在，会拿同一枚令牌再弹回 `/sso`，正是代码注释里反复警告的无限往返。现改为「改用其他账号登录」：先 `POST /api/v1/logout`（该端点两个参数都可空、必清 SSO Cookie），再回登录页。准入没开通就重试是必然失败的动作，不该做成按钮。

### 35.5 验证

- 后端 `SsoSessionSharingTest` **2/2 绿**（原 1 例 + 新增 `loginPageAssets_areServedAndPageIsNotCached`，钉住三件资源可访问与 `no-store`）；`ApiContractVerifier` 28 控制器 / 109 映射 / 2 例外，启动期通过。
- 前端 iam / rms `tsc --noEmit` 零错，`vite build` 均通过（12.0 / 12.6s）。
- 登录页实机截图（1440×900 双栏 + 窄屏单栏）：布局正确、i18n 兜底生效、`app=RMS` 时 lead 显示「登录以访问 RMS」。
- ⚠️ 截图踩坑：Chrome headless 的**最小窗口宽度约 500px**，`--window-size=430` 会按 ~500 布局再裁切，看起来像横向溢出。判定用同源 iframe 探针读 `documentElement.scrollWidth` 才准（430 与 360 下均等于视口宽，无溢出）。

## 2026-11-12（续 5）approval-ap 接入统一登录：顺手挖出一个启动即崩的共有缺陷

上一轮结尾留了个问题：「approval 没接统一登录，要不要做」。本轮做完，并在实测中撞到一个
**两个 ap 共有的、只在认证开启时才暴露的启动崩溃**——也就是说这条链此前从未真正跑起来过。

### 36.1 设计：通道由后端配置决定，不由构建期常量决定

approval 的后端其实早就准备好了两条通道：`CurrentUserResolver` 在 `cim.auth.enabled=true`
时只认 JWT（并显式忽略前端传的 userId/actor，防伪造代办），`false` 时才用查询参数。
缺的只有前端：顶栏常驻「身份切换」、登出只清 localStorage、没有 `/login` 与 `/sso`。

新增 `GET /api/v1/approval/me`，除档案外多回一个 `authEnabled`。前端 `main.tsx` 在**挂载前**
打这一枪，据此选通道：

| 返回 | 通道 | 前端行为 |
| --- | --- | --- |
| 401 | 统一登录·未登录 | 落 `/login` → 整页中继 IAM `GET /login?app=APPROVAL` |
| 200 + `authEnabled:false` | dev 演示 | 不设登录门，顶栏保留身份切换（查询参数标识操作人） |
| 200 + 档案 | 统一登录·已登录 | 档案写入 `identityStore`，顶栏显示用户块，登出走 IdP 吊销 |

> **为什么不做成 `import.meta.env` 常量**：同一份前端产物要能同时跑「带 IAM」与「无 IAM 演示」
> 两种后端配置。写成构建期常量，就得为每种部署各打一次包，且灰度期两份产物并存时行为不可控。

前端只加「怎么拿身份」，不改「身份放哪」：`identityStore` 仍是全应用唯一的「我是谁」，
十余处页面（待办/已办/我发起/详情/重送/定义/企微回调）**一行没改**。

### 36.2 三个容易漏的细节（都是「不报错但不对」型）

1. **SSE 与附件下载带不了 Authorization 头**：走 `?token=`。`ApprovalSecurityConfig` 的
   `/api/**` 链本就有 `?token=` → Bearer 的还原逻辑，`/sys/notify/stream` 另有专门一条链
   （`BearerTokenQueryParamFilter`）。前端 `notificationsStreamUrl()` / `attachmentDownloadUrl()`
   按通道切凭据载体，dev 态仍用 `?userId=&tenantId=`。
2. **租户回落 `default`**：审批流的租户是**流程变量**，存量实例全以 `default` 起；
   JWT 的 `tenantId` 可能为空，原样传 null 会表现成「登录成功但一条待办都看不到」。
3. **落盘令牌被服务端拒绝时首帧会误放行**：`bootstrap()` 拿到 401 时必须顺手清 token，
   否则 `ProtectedRoute` 凭它放行一帧，每个请求再各自 401 一次、各弹一次跳转。

### 36.3 真坑：`addFilterBefore` 的锚点用了自定义过滤器 → 认证一开就启动失败

以 `--cim.auth.enabled=true` 启动 approval，直接刷栈：

```
BeanInstantiationException: Factory method 'notifySecurityFilterChain' threw exception
  The Filter class com.cim.auth.filter.JwtAuthenticationFilter does not have a registered order
```

`addFilterBefore(filter, anchor)` 的锚点必须是 Spring Security **注册表里有顺序**的过滤器。
写成平台自定义的 `JwtAuthenticationFilter.class` 必然抛异常——它只是本平台的 Bean。
**rms 的 `RmsSecurityConfig` 是同一段代码**，而 rms 的 `cim.auth.enabled` 默认就是 `true`。

实测确认：把 rms 的该文件还原到 HEAD 版本启动，报同一个异常；
改成 `UsernamePasswordAuthenticationFilter.class` 后两个 ap 都能起（两次 `addFilterBefore`
指向同一锚点时按调用顺序插入，`bearer` 仍先于 `jwt`，`?token=` 还原逻辑不受影响）。

> **这条推论比 bug 本身重要**：这两个 ap 的「认证态」从未以 `enabled=true` 跑过一次
> （单测都跑 `enabled=false`，dev 也是关的）。凡是只在这条分支上的代码，此前都只是「看起来有」。
> **仍未做**：认证开启态的启动冒烟进 CI——本轮只靠手工起服务验证（见 §36.5），下次改这条链仍可能重犯。

### 36.4 顺带回收：DEV 令牌区样式上提

`rms` 登录中继页的「粘贴 JWT 进入」区块有两条样式（`.dev-token*`）。approval 需要同一块，
按「跨 ≥2 应用且逐字节相同才上提」的判据改名为 `.sso-relay-dev*` 收进 `@cim/ui/shell.css`，
rms 同步改用新类名（`styles.css` 再减 15 行）。**组件渲染的类名，样式必须在包内**——同 `.scrim` 的教训。

### 36.5 验证

| 项 | 结果 |
| --- | --- |
| approval 后端全量 | **12/12 绿**（新增 `ApprovalMeControllerTest` 2 例：principal→DTO 映射、dev 态只回开关） |
| `ApiContractVerifier` | 启动期通过（`/me` 返回 `Result<T>`，不占 `@RawResponse` 例外） |
| approval `tsc --noEmit` / `vite build` | 零错 / 通过（9.9s） |
| rms `tsc --noEmit` / `vite build` | 零错 / 通过（10.3s） |
| 实测认证态（`--cim.auth.enabled=true`，端口 18085） | `/me`=401、假令牌 `/me`=401、`/definitions`=401、SSE=401；启动成功 |
| 实测 rms（默认认证开，端口 18084） | 启动成功，`/api/v1/rms/me`=401 |

**已知边界（未修）**：企微免登（`/wecom/callback`）在认证态下不可用——它的链接由企微内置浏览器
直接打开、不带 JWT，而后端 `/api/**` 链统一要求令牌，`verifyWeComToken` 必然 401。
dev 通道（认证关闭）不受影响。要打通需给该端点单独 `permitAll` + 自行校验签名令牌，
属独立改动，本轮不做，已写进前端路由注释。

> ℹ️ 当时 `cim.auth.enabled` 留的是 `false`；本轮已改为默认 `true`（见 §37）。
> 本地跑集成测试仍然要 `-Dcim.notify.cluster.enabled=false`（无 Redis）。

## 2026-11-12（续 6）认证开关默认开启：端到端跑一遍，挖出五个「认证态从未成立」的断链

上一轮的结论是「approval 接入统一登录完成，开关留 `false`」。本轮把开关改成默认 `true`
（与 rms 同口径），并以**真 IAM（H2）+ 真登录 + 真令牌**跑通全链路。结果是：开关一开，
五个环节逐一暴露——它们全部只在 `cim.auth.enabled=true` 时才走到，而此前从未有人走到过。

### 37.1 准入：IAM 侧根本没登记 approval-ap

`BootstrapAdminRunner` 只按 `cim.iam.auth.bootstrap.apps` 登记接入码并授予首管理员准入。
该列表里只有 `rms-ap`，没有 `approval-ap`。于是 admin 的令牌 `apps` 不含 `approval-ap`，
approval 的准入过滤器直接 403——**登录成功，但进不去**（`BootstrapAdminRunner` 的 javadoc
自己就警告过这种「两端登录页互相弹跳」）。已在两份 IAM 配置里补登记。

> 这一条不是代码 bug，是**部署配置缺项**。它说明「新增一个 ap 接入统一登录」至少有两处
> 要改：ap 侧的 `admission.app-code`，以及 IAM 侧的 `bootstrap.apps`。缺任一处都表现为 403。

### 37.2 业务域：`SecurityGuard` 认的权威格式，IAM 从来不发

`deriveFromAuthentication()` 只认 `app:<appCode>` 前缀，而 IAM 的 `TokenIssuerService` 下发的是
`<appCode>:<ROLE>`（如 `approval-ap:ADMIN`）。`grep '"app:"'` 全仓**无任何产生点**，
于是认证态下可访问域集合恒为空 → 所有带域断言的查询一律 403。

同一处的超管判定写的是 `ROLE_SUPER_ADMIN`，而平台约定（`CimUserPrincipal#isSuper`、
`CimPermissionEvaluator`）认 `SUPER_ADMIN` / `ROLE_SUPER`——两个码都不在 IAM 的签发范围里，
即超管通路在认证态下也从未成立过。

修法：两种前缀都认；`<appCode>:<ROLE>` 仅当「恰好两段且首段形如 `*-ap`」时取首段
（否则会把三段式权限码 `approval:definition:manage` 误判成域 `approval`）；超管三个码都收。

### 37.3 租户被当成业务域用

`ApprovalProcessService` 有 6 处 `assertAppCodeScope(tenantId)`，传的是**实例上的流程变量
`tenantId`**（单租户部署恒为 `"default"`），而域集合装的是 appCode 列表——两个维度。
dev 态因 `allowed = ["*"]` 直接放行，所以这个错位长期不可见；认证态下必然 403。

其中两处还写成 `String.valueOf(vars.get("tenantId"))`，值为 null 时会得到**字面量 `"null"`
作为回调路由键**，一并修掉。现在这 6 处断言的对象改为本 ap 的 appCode
（`ApprovalPermissions.APP_CODE`），语义是「本次操作确实发生在被准入的域内」，
在开放 API（appKey 绑定单域）下仍有实际拦截作用。

### 37.4 管理端点：G1 第 2 层「域管理员」没有落地

`assertPermission` 只做权限码精确匹配。IAM 只授角色组（`approval-ap:ADMIN`）、不授
`approval:definition:manage` 这类细粒度码，于是域管理员点开「流程模板/回调管理/审计」全是 403。
现已把本 ap 的 `ADMIN` 视为域管理员通路；待 IAM 支持「应用角色 → 权限集」后应收敛回按码判定。

### 37.5 前端：`fetchMe` 用裸 axios，从不带令牌 → 已登录用户每次刷新都被登出

`fetchMe()` 为避免「未登录」也被 401 拦截器跳转，用了裸 `axios` 实例——代价是它**不附
Authorization**。认证态下它永远 401，`bootstrap()` 于是判定未登录并 `clear()` 掉落盘令牌，
用户一刷新就回到登录页。修法：裸 axios 保留，但显式带上 store 里的令牌。

> 这个 bug 只有「真的带着有效令牌启动一次」才能发现。单测（`auth.enabled=false`）与
> 靠 curl 打接口的验证都覆盖不到——它出在「前端如何自证已登录」这一步。

### 37.6 通知链路：`/sys` 从来没被代理

两个 ap 的 SSE 都打 `/sys/notify/stream`，而 `vite.config.ts` 只代理 `/api`。dev 下
EventSource 打到 vite 自身 → 404，且它**只静默重连、不报错**，表现为「通知永远不来」。
approval 与 rms 都补了 `/sys` 代理。

### 37.7 验证（全部实跑）

| 项 | 结果 |
| --- | --- |
| 真 IAM（8081，H2）+ 真登录（PBKDF2 派生） | 登录 200，令牌 `apps=[approval-ap, iam-ap, rms-ap]` |
| approval `/api/v1/approval/me` | **200**，`userId=admin`、`authEnabled=true` |
| `/tasks/todo`（曾 403） | **200** |
| 审计查看（需 `approval:audit:view`） | **200**（域管理员通路） |
| 定义部署（需 `approval:definition:manage`） | **400 参数校验**（权限已过，非 403） |
| 跨域 `definitions?appCode=mes-ap`（未授权域） | **403** `code=1404` |
| 篡改令牌 | **401** |
| 前端认证态渲染（真令牌注入 + 同源 iframe） | 落 `/`，顶栏 `admin / tenant:default`，模板数 3 来自真实接口 |
| 经 vite 访问 `/sys/notify/stream` | **401**（已转发到后端，不再是 vite 的 404） |
| approval 后端全量 | **12/12**；IAM 后端全量 **73/73** |
| approval / rms `tsc` + `vite build` | 零错 / 通过 |

### 37.8 仍未做

- ~~**G3 业务域隔离未闭环**~~ → 已在本轮闭合，见 §38。
- **超管通路依赖 IAM 展开**：IAM 目前不签发 `SUPER_ADMIN`/`ROLE_SUPER`，所以「平台超管跨域」
  实际只能靠 `approval-ap:ADMIN` 的域管理员通路生效。
- **企微免登**在认证态不可用（见 §36.5）。

## 2026-11-12（续 7）G3 收口：域只有一个来源

### 38.1 问题的准确形态

原记录写「只做了入口断言，五类查询未过滤」。逐端点核对后是四个不同的问题，混在一句描述里：

| 端点 | 原实现 | 真实缺口 |
|---|---|---|
| `/tasks/{todo,done,initiated}` | 查询条件用调用方传的 `tenantId`，断言传常量 `APP_CODE` | 断言查「你是不是 approval-ap 的人」，查询用「你自报的域」，两条线不相交 |
| `/stats` | 只有 `variableValueEquals("tenantId", …)` | **连断言都没有**，传任意 tenantId 即可跨域读耗时/趋势/驳回率 |
| `/audit`、`/callbacks` | `appCode` 可选，不传走「不限域」分支 | 知道 businessKey 就能读该单跨域全量审计 |
| `/definitions` | 内存按 `d.getTenantId()` 过滤 | 不传 `appCode` 返回全部；`/{id}/xml`、`duplicate`、`suspend` 完全不校验 |

### 38.2 三条设计决定

1. **域 = appCode，不是租户**。租户（`tenantId`）是流程变量，一个租户下可有多个业务方；域是「哪个 ap 的数据」，与令牌 `apps` 同一命名空间。单租户部署里两者取值都是 `default`，所以一直被混用——§37.3 那 6 处 `assertAppCodeScope(tenantId)` 就是这个混淆的产物。
2. **允许集合 = 令牌 `apps` ∪ 令牌 `tenantId` ∪ 共享域 `default`**。只取 `apps` 会把「各 ap 都写 default」的存量部署全线 403（上一轮烧过一次）。
3. **共享域恒放行是显式妥协**。未分域的历史数据全在 `default`；不放行则升级当天存量单据集体消失。代价是该域对所有已准入 ap 可读——真正隔离从「各 ap 以自身 appCode 发起新实例」开始生效。

### 38.3 落点

- `SecurityGuard` 新增 `resolveDomainFilter(requested)`（多值，五类查询唯一来源）与 `resolveDomain(requested)`（单值，写操作与列表域条件）；`assertAppCodeScope` 改为其判定形态。**未指定域时收敛为调用方授权域集合，而不是放开全量**——这是原实现最实质的洞。
- 查询侧：`listTodoPaged` / `listDonePaged` / `listInitiatedPaged` / `StatsService`（含驳回率、审批人负载、待办/超时计数）全部改用 `resolveDomain`；审计/回调改多域 `IN`（新增 `findByBusinessKeyAndAppCodeIn…`、`findByAppCodeIn`）。
- 资源级：`detail`、BPMN `xml`、`duplicate`、`suspend`、`withdraw`、`urge` 按归属域校验，否则 id 枚举可绕过全部列表过滤。
- 写入侧：`start` 先 `resolveDomain(req.tenantId())` 再落库；`duplicate` 的副本沿用源定义的域，否则「复制」会把域私有定义洗成全局共享。
- 前端：`SHARED_DOMAIN` 常量与后端同值；认证态身份与域由 `/me` 决定、`Layout` 只在 dev 通道渲染切换器（本来已如此，本轮补注释与 i18n 文案）。

### 38.4 实跑才暴露的两件事

- **Flowable 的「无租户」是空串不是 null**：`ACT_RE_PROCDEF.TENANT_ID_` 默认 `''`。按 `d.getTenantId() == null` 判全局定义，会把 3 个内置定义全过滤掉 —— 接口 200、返回 `[]`、不报错，控制台空屏。改按 `blank` 判。
- **方法写错会被映射成 400 而不是 405**：`GlobalExceptionHandler` 把 `HttpRequestMethodNotSupportedException` 映射成 `OPERATION_NOT_ALLOWED(2004)` + HTTP 400，文案「当前状态不允许该操作」。用它调 `withdraw`（GET 打了 POST 接口）时看起来像业务状态拦截，实际是方法不匹配——排查时容易跑偏。

### 38.5 验证（真 IAM + 真 JWT，全部实跑）

令牌 `apps=[approval-ap, iam-ap, rms-ap]`、`roles=[approval-ap:ADMIN, …]`、`tenantId=null`。

| 请求 | 结果 |
|---|---|
| `/me`、`/tasks/todo?tenantId=default`、`/stats?tenantId=default` | 200 |
| `/tasks/todo?tenantId=rms-ap` | 200（令牌 apps 确实含 rms-ap，属授权域） |
| `/tasks/{todo,done,initiated}?tenantId=mes-ap` | **403** ×3 |
| `/stats?tenantId=mes-ap`（曾无断言） | **403** |
| `/definitions?appCode=mes-ap` | **403**；不传参数 200 且返回 3 条（`tenantId=''`） |
| `/audit?businessKey=…`（不传 appCode） | 200（收敛本人域，不再全量）；`&appCode=mes-ap` **403** |
| `/callbacks`（不传 appCode） | 200（收敛）；`/callbacks/outbox?appCode=mes-ap` **403** |
| `POST /processes/start` 带 `tenantId=mes-ap` | **403** 无操作权限（三个内置流程均如此） |
| `POST /processes/start` 带 `tenantId=default` → detail → withdraw | 200 → 200 → 200 |
| 后端全量 / 前端 | **19/19**（新增 `SecurityGuardDomainScopeTest` 7 例）；`tsc` 零错 + `vite build` 通过 |

### 38.6 仍未做

- **数据分域**：存量实例全在 `default`，因此「按域隔离」目前只在**通道**上成立；真实数据分域要等第二个业务方以自身 appCode 发起（D13 RMS 回调接线仍未启动）。
- ~~`/callbacks/outbox/stats` 仍是全局计数（无域参数），属运维 KPI，未纳入收敛。~~ → 已于 §39 收敛。
- ~~`E2E` 未覆盖「不传参→只见本域」~~ → 已于 §39 补齐（17/17，含双域造数）。

## 2026-11-12（续 11）认证一开，服务间面就断了

### 39.1 现象

认证改为默认开启后，`verify-security.mjs` 从「11/11 通过」变成 **6 通过 / 11 失败**：失败项清一色 `401`，而且**带合法签名**的请求也 401。日志里没有 `[API-SEC-ALERT]`——说明签名、重放窗口、限流、域绑定全部通过，是被 Spring Security 的授权链挡下的。

也就是说：**开放 API（X-App-Key）这条服务间通道，从未在认证开启的形态下跑过**。此前所有 G9/G3 验证都在 dev 放行态完成，开关一翻，整条面失效。

### 39.2 四段断链（逐段看才看得清）

| # | 断点 | 表现 |
|---|---|---|
| 1 | `ApiAuthFilter` 只写 `RequestScopeHolder`（ThreadLocal 域作用域），**不写 `SecurityContext`** | `/api/**` 链是 `anyRequest().authenticated()` → 判为匿名 → 401 |
| 2 | 它是 `@Component` + `@Order(HIGHEST_PRECEDENCE)` 的**容器级** filter，早于 Security 链执行；而 `SecurityContextHolderFilter` 在链首用 `SecurityContextRepository` **覆盖** SecurityContext | 就算在容器层写了身份，进链也会被清空 → 仍 401 |
| 3 | `CurrentUserResolver` 认证态只认 `CimUserPrincipal` 类型的 principal | 服务身份主体是 appCode → `resolve()` 抛「未认证或令牌缺少用户标识」（发起、审批、撤回全挂） |
| 4 | `assertPermission` 认证态按权限码判定 | 服务身份没有权限码 → 部署、审计、回调管理等端点 403 |

### 39.3 设计：开放 API 的凭据是 appKey，不是 JWT

- **注册位置**：`ApiAuthFilter` 移进 Security 链（`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`，先于 JWT 过滤器），并新增 `ApiAuthFilterRegistration` 用 `FilterRegistrationBean#setEnabled(false)` **关闭容器级自动注册**——不禁用会执行两次，限流配额被双倍消耗，而且第二次仍在链外白跑。
- **身份**：校验通过后写一个最小 `UsernamePasswordAuthenticationToken(appKey, null, [])`，只为满足 `authenticated()`；权威留空，不冒充任何用户。
- **身份来源三分法**（`CurrentUserResolver`）：开放 API → 用调用方传的 `initiator`/`actor`（请求体与查询串已被 HMAC 覆盖，改不动）；认证态 → JWT；dev → 参数。
- **权限**：`assertPermission` 在开放 API 下短路返回——签名/重放/IP/限流/域绑定已经在 `ApiAuthFilter` 做完，appKey 本身即服务级凭证；域隔离不受影响，仍由 `resolveDomainFilter` 断言。

### 39.4 顺带收掉的两处 G3 尾巴

- `/callbacks/outbox/stats` 原为全局计数（多业务方接入后每方都能看到别方成功率与积压量），改为 `resolveDomainFilter` 收敛；仓库里因此失去调用点的 `countByStatus`、`countByActionAndCreatedAtAfter`、`findByBusinessKeyAndAppCodeOrderBySeqAsc`、`listByBusinessKeyAndAppCode` 一并删除（避免留死方法）。
- `getDetail` 内的审批历史原按 `businessKey` 取全量，同一单号被不同业务方复用时会把别域轨迹混进详情页；改为按实例归属域收敛。

### 39.5 验证（同一套脚本跑两种形态）

| 形态 | 结果 |
|---|---|
| 认证开启（默认，真 IAM + 真 JWT） | `verify-security.mjs` **17/17**：部署/发起/待办 200；缺签名/错签名/超重放 → 403；跨域 → 403；不传 `appCode` → 异域审计 **0 条**、本域 **1 条**；回调列表不含异域；KPI 不传参 200 / 传异域 403；限流 3 过 2 拒 |
| 认证关闭（`--cim.auth.enabled=false`） | 同一脚本 **17/17**；另验无凭据直连 `/definitions`、`/tasks/todo`、`/me` 均 200（dev 通道未被破坏） |
| JWT 用户态回归 | `/me` 200、`/definitions` 200、`todo@default` 200、`todo@mes-ap` **403**、`stats@default` 200、`outbox/stats` 200 |
| 后端全量 / 前端 | **23/23**（新增 `OpenApiAuthChainTest` 4 例）；`tsc` 零错 + `vite build` 通过 |

### 39.6 回归的自动化落点

`OpenApiAuthChainTest` 补上了「认证开 + 真发 HTTP」这一层：无凭据 401、合法 appKey 200、跨域 403、错签名 403。它是唯一能抓住本轮 bug 的自动化——单元测试不装配 Security 链，dev 放行态不认证，两边都看不见。

做过**反向验证**：把 `SecurityContextHolder.setContext(ctx)` 注释掉重跑，`openApiPassesAuthenticatedChain` 立刻变红（expected 200, was 401），恢复后转绿。不经这一步，「测试通过」也可能只是没测到。

### 39.7 仍未做

- **认证开 + JWT 用户态的自动化**：需要自签 RS256 + 假 JWKS。`TestTokens` 目前在 `cim-auth-starter` 的 `src/test` 下且未发布 test-jar，approval 侧拿不到；要么把它提到共享测试模块，要么复制一份。
- **`verify-security.mjs` 仍未进流水线**：它覆盖的是「真服务 + 开放 API 全链路」（17 条），目前靠手工起服务跑。

## 2026-11-12（续 12）清账四项：认证态自动化 / E2E 一键入口 / 死代码 / 库内端点装配

### 40.1 认证开 + JWT 用户态终于有自动化了

此前这一层只靠「人工起 IAM + 真登录跑一次」，而它的故障形态全是「不报错但不对」（令牌解析不出 → 待办全空；域判定认错格式 → 全 403）。单元测试（`ApprovalMeControllerTest`）直接往 `SecurityContext` 塞 principal，把验签、准入、域解析整条链都绕过了。

做法：`cim-auth-starter` 发布 **test-jar**（`maven-jar-plugin:test-jar`），把 `it/TestTokens`（自签 RS256 + 假 JWKS）暴露给下游；approval 侧新增 `JwtAuthChainTest` 6 例（无令牌 401 / `/me` 200 取 JWT 身份 / 本域待办 200 / 越域 403 / 篡改签名 401 / `apps` 缺本 ap 403）。不复制一份签发逻辑——两份密钥生成代码漂移时，故障会表现成「令牌验不过」而不是「测试写错了」。

**反向验证**：把 `resolveAllowedAppCodes` 的认证分支临时改成恒返回 `["*"]`，越域用例立刻 `expected 403 but was 200`，恢复转绿。

**顺带修掉一个自造的脆弱测试**：第一版篡改的是 JWT 签名段的**末**字符。RS256 签名 256 字节 = 342 个 base64url 字符，末字符只有 2 位有效——改动可能落在 padding 位，解码出同样的字节，验签照样通过。单跑时随机过一次，全量跑时红了。改为篡改签名段首字符，连跑两次稳定。

### 40.2 E2E 不再靠手工起服务

`verify-security.mjs` 17 条覆盖的是「真服务 + 真签名」全链路，却一直「手工起服务、手工跑脚本」——所以「认证一开服务间面全断」那次是隔了一轮才被发现的。

新增 `business/approval-ap/scripts/e2e-security.sh`：起服务（jar 缺失或源码更新则先打包）→ 轮询 `Started ApprovalApApplication` → 跑脚本 → **无论成败都停进程**（`trap` + `kill -9` 兜底）→ 按 `auth|dev|both` 汇总。端口被占直接退出（连别人的服务跑测试，结论是假的）。实跑：认证态 17/17、dev 态 17/17，端口每次正常释放。

顺带把脚本里写死的 BPMN 绝对路径改成按 `import.meta.url` 定位——写死时换机器报 `ENOENT`，看起来像「流程定义缺失」。

### 40.3 「iam/rms 的 auditTone 逐字节重复」不成立——那份是死代码

原本的计划是把重复的 `auditTone` 上提到平台。核实后发现前提错了：rms 的 `lib/format.ts` 是 iam 那份的**整份拷贝**（72 行逐字节相同），但其中 5 个函数（`auditLabel`/`orgTypeLabel`/`originLabel`/`auditTone`/`fmtRelative`）在 rms **零调用点**，只有 `fmtDateTime` 在用。

更严重的是这些死函数引用的 `iam.*` 译文键在 rms 内置包里**一个都没有**（`grep -c "^  'iam\." = 0`），而缺键兜底是 `humanize`——于是 `fmtDateTime(null)` 在界面上显示的是 **"Dash"** 而不是 "—"。死代码不会报错，但兜底键照样会渲染。

处理：rms 侧删掉 5 个死函数，兜底改用 `rms.common.dash`；**不上提**。判据写进组件审计文档：「逐字节相同 ≠ 都活着——先数调用点，再数 key 是否真的命中」。

### 40.4 库 jar 里的端点到今天才定死

「库里的控制器禁 `@RestController`，替代写法 `@RequestMapping + @ResponseBody` 实测未通过」这条记录一直挂着，但没人知道为什么失败，于是 `CimDispatchController` 用着同一种未验证的写法、默认关闭、零测试。

反编译定因：`RequestMappingHandlerMapping.isHandler()` 在 **Spring Framework 6.0** 起只剩 `hasAnnotation(beanType, Controller.class)`；5.0.8 里的「或 `@RequestMapping`」分支已删。**所以那种替代写法在 Spring 6 上永远 404**，与是否 @Bean 注册无关。`LibraryControllerAssemblyTest` 三个用例把这条钉住（A 形态 200 / B 形态 404 / 未注册 404）。

而 `@Controller` 蕴含 `@Component`，宿主 `scanBasePackages="com.cim"` 一定扫得到——默认态因为 Boot 允许 `@Bean` 覆盖扫描定义而看不出问题，**开关一关就炸**：

```
cim.jpa.sql-log.admin-enabled=false
→ No qualifying bean of type 'java.lang.String' available
   （SqlLogAdminController 构造器第一参是 loggerName）
```

报错完全不提「你关了一个开关」。修法是把同一个 `@ConditionalOnProperty` 写到控制器类上，让扫描阶段直接跳过：`SqlLogAdminController`（sql-log 开关）、`SseController`（notify 开关）。`I18nController` 无开关，天然一致，不动。

守门测试两个层次：`LibraryEndpointScanTest`（bootstrap，关开关真启动一次，断言 Bean 不存在）、`SseControllerScanConditionTest`（notify，扫描候选断言，毫秒级）。**两者都做过反向验证**：去掉类上条件后，前者 `No qualifying bean` 启动失败、后者 `doesNotContain` 立刻红。

### 40.5 验证

| 项 | 结果 |
|---|---|
| approval 后端 | **29/29**（新增 `JwtAuthChainTest` 6 例） |
| 平台侧 | `cim-spring-support` 25/25、`cim-jpa-starter` 43/43、`cim-notify-starter` 13/13（1 跳过）、`cim-bootstrap` 6/6 |
| E2E | 认证态 17/17、dev 态 17/17（一键脚本各跑一次） |
| 前端 | iam / rms `tsc --noEmit` 零错 |
| 反向验证 | 4 处（域隔离、JWT 签名篡改、sql-log 扫描、SSE 扫描），均先红后绿 |

### 40.6 仍未做

- `verify-security.mjs` **已能一键执行，但还没进 CI**（缺流水线定义，不是缺入口）。
- 认证态 JWT 的测试依赖 `cim-auth-starter` 的 test-jar：**消费方构建前必须先 `install` 平台模块**（与「改平台模块必 install」同一条约束），CI 里要保证顺序。

## 通用风险
- ~~本批次 mq / cache / platform 三项为前期会话产物，提交前未做全量回归，存在潜在的编译/测试漂移，建议尽快安排一次整仓 `mvn install` 回归。~~ **已于 2026-10-09 完成整仓回归，全部通过。**
- 仓库根误入的 `视频.lnk` 已于提交 `5ae9bb7` 移除，并在 `.gitignore` 追加 `*.lnk`；`docs/business/mds-ap/server/截图.lnk` 等同类快捷方式现已被忽略，不再纳入版本控制（如需彻底删除本地文件请人工确认）。
