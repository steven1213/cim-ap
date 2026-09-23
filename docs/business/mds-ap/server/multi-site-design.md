# MDS 多工厂与多站点主数据策略（Multi-site）设计

> 本文承接[位置主数据](location-design.md)（SITE/FAB）、[元治理](md-governance-design.md)（域注册）、[分发订阅](md-distribution-design.md)（范围订阅）、[编码规则](naming-rule-design.md)（code 唯一性），
> 定义 MDS 在**多工厂 / 多站点**场景下的**主数据共享与本地化策略**。
>
> **补的缺口**：各域虽都有 `tenant_id`，但**「哪些主数据全局共享、哪些按 fab 独立」的策略从未定义**。结果是：集团内多个 fab 要么各自复制一份主数据（口径漂移、无法集团对标），要么强行共用一个 fab 的配置（本地差异无法表达）。
>
> 设计原则延续全篇：**MDS 拥有共享定义与本地化覆盖；各 fab 的实时执行归各自 MES**。

---

## 0. 定位与边界（拍板）

### 0.1 三种多站点形态（先对齐概念）

| 形态 | 含义 | 主数据策略 |
| --- | --- | --- |
| **多 fab 同集团**（最常见） | 同一企业多个晶圆厂，共享工艺平台但设备/参数本地不同 | **全局共享 + 本地覆盖**（本文重点） |
| **多站点同 fab（多产区）** | 同一厂内多个洁净区/产线 | 已是 [location-design](location-design.md) 的层级问题，无需本域 |
| **多租户 SaaS** | 不同企业共用平台 | 由 `tenant_id` 隔离（平台能力），本文不涉及 |

> **结论（MS1）**：多站点策略 = **「全局共享（GLOBAL）+ 站点本地（SITE/FAB）+ 本地覆盖（Override）」** 三层模型。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 域级共享策略（哪些域全局、哪些本地） | **MDS** | `mds_md_scope_policy` |
| 实体的本地覆盖/停用 | **MDS** | `mds_md_localization` |
| **某 fab 当前生效的主数据集合** | **MDS 计算** | `resolveForFab(scopeRef)` |
| 各 fab MES 的本地缓存 | **各 fab MES** | 按订阅范围获取（[md-distribution-design §4.3](md-distribution-design.md)） |
| 跨 fab 对标分析 | BI/集团系统 | MDS 提供统一定义维度 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 策略 ↔ 域 | `mds_md_scope_policy.domain_code → mds_md_domain.domain_code` | [md-governance-design §3.1](md-governance-design.md) |
| 本地化 ↔ 站点 | `mds_md_localization.scope_ref → mds_location.code`（`level=SITE/FAB`） | [location-design §3](location-design.md) |
| 本地化 ↔ 任意实体 | `entity_type` + `entity_ref`（多态，同 [document-design §3.2](document-design.md) 范式） | 各域 |
| 订阅按站点过滤 | `mds_md_subscription.scope_type=SITE/FAB` | [md-distribution-design §3.2](md-distribution-design.md) |
| 编码唯一性 | 跨 fab **不复制 code**，差异用覆盖表达 | [00-blueprint §4](00-blueprint.md) |
| 工艺流多 fab | 已由 `process_flow.fab_code` 表达（**域内既有机制**） | [process-flow-design §4.5](process-flow-design.md) |

> **注意**：[process-flow-design](process-flow-design.md) 已用 `fab_code` 表达"同一路线在不同 fab 的工艺展开"——这是**域级本地化的既有先例**。本域把该思路**统一为通用机制**，避免每域各造一套。

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISA-95 层级模型** | Enterprise → Site → Area → Work Center → Work Unit；主数据可按层级归属 | `mds_md_localization.scope_ref` 支持 SITE/FAB 层级 |
| **集团化半导体企业实践** | 常见「**工艺平台统一、设备与参数本地化**」：路线/产品/配方逻辑跨厂共享，设备与物理参数按厂不同 | 域级策略表 |
| **MDM 主数据分发** | Hub-and-spoke：中心维护全局，站点维护本地，冲突有明确优先级 | 本地覆盖 + specificity |
| **fab 对标（Benchmarking）** | 多厂对标要求**同一定义维度**（同一 code 语义一致） | 不复制 code 原则 |
| **SEMI E10 / 集团 OEE** | 集团级 OEE 汇总要求口径一致 | 共享 `reason_code`/`lot_type` 等口径类主数据 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **GLOBAL / 全局** | 全集团共享，所有站点可见可用 | 不是"所有租户"（那由 `tenant_id` 管） |
| **SITE / FAB 本地** | 仅某站点可见/可用 | 与"多产区"不同：站点是厂级 |
| **Localization / 本地化** | 对全局实体的**本地覆盖**（改属性/停用/启用） | **不是复制一份实体** |
| **Override / 覆盖** | 覆盖全局定义的某几个字段 | 不是替代整个实体 |
| **Scope Policy / 作用域策略** | 某域默认是全局还是本地 | 不是单条实体的归属 |
| **resolveForFab** | 计算结果：某 fab 实际生效的主数据 | 是派生视图，不是新表 |

---

## 3. 实体总览与 ER

```
mds_md_domain (既有: 域注册)
      ▲ domain_code
mds_md_scope_policy (域级共享策略: GLOBAL / SITE / FAB + 继承规则)

mds_md_localization (本地覆盖: 多态 entity + scope_ref + override_json / enabled)
        scope_ref ──> mds_location.code (level=SITE/FAB)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 域作用域策略 | `mds_md_scope_policy` | 域级归属策略 |
| 本地化覆盖 | `mds_md_localization` | 实体级本地覆盖 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_md_scope_policy`（域作用域策略）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `domain_code` | VARCHAR(32) | NOT NULL, FK→`mds_md_domain.domain_code` | 域 |
| `default_scope_level` | VARCHAR(16) | NOT NULL | `GLOBAL`/`SITE`/`FAB`（该域默认归属） |
| `allow_local_override` | BIT | DEFAULT 1 | 是否允许本地覆盖 |
| `override_fields` | VARCHAR(512) | | **可覆盖字段白名单**（如 `effective_to,admin_status,capacity`） |
| `inheritance_rule` | VARCHAR(16) | | 继承规则：`STRICT`（全局不可覆盖）/`PARTIAL`（白名单字段可覆盖）/`FULL`（本地优先） |
| `is_active` | BIT | DEFAULT 1 | 是否启用 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(domain_code, tenant_id, deleted)`。

### 3.2 `mds_md_localization`（实体本地化覆盖）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `domain_code` | VARCHAR(32) | NOT NULL | 域 |
| `entity_type` | VARCHAR(32) | NOT NULL | 实体类型（多态） |
| `entity_ref` | VARCHAR(128) | NOT NULL | 实体 id/code |
| `scope_type` | VARCHAR(16) | NOT NULL | `SITE`/`FAB` |
| `scope_ref` | VARCHAR(64) | NOT NULL | 站点（`mds_location.code`，`level=SITE/FAB`） |
| `local_action` | VARCHAR(16) | NOT NULL | `OVERRIDE`（改字段）/`DISABLE`（本地停用）/`ENABLE`（本地启用）/`LOCAL_ONLY`（仅本地存在） |
| `override_json` | JSON | | 覆盖的字段与值（仅白名单字段） |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(domain_code, entity_type, entity_ref, scope_type, scope_ref, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 MS1–MS6）

### 4.1 三层模型（MS1）
`GLOBAL`（共享）→ `SITE/FAB`（本地）→ `Override`（覆盖）。**不复制实体**是核心原则：跨 fab 共享的实体在租户内**只有一份**，fab 差异用覆盖表达。

### 4.2 域级策略先行（MS2）
不同域的合理策略不同——**由 `mds_md_scope_policy` 逐域声明**：

| 域 | 建议策略 | 理由 |
| --- | --- | --- |
| 位置 / 组织 / 班次 | **FAB**（本地） | 天然按厂 |
| 单位 / 码表 / 参数定义 | **GLOBAL** + 白名单覆盖 | 口径必须一致（否则无法对标） |
| 产品 / 产品族 / 层 | **GLOBAL** | 集团产品平台统一 |
| 工艺路线（Route） | **GLOBAL** | 工艺平台统一；差异由 Process Flow 的 `fab_code` 表达 |
| 工艺流（Process Flow） | **GLOBAL + FAB 变体** | 已有 `fab_code` 机制（域内既有先例） |
| 配方（Logic） | **GLOBAL** | 逻辑配方工具无关 |
| 配方（Physical） | **FAB**（本地） | 物理配方工具相关，各厂设备不同 |
| 设备 / 载具 / 光罩 / 测试资产 / 工装 | **FAB**（本地） | 物理资产按厂 |
| 仓库 / 厂务 / 洁净等级 / 环境监控 | **FAB**（本地） | 设施按厂 |
| 批次类型 / 原因码 / 处置规则 | **GLOBAL** + 白名单覆盖 | 集团口径一致（OEE/良率对标） |
| 约束 / 编码规则 | **GLOBAL** + 白名单覆盖 | 规则宜统一，允许局部收紧 |
| 文档 / 变更 / 治理 / 分发 | **GLOBAL** | 治理机制全集团一套 |

### 4.3 覆盖白名单校验（MS3）
- `override_fields` 是**字段白名单**：只允许覆盖明确声明的字段（防"本地偷偷改语义"）；
- 修改语义的关键字段（如 `param_def.base_uom`、`lot_type.is_yield_counted`）**默认不在白名单**——要改必须走 [change-mgmt-design](change-mgmt-design.md) 改全局；
- 由 `LocalizationValidator` 强制校验。

### 4.4 生效视图计算（MS4）
```
resolveForFab(fabCode):
  1. 取所有 GLOBAL 实体（status=ACTIVE 且在生效窗口内）
  2. 应用该 fab 的 mds_md_localization：
     - OVERRIDE → 用 override_json 覆盖白名单字段
     - DISABLE  → 从结果中移除
     - ENABLE   → 加入（本地启用）
     - LOCAL_ONLY → 仅加入本地专属实体
  3. 叠加 scope_level=FAB 的本地实体
  4. 返回该 fab 生效的主数据集合（派生视图，不落表）
```
- 该视图是**订阅下发的内容**（[md-distribution-design §4.3](md-distribution-design.md) 的范围订阅）——各 fab MES 只拿自己生效的那一份。

### 4.5 冲突与优先级（MS5）
- 同一实体同时存在 `OVERRIDE` 与 `DISABLE` → **`DISABLE` 优先**（保守）；
- 多层 `scope_ref`（若未来支持 AREA 级）→ 越具体越优先（同 [constraint-design §4.4](constraint-design.md) specificity 思路）；
- 覆盖冲突必须**记录并告警**，不允许静默。

### 4.6 与工艺流 `fab_code` 的关系（MS6）
- [process-flow-design §4.5](process-flow-design.md) 的 `fab_code` 是**域内专用机制**（表达"同一路线在不同 fab 的工艺展开"）；
- 本域是**通用机制**（任何域的本地差异）；
- 两者**并存不冲突**：工艺流的 fab 变体仍在工艺流域内表达（语义更强），本域用于其余域。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 策略/本地化实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 策略与覆盖变更落 `{entity}_hist`（**本地化变更影响站点数据，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤（与站点策略正交：tenant 是租户隔离，scope 是站内归属） |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 21 个域的默认策略种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_md_scope_policy (
  id                    VARCHAR(32) NOT NULL,
  tenant_id             VARCHAR(32) NOT NULL,
  domain_code           VARCHAR(32) NOT NULL,
  default_scope_level   VARCHAR(16) NOT NULL,
  allow_local_override  BIT DEFAULT 1,
  override_fields       VARCHAR(512),
  inheritance_rule      VARCHAR(16),
  is_active             BIT DEFAULT 1,
  description           VARCHAR(512),
  version_              BIGINT NOT NULL DEFAULT 0,
  deleted               BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_scope_policy PRIMARY KEY (id),
  CONSTRAINT uq_mds_scope_policy UNIQUE (domain_code, tenant_id, deleted)
);

CREATE TABLE mds_md_localization (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  domain_code  VARCHAR(32) NOT NULL,
  entity_type  VARCHAR(32) NOT NULL,
  entity_ref   VARCHAR(128) NOT NULL,
  scope_type   VARCHAR(16) NOT NULL,
  scope_ref    VARCHAR(64) NOT NULL,
  local_action VARCHAR(16) NOT NULL,
  override_json JSON,
  is_enabled   BIT DEFAULT 1,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_localization PRIMARY KEY (id),
  CONSTRAINT uq_mds_localization UNIQUE (domain_code, entity_type, entity_ref, scope_type, scope_ref, tenant_id, deleted)
);
CREATE INDEX idx_localization_scope ON mds_md_localization (scope_type, scope_ref);
CREATE INDEX idx_localization_entity ON mds_md_localization (entity_type, entity_ref);
```

> 历史表 `mds_md_scope_policy_hist` / `mds_md_localization_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.multisite
  ├── entity
  │   ├── MdScopePolicy.java            # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── MdLocalization.java            # @Entity 继承 BaseDefData
  │   └── package-info.java              # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── MdScopePolicyRepository.java
  │   └── MdLocalizationRepository.java
  ├── service
  │   ├── ScopePolicyService.java         # 继承 AbstractJpaService
  │   ├── FabScopedResolver.java           # resolveForFab(scopeRef) 生效视图计算
  │   └── LocalizationValidator.java        # 覆盖字段白名单校验、冲突检测
  └── web
      └── MultiSiteController.java         # 策略/覆盖维护、按站点生效视图查询
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | 各 fab MES 使用 |
| --- | --- | --- |
| **接入与订阅** | `mds_md_scope_policy` + `resolveForFab` | 某 fab MES 以 `scope_type=FAB, scope_ref=本性 fab` 订阅 → **只拿自己生效的主数据**（不拿别厂资产） |
| **本地资产** | `scope_level=FAB` 的设备/载具/光罩/仓库/厂务 | 本 fab MES 直接使用；别厂不可见 |
| **共享工艺平台** | `scope_level=GLOBAL` 的产品/路线/逻辑配方/参数定义 | 各 fab MES 共用同一份定义 → **集团口径一致** |
| **本地差异** | `mds_md_localization`（覆盖/停用） | 本 fab MES 拿到的是**已应用本地覆盖**的视图（如某配方本厂不启用） |
| **集团对标** | 统一 code 与口径（`lot_type`/`reason_code` 全局） | 集团系统可按同一维度汇总 OEE/良率 |
| **本地收紧** | 约束/编码规则的本地覆盖（白名单内） | 本 fab 可**收紧**（如更短的 Q-Time），但**不能放宽**语义字段 |

> **对 MES 的关键价值**：多厂部署时，MES **只需按站点订阅**，MDS 负责把"全局定义 + 本地覆盖"合成为该站点生效的主数据——MES 侧无需实现多厂差异逻辑。

---

## 9. 待补 / 后续

- **审批与权限**：本地覆盖应由本 fab 责任人审批（可挂 [md-governance-design](md-governance-design.md) 的 steward 与工作流）。
- **跨 fab 一致性巡检**：定期比对各 fab 生效视图，发现"非预期差异"告警。
- **AREA 级本地化**：是否需要更细粒度（当前按 SITE/FAB）。
- **与既有文档闭环**：本文引用 [md-governance-design §3.1](md-governance-design.md)、[md-distribution-design §3.2](md-distribution-design.md)、[location-design](location-design.md)、[process-flow-design §4.5](process-flow-design.md)（既有 `fab_code` 先例）、[constraint-design §4.4](constraint-design.md)（specificity 思路）、[change-mgmt-design](change-mgmt-design.md)、[00-blueprint §4](00-blueprint.md)。
