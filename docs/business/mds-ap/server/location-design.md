# MDS-AP 位置主数据详细设计（Site / Fab / Area / Bay / Stage）

> 本文档为 `mds-ap` 业务域中「位置主数据」的**实现级详细设计**，承接 `docs/business/mds-ap/server/README.md` §1。
> 同时对齐平台底座：通用数据模型（`BaseDefData` / `@History` / `cimTenantFilter` / T2.5 软删唯一约束 / 主历成对迁移），详见 `docs/platform/server/design.md` §2.1–§2.3、§6。
>
> 行业依据：半导体 CIM 工厂拓扑与设备层级通用做法（SEMI E10 可靠性状态、SEMI E148 设备层级、SEMI E157 设备信息模型），以及 MES/PLM 主数据管理（MDM）中「可扩展层级 + 闭包表 + SCD2 生效日期」的主流工程实践。

---

## 0. 决策总览（已拍板）

下列 6 项开放问题已由行业经验拍板，结论为本设计的最终约束：

| # | 问题 | 拍板结论 | 行业依据 |
| - | ---- | -------- | -------- |
| D1 | 层级是否固定（将来是否会冒出 SubFab/Room/Zone） | **标准五层 + `level` 枚举可扩展**；EQUIPMENT 不进入位置层级。新增层级只需加枚举值 + 闭包，不动表结构 | SEMI E148 设备层级、MES 工厂拓扑通用建模 |
| D2 | Area 语义（工艺域 / 物理区 / 两者） | **AREA = 工艺/功能域**（Litho/Etch/…），物理落位由 BAY/STAGE 表达；Area 管「工艺归类」，Bay 管「物理落位」 | SEMI process area 与物理 bay 分离；晶圆厂现实（一工艺跨多物理区） |
| D3 | 严格单父树还是多父 | **严格单父树**（每节点恰一父，SITE 为根）。跨区服务用独立「能力/分配」关联实体，不破坏树 | 向上 rollup（产能/在制/良率）与权限推演需无歧义 |
| D4 | 是否需要 SCD2 生效日期 | **需要轻量 SCD2**：`effective_from` / `effective_to` + `status`。独立于 `@Version` 行锁与 `revision` 业务版本，也独立于 `{X}Hist` 变更审计流水 | 工厂拓扑随时间变更（Bay 重划、Area 更名、产线关停） |
| D5 | code 唯一范围与编码规则 | **code 同租户全局唯一**（非仅兄弟唯一）；遵循 T2.5 软删唯一 `(code, tenant_id, deleted)`。推荐分层点分码规则（见 §3.4） | 位置 code 被 MES 派工/EAP/报表直接引用为自然键，全局唯一最稳 |
| D6 | 机台与位置关系（谁管、搬迁轨迹用历史表还是事件表） | **设备由 MDS 主数据管理**，`mds_equipment.location_id` 指向 BAY/STAGE；**搬迁轨迹复用 `@History(SNAPSHOT)` → `mds_equipment_hist`**，可选叠加 `mds_equipment_move_event` 承载业务上下文 | 平台 M3 已落地快照历史，避免重复造轮子 |

---

## 1. 业务定义与层级语义

### 1.1 标准五层位置层级

```
SITE ──> FAB ──> AREA ──> BAY ──> STAGE
                              │
                              └──> EQUIPMENT（独立主数据，挂在 BAY/STAGE 上，非位置层级）
```

| 层级 | 含义 | 典型示例 | 关键属性 |
| ---- | ---- | -------- | -------- |
| `SITE` | 厂区 / 园区（可能含多座 fab） | 苏州厂 `SZ`、上海厂 `SH` | 城市、园区编码 |
| `FAB` | 单一制造厂 / 晶圆厂建筑 | `F1` / `F2` / `C1` | fab 类型（前道/后道）、洁净等级 |
| `AREA` | **工艺/功能域**（非物理分区） | `LITH` 光刻、`ETCH` 刻蚀、`DIFF` 扩散、`CMP`、`TF` 薄膜、`IMP` 注入、`INSP` 检验、`METL` 金属化 | 工艺阶段、是否黄光区 |
| `BAY` | 物理作业区 / 湾（Area 内的物理分组） | `B01`…`B12` | 洁净室分区、安全等级 |
| `STAGE` | 子区 / 作业单元（可选更细物理分组） | `01`… | 工具簇、缓冲区 |
| `EQUIPMENT` | 机台（独立主数据，不属位置层级） | `TK0001` | 型号、厂商、序列号、`location_id` |

### 1.2 关键语义澄清（D2）

- **AREA 是「工艺归类」，BAY 是「物理落位」**。同一工艺 AREA 可跨多个物理 BAY（例如光刻区分布在多个洁净室分区）；一个 BAY 内也可混排不同工艺（共享/通用区域）。二者解耦后，派工按 Area、物理寻址按 Bay，互不绑架。

> **闭环下游**：工艺路线 `mds_route_operation.area_code` 引用 AREA（见 [route-design.md](route-design.md) §3.3），使 route 工序在设计期即落点工艺域；与 `mds_equipment_capability.area_code`（[设备设计 §3.7](equipment-design.md)）同源，保证「工序想做的工艺」与「设备能服务的工艺」一致。
- **STAGE 为可选层**：多数产线只用到 BAY；仅当单 Bay 内仍需细分（如工具簇、缓冲岛）时启用，不强制。
- **第五层定名 STAGE（2026-11-13 修订，原 SUBBAY）**：行业位置树惯用「Site → Fab → Area → Bay → Stage（设备位）」，设备落在 Stage（或无细分的 Bay）。**Module 不是位置层**——SEMI E157/E120 语境里 Module = 设备内部的工艺腔（Process Module，如 PM 腔 / Load Lock），属 `Equipment → Module` 设备域层级；MDS 位置树不得引入 Module，避免与设备域将来引入的工艺腔 Module 撞名。
- **存储设施（Bank/Stocker）是独立主数据**：经 `location_id` 落到本层级（通常 BAY/STAGE，中央库可为 FAB），**并非 `level` 值**；与 EQUIPMENT 同构（落位于位置层级、自身不是位置层级）。详见 [仓库设计 bank-design.md](bank-design.md)。
- **EQUIPMENT 不进入位置层级**：设备是带生命周期、型号、状态的企业级主数据，与「位置拓扑」正交。设备与位置通过 `equipment.location_id` 关联，而非作为位置树的叶子。

---

## 2. 数据模型

### 2.1 实体关系（ER）

```
┌─────────────────┐        ┌──────────────────────┐
│   mds_location  │ 1    N │ mds_location_closure │
│  (位置主数据)    │──────>│  (祖先-后代闭包)       │
│  parent_id ─────┘ self-FK│  ancestor/descendant  │
└────────┬────────┘        └──────────────────────┘
         │ 1
         │ N
┌────────▼────────┐        ┌──────────────────────┐
│  mds_equipment  │ N    1 │ mds_equipment_move_event │
│  (设备主数据)    │──────>│  (搬迁业务事件,可选)    │
│  location_id ───┘ FK     └──────────────────────┘
└────────┬────────┘
        │ @History(SNAPSHOT)
        ▼
   mds_equipment_hist  (快照历史，自动落)

mds_location.clean_class_code ──> mds_clean_class(code)   (洁净等级，见 cleanliness-env-design.md)
```

### 2.2 `mds_location`（位置主表）

继承平台 `BaseDefData`（含 `id` / `tenant_id` / `description` / `@Version` / `deleted`）+ 审计五件套（见 §5）。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` | VARCHAR(32) | PK | 平台 `IdGenerator`（雪花/UUIDv7） |
| `tenant_id` | VARCHAR(32) | NOT NULL | 租户（来自 `Auditable`） |
| `level` | VARCHAR(16) | NOT NULL | 枚举：`SITE`/`FAB`/`AREA`/`BAY`/`STAGE` |
| `code` | VARCHAR(64) | NOT NULL | 本地段编码（见 §3.4），同租户全局唯一 |
| `name` | VARCHAR(128) | | 显示名（中/英） |
| `parent_id` | VARCHAR(32) | NULL（SITE 为根） | 自引用 FK → `mds_location.id` |
| `path` | VARCHAR(512) | NOT NULL | 物化全路径，如 `/SZ/F1/LITH/B03` |
| `seq` | INT | DEFAULT 0 | 同级排序 |
| `clean_class_code` | VARCHAR(32) | NULL, FK→`mds_clean_class(code)` | **洁净等级**（FAB/AREA/BAY 常用）；用于区域准入校验与环境合规。定义与监控点见 [洁净度与环境监控设计](cleanliness-env-design.md) |
| `effective_from` | TIMESTAMP | NOT NULL | SCD2 生效起（D4） |
| `effective_to` | TIMESTAMP | NULL=当前有效 | SCD2 生效止 |
| `status` | VARCHAR(16) | NOT NULL | `PLANNED`/`ACTIVE`/`INACTIVE` |
| `description` | VARCHAR(512) | | 来自 `BaseDefData` |
| `@Version` | BIGINT | | 行级乐观锁（T2.7 层①） |
| `deleted` | BOOLEAN | NOT NULL | 软删（T2.5） |
| 审计列 | — | | `create_time/create_user/event_time/event_user/event_name/event_comment/trx_id` |

**约束与索引：**
- 唯一：`UNIQUE (code, tenant_id, deleted)`（T2.5 软删兼容：一删一活可并存）。
- 索引：`idx_parent (parent_id)`、`idx_path (path)`、`idx_level (level)`。
- 行级过滤：`@SQLRestriction("deleted = false")` + `@Filter(name="cimTenantFilter", condition="tenant_id = :tenantId")`（见 §5）。

### 2.3 `mds_location_closure`（闭包表，D1/D3）

用于高效祖先/后代查询（不递归 CTE），支持层级可扩展（加层即加行，不动结构）。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `ancestor_id` | VARCHAR(32) | PK 一部分 | 祖先位置 id |
| `descendant_id` | VARCHAR(32) | PK 一部分 | 后代位置 id |
| `depth` | INT | NOT NULL | 0=自身，1=直接子，… |
| `tenant_id` | VARCHAR(32) | NOT NULL | 租户隔离 |

- PK：`(ancestor_id, descendant_id)`；索引：`idx_descendant (descendant_id)`（向上查父链）、`idx_ancestor (ancestor_id)`（向下查子树）。
- **维护时机**：在 `LocationClosureMaintainer` 中、与位置写操作**同一事务**内维护（见 §4.2），不依赖 DB 触发器。
  - 新建节点：插入 `(self, self, 0)`；若 `parent_id` 非空，复制父的所有 `(ancestor, parent)` 行并拼接 `(ancestor, new, depth+1)`。
  - 移动节点：先删该节点作为 `descendant` 且 `depth>0` 的祖先链，再按新父重算插入。
  - 软删节点：清理其作为 descendant 的全部闭包行（含自身 depth=0）。

### 2.4 设备主数据（D6，仅保留位置相关摘要）

> ⚠️ **单一来源（2026-09-23 修订）**：`mds_equipment` 的**权威字段与 DDL 见 👉 [设备建模详细设计](equipment-design.md) §3.3 / §6**。早前版本此处保留过一份简化字段表（`model`/`manufacturer`/`serial_no`/`status`），与设备设计的**五态分离**（`lifecycle_status`/`admin_status`/`qualification_state`/E10/控制模式）及**型号外键** `model_id` 不一致，**已删除**，避免建表口径分叉。

本域与设备只有两个接触点：

| 接触点 | 说明 |
| --- | --- |
| `mds_equipment.location_id → mds_location(id)` | 设备**当前物理位置**（落 BAY/STAGE）；完整字段见 [equipment-design §3.3](equipment-design.md) |
| `@History(SNAPSHOT) → mds_equipment_hist` | 换 Bay 时经设备服务 `update` 自动落快照（含 `op_type`/`operator`/`op_time`/`trx_id`/`change_set_json`），**天然形成搬迁时间线** |

- 设备**不从属于位置层级**（EQUIPMENT 不是 `level` 枚举值，见 D1）；`area` 不冗余存储，由 `location_id` 经闭包向上推导。

### 2.5 `mds_equipment_move_event`（可选搬迁业务事件，D6 补充）

当搬迁需记录「原因 / 审批单 / 承运」等业务上下文时叠加，继承平台 `BaseEventData`（`biz_key`/`event_type`/`payload` + 审计）。

| 列 | 说明 |
| -- | ---- |
| `biz_key` | 设备 id |
| `event_type` | 如 `MOVE` |
| `payload` | JSON：`{from_loc, to_loc, reason, wo_no, approver}` |

> 主时间线以 `mds_equipment_hist` 为准；本表仅补充业务语义，二者并存不冲突。

---

## 3. 设计要点与决策细则

### 3.1 层级可扩展（D1）
- 层级由 `level` 枚举表达，而非「每级一张表」。未来新增 `ROOM`/`ZONE`/`SUBFAB`：仅扩展枚举 + 在闭包中自然成层，**零表结构变更**。
- 校验规则（应用层）：`SITE` 必须无父；`FAB` 父必须是 `SITE`；`AREA` 父必须是 `FAB`；`BAY` 父必须是 `AREA`；`STAGE` 父必须是 `BAY`。

### 3.2 严格单父（D3）
- 每节点恰一 `parent_id`（SITE 为根 null）。**禁止多父**。
- 跨区服务（如共享量测机台同时服务多个 Area）用 `equipment_capability` 或 `area_allocation` 关联实体表达，不破坏树。

### 3.3 SCD2 生效日期（D4）
- `effective_from`/`effective_to` 表达「时间维版本」：`effective_to IS NULL` 为当前有效；历史区间不重叠。
- 与平台三层版本语义区分明确：
  - `@Version`：行级乐观锁（并发更新，不进历史）。
  - `revision`：`BaseRevisionData` 业务版本（发布/归档驱动，本实体未继承，按需启用）。
  - `{X}Hist`：变更审计流水（谁在何时改了什么字段）。
  - `effective_from/to`：业务时间维（同一位置在不同时间的有效拓扑），**独立维度，不进 Hist**。
- 查询默认取「当前有效」（`effective_to IS NULL AND status='ACTIVE'`）；时间旅行查询按 `effective_from<=:t AND (effective_to IS NULL OR effective_to>:t)`。

### 3.4 code 唯一性与编码规则（D5）
- **唯一性**：`code` 同租户**全局唯一**（下游以 code 为自然键直引），约束 `(code, tenant_id, deleted)`。
- **推荐编码（点分、段长固定、大写、数字补零）**：`{SITE}.{FAB}.{AREA}.{BAY}[.{STAGE}]`
  - SITE：2 字母（如 `SZ`/`SH`）
  - FAB：1–2 位（如 `F1`/`C1`）
  - AREA：3–4 字母工艺码（`LITH`/`ETCH`/`DIFF`/`CMP`/`TF`/`IMP`/`INSP`/`METL`）
  - BAY：字母+2 数字（`B01`）
  - STAGE：可选 2 数字（`01`）
- `path` = `/` + 各级 code 拼接（如 `/SZ/F1/LITH/B03`），由系统在写入时物化并建唯一/索引；`code` 仅存本地段。
- **编码生成由统一命名规则引擎驱动**：位置的点分码对应 `NamingRule` 的 `HIER` 段（沿父链拼祖先 code）+ `ATTR` 段（本层段），详细规则定义与生成时机见 [命名/编码规则设计](naming-rule-design.md)（§4.2）。

### 3.5 搬迁轨迹（D6）
- 设备当前位置 = `location_id`；历史轨迹 = `mds_equipment_hist`（SNAPSHOT 自动落）。
- 业务上下文（原因/单号）可选入 `mds_equipment_move_event`。
- **不另建独立「位置变更事件流」表来驱动主数据**，避免与平台 `@History` 重复。

---

## 4. 代码结构与实现

```
com.cim.mds.server
├── location
│   ├── domain
│   │   ├── Location.java            # @Entity 继承 BaseDefData；level=LocationLevel 枚举
│   │   ├── LocationClosure.java      # @Entity 闭包表
│   │   ├── LocationLevel.java        # 枚举 SITE/FAB/AREA/BAY/STAGE + 父级校验
│   │   └── LocationRepo.java         # extends BaseRepository<Location,String>
│   ├── service
│   │   ├── LocationService.java      # extends AbstractJpaService<Location>
│   │   └── LocationClosureMaintainer.java  # 同事务维护闭包（建/移/删）
│   └── web
│       └── LocationController.java   # extends BaseController<Location>
├── equipment
│   ├── domain
│   │   ├── Equipment.java            # @Entity 继承 BaseDefData + @History(SNAPSHOT)
│   │   └── EquipmentMoveEvent.java   # @Entity 继承 BaseEventData
│   └── service
│       └── EquipmentService.java     # extends AbstractJpaService<Equipment>
```

### 4.1 关键实现点
- 所有写操作经 `AbstractJpaService`（平台基类），自动触发 `@History` 落历史、审计填充、`cimTenantFilter` 租户过滤。
- `LocationService` 在 `create`/`move`/`remove` 中调用 `LocationClosureMaintainer`（同事务）维护闭包。
- `@FilterDef(name="cimTenantFilter", params=@ParamDef(name="tenantId", type=String.class))` 放 `location` 包 `package-info.java`（全局唯一，Hibernate 不解析元注解）；`@Filter`/`@SQLRestriction` 直接标实体。

### 4.2 闭包维护伪码
```
onCreate(node):
  insert (node, node, 0)
  if node.parent != null:
    for (a, parent, d) in closure where descendant = parent:
      insert (a, node, d+1)

onMove(node, newParent):
  delete from closure where descendant = node and depth > 0
  # 重新按 newParent 计算（同上 onCreate 的父链逻辑）

onRemove(node):
  delete from closure where descendant = node   # 含 depth=0 自身行
```

---

## 5. 与平台底座衔接（强制对齐）

| 能力 | 平台机制 | 本设计用法 |
| ---- | -------- | ---------- |
| 基类/审计 | `BaseDefData`（`Auditable`） | 位置/设备主表继承，自动获得 id/tenant/审计/@Version/deleted |
| 历史快照 | `@History(SNAPSHOT)` → `{X}Hist` | `mds_equipment` 搬迁轨迹（D6） |
| 多租户 | `cimTenantFilter` + `TenantContext` | 位置/设备/闭包均标注，查询零感知过滤 |
| 软删唯一 | `(code, tenant_id, deleted)` + `@SQLRestriction` | T2.5 兼容，一删一活并存（D5） |
| 主键 | `IdGenerator`（雪花/UUIDv7） | 所有实体 `String id` |
| 迁移 | 主/历成对 + Flyway `db/migration/{common,mysql,...}` | 见 §6；业务 ap 自维护迁移目录 |

---

## 6. 迁移与 DDL（示例：MySQL / common）

> 遵循平台 §6.2「主/历成对」：每个 `CREATE/ALTER {X}` 在同一迁移文件内含 `{X}Hist` 对应 DDL。下列片段仅示意，实际按 `db/migration/{vendor}` 落地。

```sql
-- V1__location.sql  (common / mysql)
CREATE TABLE mds_location (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  level           VARCHAR(16)  NOT NULL,
  code            VARCHAR(64)  NOT NULL,
  name            VARCHAR(128),
  parent_id       VARCHAR(32),
  path            VARCHAR(512) NOT NULL,
  seq             INT          DEFAULT 0,
  clean_class_code VARCHAR(32) NULL,
  effective_from  DATETIME(3)  NOT NULL,
  effective_to    DATETIME(3),
  status          VARCHAR(16)  NOT NULL,
  description     VARCHAR(512),
  version         BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time     DATETIME(3), create_user VARCHAR(64),
  event_time      DATETIME(3), event_user  VARCHAR(64),
  event_name      VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_location PRIMARY KEY (id),
  CONSTRAINT uq_mds_location_code UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_mds_location_parent FOREIGN KEY (parent_id) REFERENCES mds_location (id)
  -- clean_class_code: 跨域逻辑引用（服务层校验），不建 DB 级 FK —— 见 00-blueprint §11
);
CREATE INDEX idx_mds_location_parent ON mds_location (parent_id);
CREATE INDEX idx_mds_location_path   ON mds_location (path);
CREATE INDEX idx_mds_location_level  ON mds_location (level);

CREATE TABLE mds_location_closure (
  ancestor_id   VARCHAR(32) NOT NULL,
  descendant_id VARCHAR(32) NOT NULL,
  depth         INT         NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  CONSTRAINT pk_mds_location_closure PRIMARY KEY (ancestor_id, descendant_id),
  CONSTRAINT fk_closure_anc  FOREIGN KEY (ancestor_id)   REFERENCES mds_location (id),
  CONSTRAINT fk_closure_desc FOREIGN KEY (descendant_id) REFERENCES mds_location (id)
);
CREATE INDEX idx_closure_desc ON mds_location_closure (descendant_id);
CREATE INDEX idx_closure_anc  ON mds_location_closure (ancestor_id);

-- ⚠️ 单一来源（2026-09-23 修订）
-- mds_equipment / mds_equipment_hist / mds_recipe / mds_equipment_recipe_qual 的权威 DDL 分别见：
--   equipment-design.md §6（设备：型号目录/模块/端口/能力/多 owner/五态分离/控制模式）
--   recipe-design.md   §6（配方：逻辑/物理/分组/资格+PPID）
-- 本文不再重复定义，避免建表口径分叉。
-- 设备与位置的关系仍由 mds_equipment.location_id → mds_location(id) 表达（见 equipment-design §3.3）。
```

---

## 7. 待补 / 后续

- **设备主数据扩展**：已独立成文 👉 [设备建模详细设计](equipment-design.md)（型号/模块/端口/能力/闭环）。校准/保养周期主数据可后续独立成篇。
- **API 设计**（README §4）：位置树查询（子树/祖先链/按 Area 列设备）、设备位置变更接口（触发 Hist + 可选 move_event）。
- **数据权限**（`@DataPermission(Scope.FACTORY)`）：按厂区/租户的数据行级权限接入平台 RBAC（M6）。
- **种子数据 / 初始化**：SITE/FAB 由实施导入，Area/Bay 由工艺工程维护。
- **T9 厂级轮廓（[99-backlog T9-5/T9-10](99-backlog.md)）**：§1.1 文案写 FAB 有「前道/后道、洁净等级」，表结构**无 `fab_type`**。**已拍板**：`fab_type` 落位（前道/后道）；**`wafer_sizes` 不落 location**——单一来源归 [OperatingProfile](operating-profile-design.md)（可 override 到设备/工序），避免与 [multi-site-design](multi-site-design.md) 双源。混线自动化挂 Area/设备，不能只挂 Fab。

---

## 8. 增补（2026-11-13）：编辑锁 / 表模型分叉 / 用户动线

### 8.1 编辑锁（is_occupied / occupied_by / occupied_at）

**语义：这是「人 vs 人」的编辑互斥锁，不是引用保护。** 防止两人同时编辑同一条主数据互相覆盖；
「数据 vs 数据」的引用保护仍由 §3 的删除/归属校验承担，两者并存、不互替。

三列加在位置五表（`cim_site` / `cim_fab` / `cim_area` / `cim_bay` / `cim_stage`）：

| 列 | 类型 | 说明 |
| -- | ---- | ---- |
| `is_occupied` | BOOLEAN NOT NULL DEFAULT FALSE | 该条正被某人编辑 |
| `occupied_by` | VARCHAR(64) NULL | 占用者账号（IAM username） |
| `occupied_at` | TIMESTAMP NULL | 抢锁时间（超时判定依据） |

**生命周期与规则：**

| 环节 | 规则 |
| ---- | ---- |
| 点「编辑」 | 先原子抢锁：`UPDATE {表} SET is_occupied=1, occupied_by=:user, occupied_at=:now WHERE id=:id AND deleted=0 AND (is_occupied=0 OR occupied_by=:user OR occupied_at < :expire)`。影响行数=0 → 返回当前占用者，前端提示「{占用者} 正在编辑」不进表单 |
| 占用者本人 | 再次打开/继续编辑**放行**（刷新 `occupied_at`） |
| 保存成功 | 清锁与数据保存**同一事务**：`is_occupied=0, occupied_by=NULL, occupied_at=NULL` |
| 取消/关闭弹窗 | 前端显式调释放接口（按 `occupied_by` 条件释放，只清自己的锁） |
| 异常残留 | `occupied_at` 超过阈值（默认 30 分钟，可配）视为过期，他人可抢锁覆盖；管理员可强制解锁（走同一释放接口、跳过 `occupied_by` 条件） |
| 删除 | 同样先判锁：被他人占用的记录拒绝删除（本人可删，记录消失锁随之消失） |
| 保存兜底 | 服务层校验 `occupied_by` 仍是本人 + `@Version` 乐观锁双保险，防「锁被抢后旧窗口覆盖」 |

实现形态：MDS 域内 `@MappedSuperclass EditLockDefData extends BaseDefData`（三列统一），抢锁/释放用
JdbcTemplate 对五表统一实现（表名白名单），不污染平台基类。

### 8.2 表模型分叉说明（实现 vs 本文 §2）

本文 §2 的权威模型是 `mds_location` 单表 + `parent_id` + 闭包表。**当前 MDS 实现采用每层一表**
（`cim_site` → `cim_fab` → `cim_area` → `cim_bay` → `cim_stage`，编码逻辑引用、不建物理 FK），分叉理由：

1. 各层特有属性是强类型列（Fab 的 `fab_type`/`amhs_type`/`clean_grade`、Area 的 `area_type`），
   单表模型只能塞 JSON 或另开扩展表，validate 成本与查询成本都更高。
2. 6 英寸厂层级固定五层、数据量小（Site/Fab 个位数、Area/Bay/Stage 数十到数百），
   闭包表的递归查询收益不存在，沿编码链向上走即可。
3. 层间引用校验、引用保护（删上层先数下游）在应用层实现——跨域逻辑引用本就无 DB FK 可依。

**约束沿用本文口径**：编码唯一范围按物理现实——`site_code` 全局唯一、`fab_code` 全局唯一、
`area_code` Fab 内唯一、`bay_code` Fab 内唯一（物理湾道编号全厂不重复）、`stage_code` Bay 内唯一。
本文 §2 单表模型保留为「层级扩展（加层）」时的演进目标。

### 8.3 用户动线（树形拓扑页）

前端收敛为**一页「位置拓扑」**（左树右详情），替代 Site/Fab/Area 三个平铺 CRUD 页：

1. **进页即全貌**：左树 `Site → Fab → Area → Bay → Stage`，节点带状态徽标（停用 / 被编辑占用）。
2. **新建从树发起**：选中节点 →「+ 下级」，父级自动带入并锁定；顶层「+ 基地」入口在工具条。
3. **编辑先抢锁**：点「编辑」先 claim 锁——抢不到弹提示不进表单；弹窗内展示占用信息。
4. **拦截前置**：删除 / 换归属被引用保护挡下时，确认框/弹窗内直接给出原因与下游数量，
   不等提交后 toast 报错。
5. **写操作守门**（应用层强制）：

| 操作 | 规则 |
| ---- | ---- |
| 改名称/属性 | 随时允许（本人持锁） |
| 换归属 | 新父必须存在且**启用**；本节点有下游子层时拒绝（先清空下游再搬） |
| 停用 | 允许；但停用后不可被新子层引用（`requireActive*` 校验） |
| 删除 | 有下游子层拒绝；被他人编辑锁占用拒绝 |


## 9. 增补（2026-11-13）：AreaGroup 与 StageGroup 的裁决

### 9.1 AreaGroup —— 做（统计口径的分类属性）

**用途拍板：统计口径**（前段/后段等工艺分段，看板与报表的汇总维度），不是调度域、不是权限边界。

**形态拍板：树内虚拟分节**，不作为位置树的正式一层：

- 物理五层树不变（Site → Fab → Area → Bay → Stage）；AreaGroup 是「Fab 下 Area 的归组展示」，
  以可折叠分节呈现——**虚线组头是分组视图，不是位置节点**，不改物理父子关系。
- 三个触点全部长在拓扑页上，不加新页面：
  1. 树内分节：Fab 下 Area 按 group 折叠；组头可折叠/展开，组头「+」直接在该组下新建 Area；
  2. 表单属性：建/改 Area 时「所属区域组」下拉（可空）——未分组的 Area 直接挂 Fab 下不分节；
  3. 筛选与汇总：工具栏按组过滤；看板/报表按组汇总（对接 RMS 时作为统计维度下发）。

**数据模型**：

- `cim_area_group`：`group_code`（Fab 内唯一）/ `group_name` / `fab_code` / `remark` / `enabled`，
  与位置五表同款审计列 + **编辑锁三列**（§8.1，组定义也是可被并行编辑的主数据）；
- `cim_area.area_group_id`：可空逻辑引用；服务层校验「组与 Area 必须同 Fab」；
- 组删除：组内仍有 Area 时拒绝（提示先移出）；
- 权限：**复用 `mds:area:*`**——组是 Area 的管理切面，不新增权限码、不加菜单/按钮节点。

### 9.2 StageGroup —— 不建实体（裁决记录）

**语义拍板：「一台设备占多个机台位」（多腔设备一机多位）。** 该归属关系的正确表达是**设备域的
Equipment ↔ Stage 引用**（一台设备台账挂多个机台位），不是再造一个组实体。理由：

1. 组的成员随设备搬移/换腔变化，owner 是设备，放在位置域必然双向同步腐烂；
2. 设备域已有 `mds_equipment_group`（跨设备**派工组**），分工就此明确：
   **设备内 = 腔体归属（Equipment ↔ Stage），跨设备 = 派工组（equipment_group）**；
3. 若将来出现「同工艺能力的机台位集合」需求（ qualifications/派工约束），属设备资格域，
   到时按需建模，不预埋。

**树内展示衔接**：Bay 下 Stage 按所属设备分节（「这台机的 4 个腔位」折叠在一节），在设备域落地后
自然获得——本域不预做。

