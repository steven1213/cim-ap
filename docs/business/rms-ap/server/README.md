# RMS-AP 后端设计文档（骨架）

> 本文件为 **骨架占位**：记录定位、认证边界，以及与需求清单的对齐关系。  
> **需求权威源：👉 [Req.md](../Req.md)（完善版规格）**——在客户《技术协议》53 条（附录 A 原样保留）基础上，按行业实践（Applied E3 RMS 基线、SEMI E42/GEM/SECS-S7 惯例）细化为带 FR/IF/NFR/UI 编号的需求，含系统间接口字段级设计与平台铁律映射；**§1.2 业界痛点 → 本系统改善** 为差异化需求来源。

## 0. 定位

- **归属**：`docs/business/rms-ap/`，业务 ap，基于平台框架（`docs/platform/server`）实现。
- **业务域**：RMS（Recipe Management System，配方管理系统）——设备配方全生命周期管理：
  - **配方基本管理**（Req 1–6）：Recipe List / Recipe Body 建档，经 EAP 从设备上传（含批量、设备间复制）、通配符查询、删除、另存为。
  - **配方生命周期**（Req 13–20、30–38、41–42）：Draft / 中间版本（待签核）/ 生效版本，多版本且至多一个生效；主/子配方多层结构；Golden Recipe（同型号共享、兼容机差）；Spec Template；签核（含外部签核系统集成）；版本回退。
  - **比对与 Bypass**（Req 21–29、34、36–37、39、43–45）：Online Run 时响应 EAP/MES 比对请求，失败可按配置 Hold + 发 Alarm；比对方式 target / range / list-in / tolerance，Full Body / Parameter / 结构比对；设备级与单配方级 Bypass；EC/SV 常量上传比对。
  - **设备管理**（Req 46–48）：设备类型 / 设备区域 / 设备基础设置（查询、新建、修改、删除）。
  - **高可用**（Req 49–53）：属平台部署层能力（无状态多实例 + 负载均衡），不在本 ap 业务实现内展开。

## 1. 认证与准入对接（沿用平台方案）

访问 `rms-ap` 时的校验链路：

1. **本地验签**：用 IAM 的 JWKS 公钥验证令牌签名与有效期（不每请求回查 IAM）。
2. **准入校验（IAM 管）**：检查令牌 `apps` claim 是否含 `rms-ap`；不含则拒绝进入。
3. **内部权限（业务自管）**：准入通过后，`rms-ap` 用自己的 **RBAC**（复用平台 §21.1）控制菜单 / 按钮 / 数据行级权限。

> 关键点：IAM 的刀在「准入（步骤 2）」结束，**步骤 3 完全由 rms-ap 自行控制**。

## 2. 内部权限（业务自管；对应 Req 7–12）

- ⚠️ **铁律映射**：Req 8「用户维护」**不在 RMS 落地用户主数据**——员工身份由身份目录（AD/HR 同步）承载，RMS 经 IAM 统一登录拿人；本 ap 只做**业务侧授权关系**（角色、功能权限、设备可见性）。
- Req 7/9/10（角色 / 用户角色授权 / 功能权限）→ 复用平台 RBAC（`sys_role` / `sys_permission` / `sys_user_role` 体系），权限码三段式 `rms:xxx:yyy`。
- Req 11/12（**设备级可见/可操作授权**、操作历史）→ RMS 业务自建：用户↔设备授权表 + 操作留痕（复用平台审计/历史基类 `{X}`↔`{X}Hist` 约定）。

## 3. 工程约定（骨架即已遵循）

- 独立 Maven 工程 `business/rms-ap/server`（无聚合 pom，构建用 `cd` 进目录）。
- 启动类 `com.cim.rms.server.RmsApApplication`，端口 **8084**（iam 8081 / mds 8082 / mes 8083 顺延）。
- 后续新增域模块：组件扫描装配 + 自带 `@EntityScan`/`@EnableJpaRepositories`（ADR-11 各单元扫描根不重叠：本 ap 用 `com.cim.rms.*`）。
- 后续新增数据迁移：Flyway 版本段遵循平台登记（宿主 V1–999，新模块段位见 `cim-jpa-starter` 迁移 README）；`@FilterDef` 只用平台唯一声明点（ADR-12），本 ap 只标 `@Filter`。
- EAP 对接（Req 1–2、40 上下载通道）：走 `cim-mq-starter`（outbox / 幂等 / 顺序，见平台 §12/§13/§16）承载异步分发链路；SECS-II 事务由 EAP 侧终结，RMS 只消费/回填业务结果。

## 4. 待规划（backlog 占位，按 Req 分组细化）

- [ ] 域模型：Recipe / RecipeVersion / RecipeBody / Parameter Spec（target-range-list-tolerance 四种比对方式）/ 主子配方层级 / Golden Recipe / Spec Template / 审批与签核流 / 设备绑定与授权。
- [ ] 生命周期状态机：Draft → Pending（签核中）→ Active（唯一生效）/ 预设限时使用（Req 20：审核中未激活配方的限时放行，超时自动失效、签核通过自动取消预设）。
- [ ] 比对引擎：参数级 / Full Body / 结构比对 + 差异报告；Bypass 配置（设备级 / 配方级）；失败 Hold + Alarm 事件。
- [ ] EC/SV：设备常量与状态值的 Spec 设定（按配方 / 产品维度）与在线校验。
- [ ] 与 EAP 的契约：上传 / 下载 / 比对请求的消息模型（复用 cim-mq-starter 抽象）。
- [ ] 多租户：复用平台 `cimTenantFilter`。
- [ ] i18n 文案键命名空间（`rms.*`）。
