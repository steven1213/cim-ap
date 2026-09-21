# 实现状态与落地路线

> 本文件原属总文档 [`README.md`](../../README.md) 的 §8「实现状态与落地路线」。为便于维护，独立成篇；总文档保留入口链接。

## 8.1 当前实现状态

| 层次 | 内容 | 状态 |
| --- | --- | --- |
| 设计文档 | 总文档 §1–§9、后端文档 §1–§25、前端文档 §1–§13 | ✅ 完成 |
| 后端基础框架 | `platform/server`：父 BOM + 全部模块（cim-core / cim-spring-support / 各 starter / cim-system / cim-business / cim-bootstrap）骨架（pom + 占位类），`cim-bootstrap` 与各业务 ap 的 `server` 含可启动主类 | 🟡 骨架已生成 |
| 前端基础框架 | `platform/web`：Vite + React + TS 脚手架、目录结构、请求层/权限组件/路由/i18n 占位 | 🟡 骨架已生成 |
| 业务层代码 | `business/iam-ap`、`business/mds-ap`、`business/mes-ap` 各自的 `server`（Maven 应用）+ `web`（Vite 应用）骨架 | 🟡 骨架已生成 |
| 基础设施 | `docker-compose.yml`、各工程 `Dockerfile` 已生成；CI 流水线、K8s 编排未生成 | 🟡 部分已生成 |
| 数据库脚本 | Flyway 多目录迁移（含主表 + 历史表成对 DDL） | 🔲 未生成 |

> 文档与状态表中的标记含义：**✅ = 设计已完成**，**🟡 = 代码骨架已生成（占位，功能待填充）**，**🔲 = 仅规划或未落地**；🔲 / 🟡 均不代表功能已实现。

## 8.2 建议落地顺序

依赖驱动的推进顺序，避免返工：

| 阶段 | 交付物 | 依赖的前置设计 |
| --- | --- | --- |
| **P0 骨架** | 父 BOM + `cim-core` + `cim-spring-support` + `cim-bootstrap` + `docker-compose` | 后端 §1 / §2 |
| **P1 持久化** | `cim-jpa-starter`（多库 EMF、`IdGenerator`、`PhysicalNamingStrategy`、Flyway 多目录、租户过滤器） | 后端 §3 / §10 |
| **P2 数据模型** | `Auditable` + 基类族 + `BaseHistoryData` + `@History` 拦截器 + 变更检测 | 后端 §9 |
| **P3 安全** | `cim-auth-starter`（RS256、刷新轮换、数据权限、RBAC）+ 登录接口 | 后端 §5 / §15 / §21.1 / §21.2 |
| **P4 横切能力** | cache / i18n / obs / mq starter、`Result<T>`、全局异常、限流幂等 | 后端 §4 / §7 / §11 / §13 / §14 / §16 |
| **P5 效率工具** | `cim-gen-starter` 代码生成器（**P2 每实体一历史表的落地保障**） | 后端 §25 / §9.8 |
| **P6 前端底座** | 请求层 + 守卫 + 权限 + i18n + 主题 + 通用 CRUD 页面 | 前端 §1–§8 |
| **P7 生产化** | 部署编排、灰度、监控告警、数据归档作业 | 后端 §20 / §23、前端 §13 |

> 后端底座（`platform/server`）的**模块/任务级详细计划**（里程碑 M0–M7、任务拆解、验收 DoD、风险）见[后端开发计划](../platform/server/plan.md)；对应的**实现级详细设计**见[后端详细设计](../platform/server/design.md)。

## 8.3 版本演进规划

| 阶段 | 内容 |
| --- | --- |
| 一期 | 本框架底座 + RBAC + 通用 CRUD（当前目标） |
| 二期 | 代码生成器、工作流引擎、文件中心 |
| 三期 | 多租户 SaaS 化、灰度发布、可观测平台 |
| 业务 | 在 `cim-business` 中孵化 MES / EAP / SPC 模块 |
