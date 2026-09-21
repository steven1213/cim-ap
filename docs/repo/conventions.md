# 开发规范

> 本文件原属总文档 [`README.md`](../../README.md) 的 §7「开发规范」。为便于维护，独立成篇；总文档保留入口链接。

## 7.1 通用约定

| 规范项 | 约定 |
| --- | --- |
| 包命名 | 反向域名 `com.cim.{module}.{layer}`（如 `com.cim.system.domain`） |
| 接口前缀 | `/api/{module}/{resource}`；版本演进走 URI（后端 §17） |
| 时间类型 | Java 侧一律 `LocalDateTime`（**禁用 `Date` / `Calendar`**），UTC 存储、前端本地化展示 |
| 标识符 | Java / TS 均小驼峰；常量全大写下划线 |
| 提交信息 | `type(scope): 描述`（feat / fix / docs / refactor / test / chore） |
| 分支策略 | `main` / `develop` / `feature/*` / `release/*` / `hotfix/*` |
| 代码审查 | PR + 至少 1 人 Approve + CI 全绿（含 ArchUnit 架构守卫） |

## 7.2 编码强制约束

| 领域 | 必须 | 禁止 |
| --- | --- | --- |
| **主键** | 应用层时间有序 ID（雪花 / UUIDv7），由 `IdGenerator` 统一注入 | `@GeneratedValue(IDENTITY)` / 数据库自增（跨库不可移植，后端 §3） |
| **分层依赖** | `Controller → Service → Repository` 逐层调用 | Controller 直接注入 Repository；Service 互相循环依赖 |
| **对象边界** | Controller 出入参一律 DTO（`MapStruct` 转换） | Entity 直接作为请求/响应体（导致字段越权暴露，后端 §2） |
| **事务** | `@Transactional` 标注在 Service 方法，边界内只做 DB 操作 | 事务内发起远程调用 / MQ 发送 / 大循环（长事务拖垮连接池，后端 §12） |
| **异常** | 业务失败抛 `BizException(BizCode, args)`；由全局处理器统一转 `Result` | `e.printStackTrace()`、吞异常、`try-catch` 后返回 null |
| **日志** | SLF4J + MDC（`traceId` 贯穿）；敏感字段脱敏后才打印 | `System.out.println`；明文打印口令/令牌/身份证号（后端 §14 / §15） |
| **JSON 字段** | 统一走 `@Convert` 存 TEXT/CLOB | 使用数据库方言原生 JSON 类型（跨库不可移植，后端 §3） |
| **DDL** | 一律 Flyway 迁移脚本，`ddl-auto: validate` | `ddl-auto: update`（生产会误删列，后端 §3） |
| **历史实体** | 主表 `ALTER` 必须同一迁移文件带对应的 `ALTER {X}_hist` | 只改主表不改历史表（schema drift，后端 §9.8） |
| **前端存储** | `localStorage` 仅存 `lang` / `theme` 白名单 | 持久化 token 或权限数据（XSS 直接失守，前端 §5 / §10） |
| **前端权限** | `<Perms>` 无权限时**不渲染** | 用 CSS `display:none` 隐藏（DOM 仍在，可被绕过，前端 §4） |
| **口令传输** | 前端「一次摘要 + 动态公钥加密」，`nonce` 防重放；**且必须有 HTTPS** | 明文 POST；前端硬编码公钥（后端 §5.1 / 前端 §3） |
| **口令派生** | 服务端混入 `pepper` + 不可变 `userId` 做**二次派生**后才落库，并用 `MessageDigest.isEqual` 定长比对 | **把前端传来的摘要直接入库**（拖库即可回放登录，等于没加密；后端 §5.1） |
| **文案国际化** | 所有面向用户文案走 i18n `t()`；缺失时四级兜底至**默认中文** | 组件内硬编码中文；**把未翻译的裸 key 显示给用户**（前端 §6） |

## 7.3 数据库对象命名

| 对象 | 规范 | 示例 |
| --- | --- | --- |
| 表 | 小写蛇形，业务前缀分组 | `equipment_def`、`sys_user` |
| 历史表 | 主表名 + `_hist`（状态流水为 `_state_log`） | `equipment_def_hist`、`equipment_state_log` |
| 字段 | 小写蛇形（全局 `PhysicalNamingStrategy` 统一转换） | `tenant_id`、`op_time` |
| 索引 | `idx_{表名}_{字段}` | `idx_equipment_def_code` |
| 唯一约束 | `uk_{表名}_{字段}`，软删除需含 `deleted` 列 | `uk_equipment_def_code_tenant_deleted`（后端 §9.7） |
