# MDS 仓库主数据设计（Bank / Stocker / 储位）

> 本文是 MDS 第七块核心主数据——**仓库（Bank）/ 自动储位（Stocker）/ 缓冲库**的设计。
> 它与[位置主数据](location-design.md)（物理落点）、[载具主数据](carrier-design.md)（FOUP 归属库位 `home_stocker`）、[工艺路线](route-design.md)（工序缓冲 bank）、[设备设计](equipment-design.md)（邻 bay 储位）强相关，是 AMHS 物料存储的**主数据侧锚点**。

---

## 0. 决策摘要

### 0.1 边界与状态归属（MDS vs 下游）

| 维度 | 拥有方 | 存储位置 | 说明 |
| --- | --- | --- | --- |
| Bank **定义**（类型 / 物理落点 / 容量 / 槽位目录 / AMHS 端口） | **MDS 主数据** | `mds_bank` 等 | 本文范围 |
| Bank **行政态** `admin_status` | **MDS 主数据** | `mds_bank` | AVAILABLE/MAINTENANCE/DISABLED/SCRAPPED |
| Bank **实时占用**（当前槽位上有哪些 carrier） | **AMHS / MES** | AMHS 实时域 | MDS 不存 |
| Carrier **当前物理位置**（在哪个 bank/槽位/设备） | **AMHS / MES** | AMHS 实时域 | MDS 仅存 `home_stocker_code`（归属库位，静态） |
| AMHS 传输态 / 端口访问态（E87） | **AMHS / EAP** | AMHS 实时域 | 端口状态定义可复用设备端口状态机（§4.4） |

> **设计哲学（与设备/路线/产品一致）**：MDS 拥有「定义 + 静态归属 + 行政态」，实时态与实例归下游 AMHS/MES。Bank 不重复维护实时占用。

### 0.2 闭环映射

| 关系 | 引用键 | 指向 | 见 |
| --- | --- | --- | --- |
| Bank ↔ 位置（物理落点） | `mds_bank.location_id → mds_location.id` | 位置层级（通常 BAY/SUBBAY，中央库可为 FAB） | [位置设计 §2](location-design.md) |
| Bank → 所属工艺区（派生） | 经 `location_id` 上溯 `mds_location_closure` 到 `level=AREA` | 该 bank 服务的工艺域 | [位置设计 §2.3](location-design.md) |
| **Bank ↔ 载具（归属库位）** | `mds_carrier.home_stocker_code → mds_bank.code`（**修正自原 mds_location**，见 §5） | Bank 主数据 | [载具设计 §0.1](carrier-design.md) |
| Bank ↔ 载具类型（可收类型） | `mds_bank_carrier_type_compat.carrier_type_id` | 载具类型（污染隔离） | [载具设计 §3.3](carrier-design.md) |
| Bank ↔ 工序（缓冲 bank，可选） | `mds_route_operation.out_bank_code → mds_bank.code`（设计期可选项） | 工序产出缓冲库 | [路线设计 §3.3](route-design.md) |
| Bank ↔ 设备（邻 bay 储位） | 同 BAY/SUBBAY 的 `mds_equipment.location_id` 与 `mds_bank.location_id` | 经位置关联 | [设备设计 §2.4](equipment-design.md) |

---

## 1. 行业依据

| 来源 | 实践 | 本文采用 |
| --- | --- | --- |
| **SEMI E87（Carrier Management / AMHS）** | Stocker 是 AMHS 拓扑节点，有端口与槽位，carrier 经端口出入库；Stocker 与 Equipment 共享端口状态语义 | Bank 有 `mds_bank_port`，端口状态机**复用**设备端口 E157 状态目录（§4.4） |
| **SEMI E15 / E28.1（载具 / 物料控制）** | 载具归属某 stocker（home stocker）；wafer_size 与 stocker 兼容 | `home_stocker_code → mds_bank`，bank 含 `wafer_size` |
| **fab MES / AMHS 实践** | Bank 分工艺内缓冲库（IPB）、工序间缓冲、成品库（FG Bank）、中央库、邻 bay 储位；中央库常跨 Area 服务 | `bank_type` 枚举区分（§2） |
| **fab 库存 MDM 实践** | 槽位为规划资源（slots），实时占用由 AMHS 调度；Bank 可限制可收载具类型（污染隔离/尺寸） | `mds_bank_slot`（目录）+ `mds_bank_carrier_type_compat` |
| **与既有 MDS 一致** | 位置层级是地理从属（SITE>FAB>AREA>BAY>SUBBAY）；Bank 是放在这个地理结构里的**存储资源**，不是层级 | Bank 为**独立实体**引用 location，不污染 `level` 枚举（§4.1） |

---

## 2. 术语澄清

- **Bank**：广义「仓库 / 存储点」，可指逻辑 holding point（lot 在此等待），也可指物理存储设施。
- **Stocker**：300mm 晶圆厂的**自动化**存储设施（OHT/Stocker 控制器接管），是 Bank 的自动化实例；200mm 多为人工 Bank。本文以 `mds_bank` 统一承载，`amhs_enabled=1` 即 Stocker。
- **Bay Stocker / 邻 bay 储位**：紧贴设备 bay 的小容量储位，用于工序间短缓冲（减少 OHT 长距离搬运）。
- **IPB（In-Process Bank）**：工艺段内缓冲库；**FG Bank（Finished Goods Bank）**：成品/待发运库。
- **Slot（槽位）**：Stocker 内的物理 Carrier 存放位；MDS 维护**槽位目录（规划）**，实时占用归 AMHS。

---

## 3. 实体与表结构

### 3.1 `mds_bank`（仓库 / Stocker 主记录）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | 租户 |
| `code` | VARCHAR(64) | NOT NULL | 仓库代码（如 `STK-LITH-B01`），同租户全局唯一（T2.5） |
| `name` | VARCHAR(128) | | 显示名 |
| `bank_type` | VARCHAR(16) | NOT NULL | 见下方**权威枚举清单**（晶圆类 + 资产类，后三类为跨域复用） |

**`bank_type` 权威枚举清单（2026-09-23 全量补齐）**：

| bank_type | 语义 | 归属 |
| --- | --- | --- |
| `IN_PROCESS` | 工艺内缓冲库（IPB） | 本域 |
| `BUFFER` | 工序间缓冲库 | 本域 |
| `FINISHED_GOODS` | 成品库（FG Bank） | 本域 |
| `CENTRAL` | 中央库（跨 Area 服务） | 本域 |
| `BAY_STOCKER` | 邻 bay 自动储位 | 本域 |
| `RETICLE_STOCKER` | **光罩库** | 复用来源：[reticle-design §3.2](reticle-design.md) |
| `PROBE_CARD_STOCKER` | **探针卡库** | 复用来源：[test-asset-design §3.3](test-asset-design.md) |
| `TOOLING_STOCKER` | **工装库** | 复用来源：[tooling-design §3.1](tooling-design.md) |

> 资产类库同样适用本域的 `capacity`/`mds_bank_slot`（槽位目录）/`mds_bank_port`（AMHS 端口，复用 `PORT_STATE` 状态机）与 `admin_status`；不为其另建平行实体。
| `location_id` | VARCHAR(32) | NOT NULL, FK→`mds_location.id` | **物理落点**（BAY/SUBBAY；中央库可为 FAB） |
| `wafer_size` | INT | NOT NULL | 支持晶圆尺寸（200/300），与载具/设备尺寸一致 |
| `capacity` | INT | NOT NULL | 额定 Carrier 槽位数（规划值） |
| `amhs_enabled` | BIT | NOT NULL DEFAULT 0 | 是否接入 AMHS（=Stocker）；0=人工 Bank |
| `automatable` | BIT | NOT NULL DEFAULT 0 | 是否可自动搬运（OHT/AGV 可达） |
| `admin_status` | VARCHAR(16) | NOT NULL | `AVAILABLE`/`MAINTENANCE`/`DISABLED`/`SCRAPPED`（MDS 行政态） |
| `description` | VARCHAR(512) | | 基类 |
| `id`/`tenant_id`/`description`/`version`(乐观锁)/`deleted`/审计列 | — | | 继承 `BaseDefData`；乐观锁 `version` 由基类提供；`@History(SNAPSHOT)` |

- 唯一：`(code, tenant_id, deleted)`。
- **所属 AREA 派生**：不冗余存储 `area_code`；查询时经 `location_id` 上溯 `mds_location_closure` 取 `level=AREA` 祖先（与设备 `location_id` 同源，符合位置设计 D3）。
- `admin_status` 与 AMHS 实时占用**正交**：`AVAILABLE` 表示该库可接收 carrier，但其内部实时槽位占用由 AMHS 维护。

### 3.2 `mds_bank_port`（AMHS 接口端口，复用端口状态机）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | |
| `bank_id` | VARCHAR(32) | NOT NULL, FK→`mds_bank.id` | 所属库 |
| `port_code` | VARCHAR(64) | NOT NULL | 端口号 |
| `port_type` | VARCHAR(16) | NOT NULL | `IN`/`OUT`/`BIDIRECTIONAL` |
| `state_model_id` | VARCHAR(32) | FK→`mds_equipment_state_model.id` | **复用**端口状态机定义（`model_kind=PORT_STATE`，见[设备设计 §3.14.5](equipment-design.md)） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bank_id, port_code, tenant_id, deleted)`。
- 端口**实时状态**（NOTINIT/READY/NOTREADY/TRANSFERRING）归 AMHS/EAP；MDS 仅存其**状态机定义引用**（与设备 `mds_equipment_port` 同构，避免重复造轮子）。

### 3.3 `mds_bank_slot`（槽位目录定义）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | |
| `bank_id` | VARCHAR(32) | NOT NULL, FK→`mds_bank.id` | 所属库 |
| `slot_code` | VARCHAR(64) | NOT NULL | 槽位号 |
| `carrier_type_id` | VARCHAR(32) | FK→`mds_carrier_type.id` | 该槽位兼容的载具类型（可选） |
| `row_no`/`col_no`/`level_no` | INT | | 物理坐标（排/列/层），便于 AMHS 寻址 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bank_id, slot_code, tenant_id, deleted)`。
- **实时占用（哪个 carrier 在槽位）归 AMHS**，MDS 仅维护槽位**规划目录**（槽位是否可用、兼容类型）。

### 3.4 `mds_bank_carrier_type_compat`（Bank 可收载具类型，污染隔离）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | |
| `bank_id` | VARCHAR(32) | NOT NULL, FK→`mds_bank.id` | 所属库 |
| `carrier_type_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier_type.id` | 可收载具类型 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bank_id, carrier_type_id, tenant_id, deleted)`。
- 表达「该库能收哪些尺寸/类型的 FOUP」——污染隔离与尺寸一致性的第二道闸（第一道在载具 `carrier_type_compat` 决定可进 Area，本表决定可存 Bank）。

---

## 4. 关键设计点（拍板）

### 4.1 Bank 是「独立实体」，不是 location 的 `level`

- **不把 `BANK` 加进 `mds_location.level` 枚举**。理由：位置层级（`level`）表达的是**地理从属**（SITE>FAB>AREA>BAY>SUBBAY），Bank 是放在该地理结构里的**存储资源**，其本质与 EQUIPMENT 类似——都是「落位于位置层级、但自身不是位置层级」（参见[位置设计 §1.1](location-design.md) 中 EQUIPMENT 的处理）。
- Bank 通过 `location_id` 物理挂靠到 BAY/SUBBAY（或中央库挂 FAB），并复用闭包上溯 AREA。新增 Bank **零表结构变更**。
- 若未来确需把「存储区」作为位置层级（如独立 STORAGE 区），仍按 D1 扩展 `level` 枚举即可，不影响 Bank 实体设计。

### 4.2 三态分离（与载具/设备同构）

1. **Bank 行政态 `admin_status`**（MDS）：管「库是否可接收 carrier」。
2. **Bank 实时占用**（AMHS）：管「此刻哪些槽位被占、carrier 在哪」——MDS 不存。
3. **端口访问态**（AMHS，E87）：管「端口此刻能否传输」——MDS 仅存状态机定义引用。

三者正交：`admin_status=AVAILABLE` 的库可能因 AMHS 维护而临时所有端口 `NOTREADY`；`admin_status=MAINTENANCE` 的库整体禁收。

### 4.3 容量与槽位：规划 vs 实时

- `mds_bank.capacity` 是**规划额定值**（主数据）。
- `mds_bank_slot` 是**槽位目录**（规划资源），`row/col/level` 供 AMHS 寻址。
- 实时「已用/可用槽位数、某 carrier 占某槽」全部由 AMHS 实时维护，MDS 不存、不计算。派工/调度查询占用时回 AMHS。

### 4.4 端口状态机复用（关键：不重复造轮子）

- `mds_bank_port.state_model_id` 直接引用**设备设计 §3.14.5 的 `PORT_STATE` 状态机定义**（`mds_equipment_state_model` 的 `model_kind=PORT_STATE`）。
- Bank 端口与 Equipment 端口的**状态目录与合法迁移表完全一致**（NOTINIT→INIT→READY↔NOTREADY、READY→TRANSFERRING→READY…），共用同一套 `mds_equipment_state` / `mds_equipment_state_transition` 表。
- 实时端口态由 AMHS/EAP 维护并据 `transition` 校验（与设备端口同源）。

### 4.5 闭环（四向）

- **Bank → 位置**：`location_id` 落点 + 闭包上溯 AREA → 得知该库服务哪个工艺区。
- **Bank ↔ 载具**：`carrier.home_stocker_code → mds_bank.code`（归属库）；载具实时位置另由 AMHS 维护。
- **Bank ↔ 载具类型**：`bank_carrier_type_compat` 决定可收类型（尺寸/污染隔离）。
- **Bank ↔ 工序（可选）**：`route_operation.out_bank_code → mds_bank.code` 表达「该工序产出缓冲到哪个 bank」（设计期可选项，route 校验服务阶段再落地该列）。
- **Bank ↔ 设备**：bay stocker 与设备共享 BAY/SUBBAY 的 `location_id`，经位置自然关联。

### 4.6 软删 / 租户 / 主键（与平台一致）

- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

---

## 5. 与既有文档的衔接（修正点）

- **修正 `carrier.home_stocker_code`**：原[载具设计 §0.1](carrier-design.md) 将 `home_stocker_code → mds_location.code`（归属库位指向位置层级）。**现修正为指向 `mds_bank.code`**——home stocker 本质是 Bank/Stocker，应通过 Bank 主数据再经 `location_id` 落到位置，链路更清晰（`carrier.home_stocker_code → mds_bank.code → mds_bank.location_id → mds_location`）。已在[载具设计](carrier-design.md) 同步修订引用。
- **位置设计**：Bank 是「落位于位置层级的独立存储资源」，与 EQUIPMENT 处理同构（非 `level`）。
- **设备设计**：bay stocker 与设备经共享 `location_id`（BAY/SUBBAY）关联。
- **工艺路线**：`operation.out_bank_code`（预留）指向本设计的 `mds_bank`，作为工序缓冲 bank。

---

## 6. DDL（MySQL 示例，主历成对）

```sql
CREATE TABLE mds_bank (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  code          VARCHAR(64)  NOT NULL,
  name          VARCHAR(128),
  bank_type     VARCHAR(16)  NOT NULL,
  location_id   VARCHAR(32)  NOT NULL,
  wafer_size    INT          NOT NULL,
  capacity      INT          NOT NULL,
  amhs_enabled  BIT          NOT NULL DEFAULT 0,
  automatable   BIT          NOT NULL DEFAULT 0,
  admin_status  VARCHAR(16)  NOT NULL,
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_bank PRIMARY KEY (id),
  CONSTRAINT uq_mds_bank_code UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_bank_location FOREIGN KEY (location_id) REFERENCES mds_location (id)
);
CREATE INDEX idx_bank_location ON mds_bank (location_id);
CREATE INDEX idx_bank_type     ON mds_bank (bank_type);
CREATE INDEX idx_bank_status   ON mds_bank (admin_status);

CREATE TABLE mds_bank_port (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  bank_id       VARCHAR(32)  NOT NULL,
  port_code     VARCHAR(64)  NOT NULL,
  port_type     VARCHAR(16)  NOT NULL,
  state_model_id VARCHAR(32),
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_bank_port PRIMARY KEY (id),
  CONSTRAINT uq_mds_bank_port_code UNIQUE (bank_id, port_code, tenant_id, deleted),
  CONSTRAINT fk_bp_bank FOREIGN KEY (bank_id) REFERENCES mds_bank (id),
  CONSTRAINT fk_bp_state_model FOREIGN KEY (state_model_id) REFERENCES mds_equipment_state_model (id)
);
CREATE INDEX idx_bp_bank ON mds_bank_port (bank_id);

CREATE TABLE mds_bank_slot (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  bank_id       VARCHAR(32)  NOT NULL,
  slot_code     VARCHAR(64)  NOT NULL,
  carrier_type_id VARCHAR(32),
  row_no        INT,
  col_no        INT,
  level_no      INT,
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_bank_slot PRIMARY KEY (id),
  CONSTRAINT uq_mds_bank_slot_code UNIQUE (bank_id, slot_code, tenant_id, deleted),
  CONSTRAINT fk_bs_bank FOREIGN KEY (bank_id) REFERENCES mds_bank (id),
  CONSTRAINT fk_bs_carrier_type FOREIGN KEY (carrier_type_id) REFERENCES mds_carrier_type (id)
);
CREATE INDEX idx_bs_bank ON mds_bank_slot (bank_id);

CREATE TABLE mds_bank_carrier_type_compat (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  bank_id       VARCHAR(32)  NOT NULL,
  carrier_type_id VARCHAR(32) NOT NULL,
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_bank_ctc PRIMARY KEY (id),
  CONSTRAINT uq_mds_bank_ctc UNIQUE (bank_id, carrier_type_id, tenant_id, deleted),
  CONSTRAINT fk_bctc_bank FOREIGN KEY (bank_id) REFERENCES mds_bank (id),
  CONSTRAINT fk_bctc_carrier_type FOREIGN KEY (carrier_type_id) REFERENCES mds_carrier_type (id)
);
CREATE INDEX idx_bctc_bank ON mds_bank_carrier_type_compat (bank_id);

-- 历史表 mds_bank_hist / mds_bank_port_hist / mds_bank_slot_hist / mds_bank_carrier_type_compat_hist
-- 由 @History(SNAPSHOT) 在运行时自动生成（与平台 M3 一致）。
```

---

## 7. 代码结构

```
com.cim.mds.bank
├── entity
│   ├── Bank.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)；含 bank_type/location_id/admin_status
│   ├── BankPort.java           # @Entity 继承 BaseDefData（port_type + state_model_id 复用端口状态机）
│   ├── BankSlot.java           # @Entity 继承 BaseDefData（slot 目录）
│   └── BankCarrierTypeCompat.java # @Entity 继承 BaseDefData（可收载具类型）
├── repository
│   ├── BankRepository.java
│   ├── BankPortRepository.java
│   ├── BankSlotRepository.java
│   └── BankCarrierTypeCompatRepository.java
└── service
    ├── BankService.java          # 继承 AbstractJpaService（自动租户过滤 + @History 留痕）
    ├── BankPortService.java
    ├── BankSlotService.java
    ├── BankCarrierTypeCompatService.java
    └── BankValidator.java        # 跨实体校验：location_id 须存在；home_stocker 双向一致；
                                  #   wafer_size 与载具/设备一致；bank_carrier_type_compat 指向须存在
```

- `Bank.locationId`/`BankPort.stateModelId` 为**逻辑外键**（位置/状态机定义在不同模块上下文），参照完整性由 `BankValidator` 服务层校验（与既有主数据一致）。
- 历史表由 `@History(SNAPSHOT)` 自动生成，Bank 的增删改（含容量/类型变更）自动落 `mds_bank_hist`。

---

## 8. 待补 / 后续

- **MES/AMHS 实时态衔接契约**：Bank 的 `code`/`slot_code`/`port_code` 导出/订阅格式（供 AMHS 实时占用与端口态回写）；`home_stocker` 双向一致性规则。
- **route `out_bank_code` 落地**：在路线校验服务阶段补该列 + FK + 校验（本文 §0.2 / §4.5 已预留绑定语义）。
- **Bank 与 Equipment 邻近调度**：基于共享 `location_id`（同 BAY/SUBBAY）推导「工序设备 ↔ 邻 bay 储位」的搬运距离优化（排程侧）。
- **Bank 行政态 ↔ 容量联动**：`admin_status=MAINTENANCE` 时 AMHS 应禁止新 carrier 入该库（由 AMHS 侧消费 `admin_status`）。
