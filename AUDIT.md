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

## 2026-10-09 全量回归记录
- **范围**：整仓（platform 13 模块 + iam-ap/server）。
- **命令**：`/tmp/mvnx.sh install`（platform）→ `/tmp/mvnx2.sh <iam-ap/server> test`。
- **结果**：
  - platform：`BUILD SUCCESS`，13/13 模块全绿（含 `cim-mq-starter` / `cim-cache-starter` / `cim-system` / `cim-bootstrap` 等全部子模块）。
  - iam-ap：回归当时 `29/29` 测试全绿，`BUILD SUCCESS`（其后管理面 +4 → 29/29，改密 clientHash +2 → 31/31，改密强制作废旧会话 +3 → 36/36，管理控制台 +5 → **当前 41/41**）。
- **结论**：前期会话落地的 mq / cache / platform 三项经整仓回归确认无编译/测试漂移，原「待回归」标记全部解除。

## 通用风险
- ~~本批次 mq / cache / platform 三项为前期会话产物，提交前未做全量回归，存在潜在的编译/测试漂移，建议尽快安排一次整仓 `mvn install` 回归。~~ **已于 2026-10-09 完成整仓回归，全部通过。**
- 仓库根误入的 `视频.lnk` 已于提交 `5ae9bb7` 移除，并在 `.gitignore` 追加 `*.lnk`；`docs/business/mds-ap/server/截图.lnk` 等同类快捷方式现已被忽略，不再纳入版本控制（如需彻底删除本地文件请人工确认）。
