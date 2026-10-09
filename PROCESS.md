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
| IAM 统一登录 + 管理面 + 前端控制台 | `business/iam-ap/server` + `business/iam-ap/web` + `cim-auth-starter` + `cim-spring-support`(auth 码) | ✅ 完成 | iam-ap 31/31 绿（含 AdminSecurityTest×4 + BootstrapAdminTest×1 + LocalCredentialServiceChangePasswordTest×2）；web `tsc -p tsconfig.json` + `vite build` 全绿（113 模块）；platform 27/27 绿 | `feat(iam): 落地管理面鉴权/首管理员引导/当前用户端点/自助改密；feat(iam-web): 落地登录门户与管理后台` |
| MDS 主数据设计文档 | `docs/business/mds-ap/**` | ✅ 完成 | 两轮文档评审收口 | `docs(mds): 补全 MDS 主数据设计文档集并完成评审收口` |
| cim-mq-starter | `platform/server/cim-mq-starter/**` | ✅ 完成 | 2026-10-09 整仓 `mvn install` 13 模块全绿（含本模块，含其单测） | `feat(mq): 落地 cim-mq-starter 统一消息抽象与韧性/幂等/Outbox` |
| cim-cache-starter | `platform/server/cim-cache-starter/**` | ✅ 完成 | 2026-10-09 整仓 `mvn install` 13 模块全绿（含本模块，含其单测） | `feat(cache): 落地 cim-cache-starter 多级缓存与集群能力` |
| platform 底座(server) | `cim-system` + `cim-jpa-starter` + `cim-bootstrap` + 根 `pom.xml` + `docs/platform/server/README.md` + `docs/repo/*` | ✅ 完成 | 2026-10-09 整仓 `mvn install` 13 模块全绿；`cim-system` 16 测(1 跳过)+`cim-bootstrap` 5 测冒烟全绿 | `feat(platform): 落地 cim-system 域模块、Flyway 多目录与 bootstrap 装配` |

## 审计索引
- 详见 [AUDIT.md](./AUDIT.md)（按模块记录范围、验证证据、风险与待办）。

## 说明
- 本批次为历史多会话工作的集中收口：IAM 三里程碑在本会话已完成并验证；MDS 文档集、cim-mq-starter、cim-cache-starter、platform 底座为前期会话落地、本次统一提交。**2026-10-09 已完成整仓回归**（platform 13 模块 `mvn install` 全绿 + iam-ap 29/29 绿，新增管理面鉴权 `AdminSecurityTest`×4 与首管理员引导 `BootstrapAdminTest`×1），mq/cache/platform 三项「待回归」标记已全部解除（见 AUDIT.md「2026-10-09 全量回归记录」）。**2026-10-09 后续增量**：`/me/password` 改密接口由收明文口令改为收客户端第一层派生的 `clientHash`（与登录一致），新增 `LocalCredentialServiceChangePasswordTest`×2 → iam-ap 31/31 绿。
- IAM 前端控制台（`business/iam-ap/web`，React18+TS+Vite）本次新建并打通：登录门户（第一层 PBKDF2）+ 概览/应用与准入/令牌踢人/自助改密。`tsc -p` 类型检查与 `vite build` 均通过。
- 后续每次增量提交继续遵守上方纪律并更新本表。
