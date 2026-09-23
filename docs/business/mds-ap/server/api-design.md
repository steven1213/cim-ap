# MDS 接口设计（API Design）总纲

> 本文是 MDS 对外接口的**统一契约**——覆盖 [00-blueprint §1.1](00-blueprint.md) 的 **36 个域**与管理能力（26 业务主数据域 + 2 基础参考数据域 + 4 横切治理层 + 2 横切规范 + 2 集成接口）。
> 此前 README §4 长期挂着「API 设计：待补」；本文补齐，并**以 MES 生产执行为第一消费者**来设计接口形态。
>
> 核心设计取向：**双通道**
> | 通道 | 用途 | 特征 |
> | --- | --- | --- |
> | **Distribution（订阅/下发）** | 生产热路径（投料/派工/组批） | 快照 + 增量、本地求值、**零回查**（[md-distribution-design](md-distribution-design.md)） |
> | **Query / Resolve API（查询与解析）** | 管理端、低频与复杂解析 | REST、按需调用、可分页 |
>
> 设计原则延续全篇：**MDS 拥有定义与解析能力；MES 缓存定义、只在必要时调用解析接口**。

---

## 0. 总体约定

### 0.1 基础约定

| 项 | 约定 |
| --- | --- |
| 基础路径 | `/api/v1/mds/{domain}/...`（`domain` = 域短名，如 `equipment`/`route`/`paramdef`） |
| 认证 | **IAM JWT**（本地验签 JWKS + `apps` claim 含 `mds-ap`），见 [README §1](README.md) |
| 授权 | 域内 RBAC（平台 §21.1）：菜单/按钮/数据权限；治理类接口叠加责任人校验（[md-governance-design](md-governance-design.md)） |
| 租户 | `TenantContext` 自动注入 `tenant_id`；接口**不显式传 tenant** |
| 响应体 | 统一 `Result<T>`：`{ code, message, data, traceId }`；错误码复用平台 `BizCode` |
| 分页 | `page`/`size`/`sort`（如 `sort=code,asc`）；游标分页用于大结果集（`cursor`） |
| 过滤 | `filter=` 简化语法（`field:op:value`，`op ∈ eq,ne,gt,ge,lt,le,in,like,isnull`），复杂条件走各自查询接口 |
| 并发控制 | 写接口带 `If-Match: <version>`（乐观锁）；冲突返回 `409` + 最新版本 |
| 幂等 | 写接口支持 `Idempotency-Key` 头（防重放） |
| 时间 | 统一 ISO-8601 带时区；MDS 不返回本地化格式 |
| 国际化 | 展示标签走 [uom-dict-design](uom-dict-design.md) 的 `i18n_json`；`Accept-Language` 影响展示字段 |

### 0.2 错误码分段（复用平台 `BizCode`）

| 段 | 含义 |
| --- | --- |
| `40xxx` | 参数/校验错误（含 DSL 静态校验失败） |
| `41xxx` | 认证/授权错误 |
| `42xxx` | 状态机非法迁移（如对 `DRAFT` 调 release） |
| `43xxx` | 版本冲突/冻结（对 `RELEASED`+`is_frozen` 写入） |
| `44xxx` | 引用完整性错误（外键目标不存在） |
| `45xxx` | 求值错误（表达式运行期失败，[expression-dsl-design §7](expression-dsl-design.md)） |
| `50xxx` | 服务内部错误 |

---

## 1. 接口分类（七类）

### 1.1 元数据 API（基础参考数据）

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/mds/meta/domains` | 域注册表（[md-governance-design §3.1](md-governance-design.md)） |
| `GET /api/v1/mds/meta/code-tables/{code}/items` | 码表项（含 i18n） |
| `GET /api/v1/mds/meta/uoms` / `POST .../uoms/convert` | 单位与换算 |
| `GET /api/v1/mds/meta/params` | 参数定义（[param-def-design](param-def-design.md)） |
| `GET /api/v1/mds/meta/expr/functions` / `.../variables` | DSL 函数与变量注册表（[expression-dsl-design §3](expression-dsl-design.md)） |
| `GET /api/v1/mds/meta/realtime-registry` | 实时态注册表（[realtime-contract-design §3](realtime-contract-design.md)） |

### 1.2 查询 API（各域读接口）

- 模式统一：`GET /{domain}/{entity}`（列表）、`GET /{domain}/{entity}/{id}`（详情）、`GET /{domain}/{entity}/by-code/{code}`；
- 支持 `include=children,links,localization` 控制嵌套深度（默认不展开，防 N+1）；
- **只读**为主；写操作走 §1.3 的生命周期接口或管理端接口。

### 1.3 生命周期 API（版本与状态）

对所有具备生命周期的域（route / product / recipe / process-flow / constraint / naming-rule / test-program / product-bom / apc-model）统一：

| 接口 | 说明 |
| --- | --- |
| `POST /{domain}/{entity}/{id}/submit-review` | `DRAFT → IN_REVIEW` |
| `POST .../approve` | `IN_REVIEW → APPROVED` |
| `POST .../release` | `APPROVED → RELEASED`（此时内容**冻结不可变**） |
| `POST .../freeze` / `.../unfreeze` | 叠加/解除冻结（正交于 status） |
| `POST .../obsolete` / `.../archive` | 退役 |
| `POST .../clone-as-new-version` | 基于 RELEASED 克隆出 `version+1` 的 `DRAFT`（[route-design §4.2.2](route-design.md)） |

> 迁移前校验合法性；非法迁移返回 `42xxx`。`is_frozen=1` 时的写入返回 `43xxx`。

### 1.4 求值与试算 API（设计期能力，MES 亦可用）

| 接口 | 说明 | 见 |
| --- | --- | --- |
| `POST /constraint/eval-single` | 单对象校验 | [constraint-design §4.6.1](constraint-design.md) |
| `POST /constraint/eval-context` | 上下文求值预演（**投料/派工试算**） | §4.6.2 |
| `POST /constraint/detect-conflict` | 冲突检测 | §4.6.3 |
| `POST /constraint/analyze-coverage` | 覆盖分析 | §4.6.4 |
| `POST /process-flow/compare` | 流程对比（4 类） | [process-flow-design §4.7](process-flow-design.md) |
| `POST /expr/validate` / `POST /expr/preview` | DSL 静态校验 / 样例上下文预览 | [expression-dsl-design §6](expression-dsl-design.md) |
| `POST /facility/impact` / `POST /env/impact` | 影响面推导 | [facility-utility-design §4.4](facility-utility-design.md) |

### 1.5 解析与匹配 API（**MDS 的核心增值，MES 的主用接口**）

把跨多表 join 的解析收敛为**单个接口**——避免 MES 自己拼装多表：

| 接口 | 输入 | 输出 | 见 |
| --- | --- | --- | --- |
| `POST /recipe/resolve-ppid` | 设备 + 逻辑配方 | 物理配方 + PPID | [recipe-design §4.3](recipe-design.md) |
| `POST /route/resolve-context` | 产品 | 路线 + 工艺流 + 参数窗口 + 设备组 | [process-flow-design §4.6](process-flow-design.md) |
| `POST /equipment/resolve-eligible` | 工序 + 产品 + 尺寸 | 合格设备池（含能力/节点/日历） | [equipment-design](equipment-design.md) |
| `POST /reticle/resolve-eligible` | 层 + scanner | 合格光罩（资格 + 寿命余量） | [reticle-design §4.4](reticle-design.md) |
| `POST /test-asset/resolve-eligible` | tester 型号 + 产品 | 合格探针卡/负载板 | [test-asset-design §8](test-asset-design.md) |
| `POST /tooling/resolve-eligible` | 设备类/型号 | 可用工装 | [tooling-design §8](tooling-design.md) |
| `POST /apc/resolve-model` | 产品 + 工序 + 设备 | 生效 APC/FDC 模型 | [apc-fdc-design §4.1](apc-fdc-design.md) |
| `GET /multisite/resolve-for-fab?scopeRef=F1` | 站点 | 该站点生效的主数据集合（含本地覆盖） | [multi-site-design §4.4](multi-site-design.md) |
| `POST /material/explode-bom` | 工序/配方/产品 | 用料清单 | [material-design §8](material-design.md) |
| `POST /product-bom/explode` | 父产品 + 层数 | 结构展开（含损耗） | [product-bom-design §8](product-bom-design.md) |
| `POST /org/check-qualified` | 人 + 范围 | 是否持证可用 | [org-personnel-design §4.4](org-personnel-design.md) |
| `POST /pm/next-due` | 设备 | 下次 PM/校准到期 | [pm-calibration-design §4.3](pm-calibration-design.md) |

> **设计取向**：这些接口**允许直连 MDS**（低频、非热路径）；热路径由本地缓存 + 规则包覆盖（[realtime-contract-design §7](realtime-contract-design.md)）。

### 1.6 分发 API（订阅机制）

| 接口 | 说明 | 见 |
| --- | --- | --- |
| `GET /distribution/releases/{code}` | 拉取发布批次（快照/增量） | [md-distribution-design §3.3](md-distribution-design.md) |
| `GET /distribution/releases/latest?domain=&scope=` | 取最新批次版本号（版本比对） | §4.3 |
| `POST /distribution/deliveries/{id}/ack` | 回执确认 | §3.5 |
| `GET /distribution/subscriptions/mine` | 我的订阅与断点锚点 | §3.2 |

### 1.7 治理 API

| 接口 | 说明 | 见 |
| --- | --- | --- |
| `POST /change/ecr` / `.../ecn` / `.../impact` | 变更单与影响分析 | [change-mgmt-design](change-mgmt-design.md) |
| `POST /governance/signatures` | 追加电子签名（**只增不改不删**） | [md-governance-design §4.5](md-governance-design.md) |
| `POST /governance/quality-rules/{id}/run` | 触发数据质量校验 | §4.4 |
| `GET /governance/stewards?domain=` | 责任人解析 | §4.2 |

---

## 2. MES 消费接口清单（核心）

> 按 MES 的**七大执行阶段**给出接口、频率与可否缓存。**热路径必须走订阅，不调 API**。

| 阶段 | 接口 | 频率 | 可缓存 | 目标 SLA |
| --- | --- | --- | --- | --- |
| 投料准备 | `GET /distribution/releases/latest` + 增量拉取 | 每次上电/定时 | ✅ 全量可缓存 | ≤ 1 min（增量可见） |
| 投料准备 | `POST /route/resolve-context` | 每个新产品首次 | ✅ 结果可缓存 | ≤ 200 ms |
| 投料准备 | `POST /material/explode-bom` | 每工序首次 | ✅ 可缓存 | ≤ 200 ms |
| 组批 | （本地求值 `BATCHING` 约束） | 每次组批 | ✅ 规则包 | **≤ 50 ms** |
| 派工 | （本地求值 `COMPATIBILITY`/`CAPABILITY`/`CAPACITY` 规则包） | 每次派工 | ✅ 规则包 | **≤ 50 ms** |
| 派工（需复杂解析时） | `POST /equipment/resolve-eligible` | 低（缓存未命中时） | ✅ 可缓存 | ≤ 300 ms |
| 派工（换配方/换光罩/换卡） | `POST /recipe/resolve-ppid` / `/reticle/resolve-eligible` / `/test-asset/resolve-eligible` | 低 | ✅ 可缓存 | ≤ 300 ms |
| 搬运 | （本地求值 `CARRIER_AREA_COMPAT` + 库容） | 每次搬运 | ✅ 规则包 | **≤ 50 ms** |
| 上机执行 | （订阅点表 + 本地求值 `TIMING`/`QUALITY`） | 每次 move | ✅ | **≤ 50 ms** |
| 过程监控 | （本地求值 SPC 规则 + OCAP） | 每次采样 | ✅ 规则包 | **≤ 50 ms** |
| 完工判定 | （本地求值 `DISPOSITION_TRIGGER`） | 每次完工 | ✅ 规则包 | **≤ 50 ms** |
| 保养联动 | `POST /pm/next-due` | 每日/每次上电 | ✅ 可缓存 | ≤ 500 ms |
| 环境/厂务异常 | `POST /facility/impact` / `/env/impact` | 事件驱动 | ❌ | ≤ 500 ms |
| 追溯 | `POST /product-bom/explode` + 各域查询 | 低频按需 | ❌ | ≤ 2 s（含分页） |
| 白名单回写 | `PATCH /{domain}/asset-state` | 天级 | ❌ | ≤ 500 ms |

**两条硬规则**：
1. **热路径（组批/派工/搬运/上机/判异/判定）禁止调用 MDS API**，必须本地缓存 + 规则包求值；
2. 所有跨实体解析（§1.5）优先在**接入期**批量预热缓存，而非运行期调用。

---

## 3. 版本策略

| 维度 | 策略 |
| --- | --- |
| **接口路径版本** | `/api/v1/...`；破坏性变更才升 `v2`，`v1` 保留 ≥ 12 个月 |
| **主数据编码** | **code 一经发布不改**（改名也不改 code）——下游按 code 关联 |
| **字段演进** | 只增不改不删（向后兼容）；废弃字段标记 `deprecated` 并保留 |
| **枚举扩展** | 加值不算破坏性变更；**下游必须容忍未知枚举值**（default 分支） |
| **响应形状** | 只增字段；不改既有字段类型与语义 |
| **规则包格式** | 独立版本号（`rulePack.version`）；下游按版本兼容 |
| **契约测试** | 变更前后跑契约测试（含 JSON Schema 校验） |

---

## 4. 安全与合规

| 项 | 约定 |
| --- | --- |
| 认证 | IAM JWT（RS256 本地验签 + `apps` claim 准入），[README §1](README.md) |
| 授权 | 域 + 操作级 RBAC；治理/变更/签名类接口额外校验责任人（[md-governance-design](md-governance-design.md)） |
| 数据级权限 | `@DataPermission` 行级过滤（平台 §21.1） |
| 敏感字段 | 人员信息仅存 `person_ref`（[org-personnel-design §0.1](org-personnel-design.md)）；响应脱敏 |
| 审计 | 写操作进 `@History(SNAPSHOT)`；签名类接口**只增**（[md-governance-design §4.5](md-governance-design.md)） |
| 越权放行留痕 | `SOFT` 约束越权放行须留痕（[constraint-design §4.3](constraint-design.md)），API 提供放行记录接口 |
| 限流 | 按消费者（`consumer_code`）+ 接口分级限流；热路径不占用配额（走订阅） |

---

## 5. 与既有设计的关系

| 设计 | 与 API 的关系 |
| --- | --- |
| [md-distribution-design](md-distribution-design.md) | 订阅通道 = §1.6 分发 API 的封装；**生产主通道** |
| [realtime-contract-design](realtime-contract-design.md) | §2 频率/SLA 约定的来源；回写白名单落地为 §2 末行 `PATCH asset-state` |
| [expression-dsl-design](expression-dsl-design.md) | §1.4 的 `expr/validate`、`expr/preview`；规则包内嵌 AST |
| [constraint-design](constraint-design.md) | §1.4 的约束求值接口；规则包 = 约束的下发形态 |
| [md-governance-design](md-governance-design.md) | §1.7 治理接口；域注册驱动接口清单元数据 |
| [change-mgmt-design](change-mgmt-design.md) | 生命周期接口的**授权前置**（ECN 批准才允许 clone/release） |
| [multi-site-design](multi-site-design.md) | §1.5 的 `resolve-for-fab`；订阅按站点过滤 |
| [naming-rule-design](naming-rule-design.md) | 创建接口可请求服务端生成 `code`（`?generateCode=true`） |

---

## 6. 待补 / 后续

- **OpenAPI 规格**：本文是总纲，各域需产出 OpenAPI 3.1 片段并汇总为单一 spec，驱动 SDK 与契约测试生成。
- **SDK**：Java（MES/管理端共用）与 TS（前端）两版；与 [expression-dsl-design §14](expression-dsl-design.md) 的求值 SDK 合并发布。
- **灰度与兼容**：下游版本分布监控（结合 [md-distribution-design](md-distribution-design.md) 的 `_delivery`）以决定旧版接口下线时间。
- **GraphQL 评估**：管理端多表联查是否引入（当前用 `include` 参数 + 专用解析接口替代）。
- **与既有文档闭环**：本文契约引用 [00-blueprint §1.1](00-blueprint.md) 的**全部 36 个域**的 §8/接口段；认证对齐 [README §1](README.md)；错误码与响应体对齐平台 `Result`/`BizCode`（[platform design](../../../platform/server/design.md)）。
