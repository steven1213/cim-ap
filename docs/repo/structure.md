# 仓库目录结构与工程约定

> 本文件原属总文档 [`README.md`](../../README.md) 的 §4「仓库目录结构」。为便于维护，独立成篇；总文档保留入口链接。

> 以下代码目录为**按设计落地的初始骨架**：`platform/` 为基础底座（与 `docs/platform` 同构），`business/` 为业务层（与 `docs/business` 同构，每个 ap 自含 `server`/`web`）。各模块目前仅含占位，状态标记见下表与 [实现状态与落地路线](roadmap.md)。

```
cim-ap
├── README.md                  # 总文档（入口：定位/技术栈/架构/快速开始/质量目标）
├── docs
│   ├── platform/
│   │   ├── server/README.md   # 后端设计文档 §1–§25
│   │   └── web/README.md      # 前端设计文档 §1–§13
│   ├── business/
│   │   ├── README.md          # 业务扩展文档（含 IAM 统一登录/准入说明）
│   │   ├── iam-ap/            # 基础设施型 ap：统一登录授权认证 + 跨业务准入控制
│   │   │   ├── server/README.md
│   │   │   └── web/README.md
│   │   ├── mds-ap/            # 业务 ap：主数据 / 设备主数据
│   │   │   ├── server/README.md
│   │   │   └── web/README.md
│   │   └── mes-ap/            # 业务 ap：制造执行系统
│   │       ├── server/README.md
│   │       └── web/README.md
│   └── repo/                  # 仓库文档（本目录）：结构/约定/路线/FAQ 独立篇章
├── docker-compose.yml         # 本地开发依赖：MySQL / Redis（+ 可选 Kafka / Nginx）
│
├── platform/                  # 【基础底座】与 docs/platform 同构
│   ├── server/                # 后端基础框架（Java Maven 多模块，pom.xml 在此）
│   │   ├── pom.xml            # 父 BOM（依赖版本收敛 / 插件 / enforcer）
│   │   ├── cim-core           # 领域内核：实体 / 领域服务 / Repository 端口（零 Spring 依赖）
│   │   ├── cim-spring-support # Spring 适配：基类 / 工具 / 注解 / AOP 切面
│   │   ├── cim-jpa-starter    # 持久化（多库 EMF / 审计 / 历史 / 多租户过滤）
│   │   ├── cim-auth-starter   # 认证（RS256 / 刷新轮换 / 数据权限）
│   │   ├── cim-cache-starter  # 缓存（多级缓存 / 防穿透）
│   │   ├── cim-mq-starter     # 消息（发件箱中继 / 集成事件）
│   │   ├── cim-i18n-starter   # 国际化
│   │   ├── cim-obs-starter    # 可观测（指标 / 链路 / 日志）
│   │   ├── cim-gen-starter    # 代码生成器（见后端文档 §25）
│   │   ├── cim-system         # 系统域模块：用户/角色/菜单/字典/日志
│   │   ├── cim-business       # 业务域模块（后续按域孵化）
│   │   └── cim-bootstrap      # 启动装配模块（仅 Main + 配置）
│   └── web/                   # 前端基础框架（Node + Vite，package.json 在此）
│       ├── package.json
│       ├── index.html
│       ├── Dockerfile
│       └── src
│           ├── main.tsx / App.tsx # 应用入口与根组件（路由挂载）
│           ├── assets / styles    # 静态资源与 SCSS Design Token
│           ├── layouts / router   # 布局与动态路由 + 守卫
│           ├── services           # Axios 实例 + 拦截器 + 模块 API
│           ├── components         # 通用组件（含 <Perms> 权限组件）
│           ├── hooks              # 自定义 Hooks（usePermission / useTable / useRequest）
│           ├── stores             # Zustand 域切片（auth / user / app / dict）
│           ├── locales            # i18n 资源与初始化
│           ├── types              # 全局 TS 类型（与后端 DTO/Result 对齐）
│           ├── modules            # 业务模块自包含（system / dashboard …）
│           └── utils              # 工具
│
└── business/                  # 【业务层】与 docs/business 同构，每个 ap 自含 server/web
    ├── iam-ap/                # 基础设施型 ap：统一登录 + 跨业务准入
    │   ├── server/            # Maven 工程（pom.xml + IamApApplication + Dockerfile）
    │   └── web/               # Vite 工程（package.json + src）
    ├── mds-ap/                # 业务 ap：主数据 / 设备主数据
    │   ├── server/
    │   └── web/
    └── mes-ap/                # 业务 ap：制造执行系统
        ├── server/
        └── web/
```

#### 顶层目录命名取舍

| 候选方案 | 优点 | 不足 / 风险 | 结论 |
| --- | --- | --- | --- |
| **`server/` + `web/`**（采用） | 简短、日常 `cd` 与 CI 路径都更省；前后端**地位对等**；通配度高，团队成员无需解释即可理解 | `server` 在 Java 语境可能与 Servlet 容器概念纠缠；`web` 字面只指「网页」，将来加移动端时需要再演进 | ✅ **采用**（作为 `platform/` 与 `business/{ap}/` 内部的 server/web 命名；平台/业务分层见下方架构决策） |
| `backend/` + `frontend/` | 语义**最精确**、零歧义；`frontend` 天然涵盖移动端 / 大屏 / 桌面端 | 名称偏长（8 字符），高频路径输入成本高 | 备选 |
| `apps/server` + `apps/web`（Nx/Turborepo 风格） | 未来加更多应用时有扩展位 | 当前只有 2 个应用，`apps/` 属**提前优化**，白白多一层路径 | ❌ 当前不做（第三个应用出现再迁，见下方演进） |
| `services/` + `ui/` | `services` 暗示清晰的微服务边界 | `services` 与当前「单体多模块」现状矛盾；`ui` 内涵偏窄，忽略了请求层 / 权限 / 状态管理等非 UI 部分 | ❌ 不采用 |
| 后端平铺仓库根 + `web/`（原方案） | Maven 命令最短 | 前后端地位不对等；`.mvn/`、`target/`、`pom.xml` 污染仓库根 | ❌ 已废弃 |

> **`server/web` 的两个已知短板与规避**
> 1. **`server` 的歧义**：本项目 `server/` 下不存在任何 Servlet 容器或 `web.xml` 概念，且每份 README 均标注「后端工程根 / Maven 工程根」，配合 `pom.xml` 位置即可消除歧义。
> 2. **`web` 装不下移动端**：约定 —— 在出现**第三个前端应用**（移动端 / 桌面端 / 大屏独立工程）之前保持现状；届时一次性升级为 `apps/web`、`apps/mobile`、`apps/desktop`，`server/` 保持不变，`docs` 同步跟进。**这是渐进式演进，不是现在就提前搭 `apps/` 层。**
>
> **统一词汇原则（架构决策）**：代码与 `docs/` **严格同构**——
> - `platform/server` ↔ `docs/platform/server`、`platform/web` ↔ `docs/platform/web`（基础底座）；
> - `business/{ap}/server` ↔ `docs/business/{ap}/server`、`business/{ap}/web` ↔ `docs/business/{ap}/web`（业务层，每个 ap 自含 server/web）。
> 外层 `platform/`（基础底座）与 `business/`（业务层）用于隔离；文档与代码命名务必同步改动，避免出现「文档一套叫法、代码另一套」。

> **平台 / 业务分层（已拍板）**：`platform/` 是**基础底座**，承载 `server`（后端框架 `cim-*` 模块）与 `web`（前端基础框架）；`business/` 是**业务层**，每个业务系统以 `{ap}-ap` 命名（如 `iam-ap`、`mds-ap`、`mes-ap`），其下再分 `server/`、`web/`，与底座同源结构。新增业务系统只需在 `business/` 下复制该骨架即可，无需改动 `platform/`。

> **`server/` 内部不再套一层**（不做 `server/modules/cim-core`）：Maven `<modules>` 已是扁平声明，再多一层只会加深路径、无实际收益。同理 `web/` 内也不做 `web/web/`。

#### 由此带来的工程约定

| 关注点 | 约定 | 理由 |
| --- | --- | --- |
| **Maven 工程根** | 后端**基础底座**的父 POM / 聚合器即 `platform/server/pom.xml`（`<modules>` 用相对路径 `cim-core` 等，聚合全部 `cim-*` 框架模块）；仓库根**不设** Maven 聚合 pom（避免跨 `platform/` 与 `business/` 两层拉聚合），业务 ap 各自是独立 Maven 工程 | 一套仓库两套构建体系（Maven / pnpm），硬要用 Maven 驱动 pnpm（如 frontend-maven-plugin）是反模式：慢、难调试。CI 里两个 job 并行更合适 |
| **构建命令** | 后端底座：`cd platform/server && mvn clean install -DskipTests`（先把 `com.cim:cim-*` 工件装进本地仓库）；某业务 ap：`cd business/{ap}/server && mvn spring-boot:run` | 底座与业务 ap 分离构建；业务 ap 依赖已 install 的 `cim-*` 工件，单 ap 可独立部署 |
| **Docker 构建上下文** | 各工程根放自己的 `Dockerfile`，context = 该目录 | 避免 `.` context 拷贝整个 monorepo（含无关的前/后端代码）导致镜像臃肿、缓存失效 |
| **CI 触发** | 按路径过滤：`platform/server/**` 与 `business/*/server/**` 触发后端 job；`platform/web/**` 与 `business/*/web/**` 触发前端 job | 前端改样式不必跑全套 Maven 构建 |
| **前后端契约** | 后端产出 OpenAPI JSON，前端用 `openapi-typescript` 生成 TS 类型，**经 CI 产物传递**，不设共享源码目录 | 避免为「共享」引入一个两边都要依赖的第三方目录；契约单向流动即可 |
| **运维编排** | 本地开发 compose 放仓库根（命令最短）；Nginx/K8s/生产编排收口 `deploy/` | 开发高频命令要短，生产编排要集中治理 |

| 目录 | 当前状态 | 说明 |
| --- | --- | --- |
| `docs/**` | ✅ 已完成 | 设计文档：后端 §1–§25、前端 §1–§13、business 骨架（iam-ap / mds-ap / mes-ap 占位文档）、repo 仓库文档 |
| `platform/server/**` | 🟡 骨架已生成 | 后端基础框架 Maven 多模块（父 BOM + cim-core / cim-spring-support / 各 starter / cim-system / cim-business / cim-bootstrap），依赖方向见[后端文档 §1](../../platform/server/README.md#1-maven-多模块) |
| `platform/web/**` | 🟡 骨架已生成 | 前端基础框架 Vite + React + TS 脚手架，目录规范见[前端文档 §1](../../platform/web/README.md#1-技术栈与目录结构) |
| `business/**` | 🟡 骨架已生成 | 业务层 ap：iam-ap / mds-ap / mes-ap 各自的 `server`（Maven 应用）+ `web`（Vite 应用）骨架 |
| `deploy/**` | 🔲 未生成 | Nginx 配置 / K8s manifests / 生产 compose |
| `docker-compose.yml` | ✅ 已存在 | 本地一键拉起 MySQL / Redis（含可选 Kafka / Nginx） |
