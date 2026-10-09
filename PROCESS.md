# PROCESS.md — cim-ap 研发流程与里程碑检查点

> 本文件是 cim-ap 仓库的研发流程纪律与里程碑检查点台账。
> **每次提交（commit）必须同步更新本检查点表与审计报告（[AUDIT.md](./AUDIT.md)）。**
> 严禁 `git add -A` / `git add .` 通配暂存；一律按模块 / 逻辑单元显式 `git add <path>`。

## 提交纪律
- 禁止 `git add -A` / `git add .`；必须按模块显式指定路径，避免把不相关改动混进同一笔提交。
- 单一职责：一个 commit 只承载一个逻辑模块的变更。
- 提交信息遵循 Conventional Commits：`feat` / `fix` / `docs` / `chore` / `refactor` / `test`，并带作用域，如 `feat(iam): ...`。
- 每次 commit 同步本文件「里程碑检查点」表对应行 + `AUDIT.md` 对应条目。
- 不提交临时产物 / 个人快捷方式（如 `*.lnk`）/ 构建日志。

## 里程碑检查点

| 里程碑 | 范围 | 状态 | 验证 | 本次提交 |
|--------|------|------|------|----------|
| IAM 统一登录 + 管理面 + 前端控制台 | `business/iam-ap/server` + `business/iam-ap/web` + `cim-auth-starter` + `cim-spring-support`(auth 码) | ✅ 完成 | iam-ap **41/41** 绿（含 AdminSecurityTest×4 + BootstrapAdminTest×1 + LocalCredentialServiceChangePasswordTest×2 + LocalTokenVersionCheckerTest + ChangePasswordInvalidatesSessionTest + **AdminConsoleTest×5**）；web `tsc -p tsconfig.json` + `vite build` 全绿（126 模块，含管理控制台 IA v3 与 UI v2）；platform 27/27 绿 | `feat(iam): 管理控制台端点+审计流水；feat(iam-web): 菜单注册表驱动的管理控制台 IA` |
| MDS 主数据设计文档 | `docs/business/mds-ap/**` | ✅ 完成 | 两轮文档评审收口 | `docs(mds): 补全 MDS 主数据设计文档集并完成评审收口` |
| cim-mq-starter | `platform/server/cim-mq-starter/**` | ✅ 完成 | 2026-10-09 整仓 `mvn install` 13 模块全绿（含本模块，含其单测） | `feat(mq): 落地 cim-mq-starter 统一消息抽象与韧性/幂等/Outbox` |
| cim-cache-starter | `platform/server/cim-cache-starter/**` | ✅ 完成 | 2026-10-09 整仓 `mvn install` 13 模块全绿（含本模块，含其单测） | `feat(cache): 落地 cim-cache-starter 多级缓存与集群能力` |
| platform 底座(server) | `cim-system` + `cim-jpa-starter` + `cim-bootstrap` + 根 `pom.xml` + `docs/platform/server/README.md` + `docs/repo/*` | ✅ 完成 | 2026-10-09 整仓 `mvn install` 13 模块全绿；`cim-system` 16 测(1 跳过)+`cim-bootstrap` 5 测冒烟全绿 | `feat(platform): 落地 cim-system 域模块、Flyway 多目录与 bootstrap 装配` |

## 审计索引
- 详见 [AUDIT.md](./AUDIT.md)（按模块记录范围、验证证据、风险与待办）。

## 说明
- 本批次为历史多会话工作的集中收口：IAM 三里程碑在本会话已完成并验证；MDS 文档集、cim-mq-starter、cim-cache-starter、platform 底座为前期会话落地、本次统一提交。**2026-10-09 已完成整仓回归**（platform 13 模块 `mvn install` 全绿 + iam-ap 29/29 绿，新增管理面鉴权 `AdminSecurityTest`×4 与首管理员引导 `BootstrapAdminTest`×1），mq/cache/platform 三项「待回归」标记已全部解除（见 AUDIT.md「2026-10-09 全量回归记录」）。**2026-10-09 后续增量**：① `/me/password` 改密接口由收明文口令改为收客户端第一层派生的 `clientHash`（与登录一致），新增 `LocalCredentialServiceChangePasswordTest`×2 → iam-ap 31/31 绿；② 改密后强制作废旧会话：IAM 注册 `LocalTokenVersionChecker`（顶替 platform 默认 `acceptAll()`，进程内直查库比对令牌 `ver`）+ `changePassword` 入库后 `bump` 版本，新增 `LocalTokenVersionCheckerTest` + `ChangePasswordInvalidatesSessionTest` → iam-ap **36/36 绿**；前端 `ProfilePage` 改密成功后清态跳登录。
- IAM 前端控制台（`business/iam-ap/web`，React18+TS+Vite）本次新建并打通：登录门户（第一层 PBKDF2）+ 概览/应用与准入/令牌踢人/自助改密。`tsc -p` 类型检查与 `vite build` 均通过。
- **2026-10-09 后续增量③（前端 UI 重构 · 工业级控制台 v2）**：初版扁平布局信息密度低、缺工程感，遂重排为「顶部 header + 左侧可收缩菜单 + 内容区 + footer」四段式外壳，并建立面向半导体/CIM 的设计体系 —— 等宽字体承载编码类信息（接入码/用户 ID/角色组/算法/版本）、全局 tabular-nums 数字对齐、小号大写字母标签、紧凑行高提升一屏信息量、浅深双主题（`<html data-theme>` + 内联脚本防闪烁）。新增 `components/Panel.tsx`、`components/Icons.tsx`（内联 SVG，零图标库依赖）、`lib/theme.ts`。登录页精修交互：记住用户名（仅存用户名，口令不落盘）、口令可见性切换、Caps Lock 提示、回车提交、智能自动聚焦。**未改动任何后端契约与 API 调用**。`tsc -p` + `vite build` 全绿（116 模块，CSS 19.8 kB / JS 252 kB）；另以无头 Chrome 实拍 9 张界面截图做视觉回归，修复 2 处渲染缺陷（原生复选框样式、行内表单字段宽度）。
- **2026-10-09 后续增量④（IAM 功能全景梳理 · 管理控制台 IA v3 + 审计/用户/会话后端）**：原菜单为硬编码 4 项（概览/应用与准入/令牌踢人/我的），功能面不完整（无用户管理、无审计、无会话视图）。本次先梳理 IAM（统一身份 + 跨业务准入）应有功能域，再按「用户使用习惯」重构信息架构，并补齐后端能力：
  - **后端新增 `audit` 包**：`AuditEvent`（`audit_event` 表，V5 迁移 h2+mysql）+ `AuditService`（**`REQUIRES_NEW` 独立事务**——审计常出现在「业务即将抛异常」路径，同事务会随之回滚导致失败事件查不到）+ `AuditType` 受控词表 + `AuditEventDto`；在登录/登出/改密/账号增删改/应用注册更新/准入授予撤销/强制下线等路径埋点。
  - **后端新增 `admin` 包**：`AdminOverviewController`（平台态势）、`UserAdminController`（账号 CRUD + 启停/重置/解锁/删除，禁用/重置/删除均 bump 强制下线）、`SessionAdminController`（在线会话 + 强制下线）、`LockoutAdminController`（锁定清单 + 解锁）、`RoleAdminController`（角色组聚合）、`AuditAdminController`（审计查询）、`SettingsAdminController`（只读生效参数）；`AccountLockService` 新增 `listActiveLocks/unlock/find`，`LocalCredentialService` 新增 `listAll/setEnabled/resetPassword/deleteUser/findByUserId`，`RefreshTokenService` 新增 `listActiveSessions`，`AppRegistrationService` 新增 `roleAggregate/listAssignmentsForUser` + `GET /apps/users/{userId}/assignments`。全部类级 `@PreAuthorize("hasAuthority('iam-ap:ADMIN')")`。
  - **前端菜单改为注册表驱动**（`lib/menu.tsx` + `lib/permissions.ts` + `components/RequireAdmin.tsx`）：分组（身份管理/接入管理/安全与会话）+ 按 `isIamAdmin` 过滤 + 路由守卫；新增 7 个页面（用户账号/登录锁定/准入授权/角色组/在线会话/审计日志/系统设置），概览按权限二分（管理员=平台态势 / 普通用户=个人矩阵），应用页拆分为「应用注册」与「准入授权」，令牌踢人并入「在线会话」。
  - **验证**：iam-ap **41/41 绿**（新增 `AdminConsoleTest`×5：用户清单与创建、禁用后登录被拒、锁定列表与解锁、审计与概览/设置/角色组/会话、非管理员 403）；web `tsc` + `vite build` 全绿（126 模块）；另以无头 Chrome 实拍浅/深双主题全页截图做视觉回归，并以 CDP 量化检测横向溢出（`scrollWidth === clientWidth`，KPI 6 卡 192px 不溢出）。
  - 内容区 `.content` 去掉 `max-width` 居中上限（改为铺满 + 14px gutter），消除宽屏两侧大片留白。
- 后续每次增量提交继续遵守上方纪律并更新本表。
