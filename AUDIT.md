# AUDIT.md — cim-ap 审计报告

> 按模块记录变更范围、验证证据、关键修复、风险与待办。配合 [PROCESS.md](./PROCESS.md) 的检查点表使用。

## 2026-10-08 批量提交（建立 PROCESS 机制 + 5 模块收口）

### 1. IAM 统一登录（M-login / M-refresh / M-lockout）
- **范围**：`business/iam-ap/server`（token/app/auth/config 包 + 迁移 V1–V4）、`cim-auth-starter`（SPI 校验链：版本/黑名单检查器 + 过滤器插入）、`cim-spring-support`（`BizCode`/`BizException` 新增 `ACCOUNT_LOCKED`，`GlobalExceptionHandler` 映射）。
- **验证（已跑通）**：
  - iam-ap：`24/24` 测试全绿（M-login 15 + M-refresh 3 + M-lockout 6）。
  - platform：`27/27` 测试全绿（cim-auth-starter 含新增 `IamTokenVersionChecker`/`IamTokenBlacklistChecker` 各 4 测 + 集成测试）。
- **关键修复 / 设计决策**：
  - `SecurityAutoConfiguration` 装配顺序陷阱：默认 `acceptAll` Bean 原带 `havingValue=""` 条件，配真 URL 时仍命中 → 改为具体 IAM 实现声明在前、默认仅 `@ConditionalOnMissingBean`（版本/黑名单两处同修）。
  - `jti` claim 接入 + 按 jti 黑名单跨模块契约，经 `SessionIntegrationTest` 真实端口端到端验证（IAM 拉黑 → 验证侧 401）。
  - 刷新令牌轮转（仅存 SHA-256 散列、防重放）+ 用户级 `ver` bump 兜底，与黑名单正交。
  - 登录失败锁定：滑动窗口计数，达阈值锁定，成功清零；预检不区分用户是否存在（防账号枚举）。
- **风险 / 待办**：AD 真实联调、前端登录页 + 第一层派生 JS、`cim-iam-ap` web 端、刷新/锁定 TTL 灰度与监控。

### 2. MDS 主数据设计文档集
- **范围**：`docs/business/mds-ap/server/**`（39 篇设计文档 + 新增 `operating-profile-design.md`、蓝图/backlog/README）。
- **验证**：两轮文档评审收口（非代码测试）。
- **风险 / 待办**：文档对应落地代码尚未开始；`process-flow-design.md` 等为设计态，需与实现对齐。

### 3. cim-mq-starter（统一消息抽象）
- **范围**：`platform/server/cim-mq-starter/**`（outbox / resilience(Resilience4j) / idempotent / provider(Pulsar·Kafka·RabbitMQ) / listener / observability / serialize / spi / autoconfigure + 测试）。
- **验证**：**本次未重跑测试（待回归）**——属前期会话落地、本轮统一提交。
- **风险 / 待办**：需补 provider 级集成测试，确认 outbox→broker 投递语义、幂等去重、乱序处理；`cim-mq-starter-usage.md` 同步纳入。

### 4. cim-cache-starter（多级缓存与集群）
- **范围**：`platform/server/cim-cache-starter/**`（cluster / config / guard / multi / support + resources + test）。
- **验证**：**本次未重跑测试（待回归）**。
- **风险 / 待办**：集群模式一致性、guard 限流/击穿防护需补测试。

### 5. platform 底座（server 侧其余模块）
- **范围**：`cim-system`（域模块：rbac/role/user/permission/menu/dict/log/autoconfigure 等，删除 `CimSystemMarker` 占位）、`cim-jpa-starter`（Flyway 多目录 locations 定制、`DbCapabilities` 方言派生）、`cim-bootstrap`（装配 + `application.yml.example`）、根 `pom.xml`、平台文档 `docs/platform/server/README.md`、`docs/repo/{roadmap,structure}.md`。
- **验证**：**本次未重跑测试（待回归）**。
- **风险 / 待办**：`cim-system` 域模块需确认与 `cim-bootstrap` 装配冒烟通过；Flyway 多目录追加顺序需回归。

## 通用风险
- 本批次 mq / cache / platform 三项为前期会话产物，提交前未做全量回归，存在潜在的编译/测试漂移，建议尽快安排一次整仓 `mvn install` 回归。
- 仓库根存在误入的 `docs/business/mds-ap/server/截图.lnk` 快捷方式文件，**本次不纳入提交**，需人工确认是否删除。
