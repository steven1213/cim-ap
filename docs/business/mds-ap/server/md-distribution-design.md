# MDS 主数据分发与订阅（Master Data Distribution / Subscription）设计

> 本文是 MDS 的**对外输出机制**——定义 MDS 如何把主数据**可靠地**交付给 MES / EAP / AMHS / WMS / SPC / 门户。
>
> **补的缺口**：此前只在 [constraint-design §4.7](constraint-design.md) 提过 `compileRulePack()` 这一个**点状能力**，[equipment-interface-design §8](equipment-interface-design.md) 提过 EAP 拉点表。但 MDS 作为**主数据源**，**缺一套通用的「消费者注册 / 订阅 / 发布批次 / 增量 / 回执」机制**——导致下游要么全量拉、要么各自实现同步逻辑。
>
> 设计原则延续全篇：**MDS 拥有分发契约与发布批次；下游持有本地缓存；MDS 不承担下游的实时查询**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么需要分发机制（核心）

| 反模式 | 后果 |
| --- | --- |
| 下游每请求回查 MDS | MDS 成为性能瓶颈；派工热路径被主数据查询拖垮 |
| 下游各自实现全量同步 | 同步逻辑重复、口径不一、漏同步导致数据不一致 |
| 无版本/无回执 | 无法回答"下游用的是哪一版主数据"；出问题无法定位 |
| 全量推送 | 数据量大、网络与存储浪费 |

> **结论（MD1）**：MDS 需要一套**发布—订阅（Publish/Subscribe）**机制：**版本化的发布批次（Release）+ 快照（Snapshot）+ 增量变更（Delta）+ 投递回执（ACK）**。这与 IAM「本地验签 + 减少回查」的解耦思路一致（[README §1](README.md)）。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 消费者注册表 | **MDS** | `mds_md_consumer` |
| 订阅配置（订什么域/范围） | **MDS** | `mds_md_subscription` |
| 发布批次与快照 | **MDS** | `mds_md_release` + `_item` |
| 投递回执状态 | **MDS** | `mds_md_delivery` |
| **本地缓存与求值** | **下游** | MES/EAP/AMHS 各持本地副本 |
| **缓存失效与刷新** | **下游** | 按 MDS 推送/版本比对自行刷新 |
| **网络传输通道** | **平台/MQ** | MDS 产出消息，通道由平台提供（M4 的 mq 待办） |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 订阅 ↔ 域 | `mds_md_subscription.domain_code → mds_md_domain.domain_code` | [md-governance-design §3.1](md-governance-design.md) |
| 发布项 ↔ 各域实体 | `mds_md_release_item.entity_type/entity_ref` | 全篇各域 |
| 变更 ↔ 发布 | ECN 生效触发增量发布 | [change-mgmt-design §4.5](change-mgmt-design.md) |
| 约束规则包 | `domain_code=CONSTRAINT` 的特例 | [constraint-design §4.7](constraint-design.md) |
| 设备点表 | `domain_code=EQUIP_INTERFACE`，EAP 订阅 | [equipment-interface-design §8](equipment-interface-design.md) |
| SPC 规则集 | `domain_code=SAMPLING_SPC`，SPC 系统订阅 | [sampling-spc-design §8](sampling-spc-design.md) |
| 编码规则 | `domain_code=NAMING_RULE`，供生成端订阅 | [naming-rule-design](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISA-95 / IEC 62264** | 层级间主数据同步需版本化与一致性保障 | 发布批次 + 校验和 |
| **MDM 分发实践** | Master Data Distribution 采用 hub-and-spoke：中心发布、消费方订阅、按需增量 | 本域整体模型 |
| **发布订阅（Pub/Sub）模式** | 主题（domain）+ 订阅（subscription）+ 回执（ack） | `_consumer` / `_subscription` / `_delivery` |
| **SEMI E30 / EAP 集成** | EAP 需按设备型号加载点表并感知改版 | `EQUIP_INTERFACE` 域订阅（[equipment-interface-design](equipment-interface-design.md)） |
| **数据一致性（幂等）** | 增量消息需幂等（重复投递不产生副作用） | `release_item.op_type` + `entity_ref` + `revision` 幂等键 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Consumer / 消费者** | 订阅 MDS 主数据的下游系统 | 不是"用户"；是系统级消费者 |
| **Subscription / 订阅** | 消费者 × 域 × 范围的订阅关系 | 不是"消息队列订阅"；含**主数据范围**语义 |
| **Release / 发布批次** | 一次版本化的主数据交付（含范围与校验和） | 不是"软件发布"；是**数据批次** |
| **Snapshot / 快照** | 某批次的全量数据（首次或基线） | 与 Delta 相对 |
| **Delta / 增量** | 相对上一批次的变更集 | 增量基于 `@History` 的变更日志 |
| **Delivery / 投递** | 批次对某消费者的交付记录与回执 | `ACK`=已确认；`NACK`=失败重试 |
| **Transfer Mode** | `PUSH`（MDS 推）/`PULL`（下游拉） | 由消费者能力决定 |
| **幂等键** | `(entity_type, entity_ref, version)` | 重复投递按此去重 |

---

## 3. 实体总览与 ER

```
mds_md_consumer (消费者: 系统类型 / 端点 / 协议)
        │ consumer_id
        └──< mds_md_subscription (订阅: domain_code + scope + transfer_mode)

mds_md_release (发布批次: domain_code + scope + version + checksum)
        ├──< mds_md_release_item (内容项: entity_type/entity_ref/op_type/version)
        └──< mds_md_delivery    (投递回执: consumer_id + status ACK/NACK)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 消费者 | `mds_md_consumer` | 下游系统注册 |
| 订阅 | `mds_md_subscription` | 订什么、怎么传 |
| 发布批次 | `mds_md_release` | 版本化交付批次 |
| 批次内容项 | `mds_md_release_item` | 逐条实体变更 |
| 投递回执 | `mds_md_delivery` | 每消费者的确认状态 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_md_consumer`（消费者）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `consumer_code` | VARCHAR(64) | NOT NULL | 消费者代码（如 `MES-PROD`/`EAP-LITHO`/`AMHS`） |
| `consumer_name` | VARCHAR(128) | | 名称 |
| `system_type` | VARCHAR(24) | NOT NULL | `MES`/`EAP`/`AMHS`/`WMS`/`SPC`/`CMMS`/`PORTAL`/`ERP`/`OTHER` |
| `endpoint` | VARCHAR(256) | | 回调地址 / 队列主题（PUSH 用） |
| `protocol` | VARCHAR(24) | | `HTTP_CALLBACK`/`KAFKA`/`MQTT`/`DATABASE`/`FILE` |
| `transfer_mode` | VARCHAR(8) | NOT NULL DEFAULT 'PULL' | `PUSH`/`PULL` |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`/`SUSPENDED`/`INACTIVE` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(consumer_code, tenant_id, deleted)`。

### 3.2 `mds_md_subscription`（订阅）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `consumer_id` | VARCHAR(32) | NOT NULL, FK→`mds_md_consumer.id` | 消费者 |
| `domain_code` | VARCHAR(32) | NOT NULL, FK→`mds_md_domain.domain_code` | 订阅的域 |
| `scope_type` | VARCHAR(24) | | 范围（`GLOBAL`/`SITE`/`AREA`/`EQUIPMENT_CLASS`/`PRODUCT_FAMILY`…） |
| `scope_ref` | VARCHAR(128) | | 范围目标（如某厂区/某设备类） |
| `include_deleted` | BIT | DEFAULT 0 | 是否接收删除事件 |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用 |
| `last_ack_release` | VARCHAR(32) | | 最近已确认批次（断点续传锚点） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(consumer_id, domain_code, scope_type, scope_ref, tenant_id, deleted)`。
- **范围订阅**是关键：EAP-LITHO 只订 `EQUIP_INTERFACE` 域中 `EQUIPMENT_CLASS=SCANNER` 的点表，而非全域。

### 3.3 `mds_md_release`（发布批次）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `release_code` | VARCHAR(64) | NOT NULL | 批次号（如 `REL-20260923-0007`） |
| `domain_code` | VARCHAR(32) | NOT NULL, FK→`mds_md_domain.domain_code` | 所属域 |
| `scope_type`/`scope_ref` | VARCHAR(24)/VARCHAR(128) | | 范围 |
| `release_type` | VARCHAR(16) | NOT NULL | `SNAPSHOT`（全量基线）/`DELTA`（增量） |
| `revision` | VARCHAR(32) | NOT NULL | 批次版本（单调递增，供下游比对） |
| `item_count` | INT | | 条目数 |
| `checksum` | VARCHAR(128) | | 内容校验和（完整性校验） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`PUBLISHED`/`SUPERSEDED` |
| `published_at` | DATETIME(3) | | 发布时间 |
| `trigger_ref` | VARCHAR(128) | | 触发来源（`ECN-xxxx` / 手动 / 定时） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(release_code, tenant_id, deleted)`；`(domain_code, scope_type, scope_ref, revision, tenant_id, deleted)`。

### 3.4 `mds_md_release_item`（批次内容项）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `release_id` | VARCHAR(32) | NOT NULL, FK→`mds_md_release.id` | 批次 |
| `entity_type` | VARCHAR(32) | NOT NULL | 实体类型（如 `EQUIPMENT`/`ROUTE_OPERATION`） |
| `entity_ref` | VARCHAR(128) | NOT NULL | 实体引用（id/code） |
| `op_type` | VARCHAR(8) | NOT NULL | `UPSERT`/`DELETE` |
| `entity_revision` | VARCHAR(32) | | 实体版本（幂等键组成部分） |
| `payload_ref` | VARCHAR(256) | | 载荷位置（避免大字段入库，可指向对象存储/文件） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(release_id, entity_type, entity_ref, tenant_id, deleted)`。
- **幂等键** = `(entity_type, entity_ref, entity_revision)`：下游按此去重，重复投递无害。

### 3.5 `mds_md_delivery`（投递回执）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `release_id` | VARCHAR(32) | NOT NULL, FK→`mds_md_release.id` | 批次 |
| `consumer_id` | VARCHAR(32) | NOT NULL, FK→`mds_md_consumer.id` | 消费者 |
| `status` | VARCHAR(16) | NOT NULL | `PENDING`/`DELIVERED`/`ACK`/`NACK`/`EXPIRED` |
| `delivered_at` | DATETIME(3) | | 投递时间 |
| `acked_at` | DATETIME(3) | | 确认时间 |
| `retry_count` | INT | DEFAULT 0 | 重试次数 |
| `error_msg` | VARCHAR(512) | | 失败原因 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(release_id, consumer_id, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 MD1–MD6）

### 4.1 发布—订阅模型（MD2）
- MDS 是**唯一数据源**（hub），下游是 spoke；
- 下游按 `system_type` 与业务需要**按域 + 范围订阅**（不是全量）；
- `transfer_mode` 支持 PUSH（MDS 主动推，需 `endpoint`）与 PULL（下游按版本拉）。

### 4.2 快照 + 增量两段式（MD3）
```
首次接入 / 基线重建：
   MDS 生成 SNAPSHOT 批次（全量，含校验和）→ 下游全量加载
日常运行：
   变更发生（各域写入 / ECN 生效）
        → MDS 聚合为 DELTA 批次（版本 +1）
        → 按订阅投递（PUSH）或等下游拉取（PULL）
        → 下游按幂等键应用 → 回 ACK
断点续传：
   subscription.last_ack_release 记录锚点；下游重连从锚点+1 拉取
```
- **DELTA 来源**：各域的 `@History(SNAPSHOT)` 变更日志（平台既有能力）——**不额外造轮子**。

### 4.3 范围订阅与载荷外置（MD4）
- `scope_type/scope_ref` 让下游只拿自己关心的子集（EAP 只要自己厂区的设备点表）；
- `release_item.payload_ref` 把大载荷外置（对象存储/文件），表内只存引用——**与 [recipe-design](recipe-design.md) 的 `body_ref`、[document-design](document-design.md) 的 `doc_ref` 同范式**。

### 4.4 回执、超时与重试（MD5）
- 每批次对每消费者生成一条 `mds_md_delivery`；
- `PENDING` 超时未 `ACK` → 重试（`retry_count`）→ 超过阈值 `EXPIRED` + 告警；
- **未 ACK 的批次可查**：运维能立刻回答"哪台下游还停在哪个版本"。

### 4.5 与变更治理联动（MD6）
- [change-mgmt-design §4.5](change-mgmt-design.md) 的 ECN 生效 → **自动触发 DELTA 发布**（`trigger_ref=ECN-xxx`）；
- 使"变更已生效"与"下游已同步"形成闭环，避免"改了没下发"。

### 4.6 特例：规则包与点表（MD6 续）
以下"点状能力"统一收敛为本机制的特例，**不另建通道**：

| 特例 | 域（domain_code） | 消费者 | 见 |
| --- | --- | --- | --- |
| 约束规则包 `compileRulePack()` | `CONSTRAINT` | MES/EAP | [constraint-design §4.7](constraint-design.md) |
| 设备点表 | `EQUIP_INTERFACE` | EAP | [equipment-interface-design §8](equipment-interface-design.md) |
| SPC 规则集 | `SAMPLING_SPC` | SPC | [sampling-spc-design §8](sampling-spc-design.md) |
| 编码规则 | `NAMING_RULE` | 生成端 | [naming-rule-design](naming-rule-design.md) |
| 码表/单位 | `REF_DATA` | 前端 + 各系统 | [uom-dict-design §8](uom-dict-design.md) |

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 分发实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | **增量变更的数据来源**（本机制的核心依赖） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 + 批次号序列（`mds_naming_seq`） |
| M4 mq（待办） | PUSH 通道（Kafka/MQTT/HTTP）由平台消息能力承载 |
| 主历成对迁移 | 结构迁移 + 历史表 + 消费者种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_md_consumer (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  consumer_code VARCHAR(64) NOT NULL,
  consumer_name VARCHAR(128),
  system_type   VARCHAR(24) NOT NULL,
  endpoint      VARCHAR(256),
  protocol      VARCHAR(24),
  transfer_mode VARCHAR(8) NOT NULL DEFAULT 'PULL',
  status        VARCHAR(16) NOT NULL,
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_consumer PRIMARY KEY (id),
  CONSTRAINT uq_mds_consumer UNIQUE (consumer_code, tenant_id, deleted)
);

CREATE TABLE mds_md_subscription (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  consumer_id      VARCHAR(32) NOT NULL,
  domain_code      VARCHAR(32) NOT NULL,
  scope_type       VARCHAR(24),
  scope_ref        VARCHAR(128),
  include_deleted  BIT DEFAULT 0,
  is_enabled       BIT DEFAULT 1,
  last_ack_release VARCHAR(32),
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_subscription PRIMARY KEY (id),
  CONSTRAINT uq_mds_subscription UNIQUE (consumer_id, domain_code, scope_type, scope_ref, tenant_id, deleted),
  CONSTRAINT fk_sub_consumer FOREIGN KEY (consumer_id) REFERENCES mds_md_consumer (id)
);
CREATE INDEX idx_sub_domain ON mds_md_subscription (domain_code);

CREATE TABLE mds_md_release (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  release_code VARCHAR(64) NOT NULL,
  domain_code  VARCHAR(32) NOT NULL,
  scope_type   VARCHAR(24),
  scope_ref    VARCHAR(128),
  release_type VARCHAR(16) NOT NULL,
  revision      VARCHAR(32) NOT NULL,
  item_count   INT,
  checksum     VARCHAR(128),
  status       VARCHAR(16) NOT NULL,
  published_at DATETIME(3),
  trigger_ref  VARCHAR(128),
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_release PRIMARY KEY (id),
  CONSTRAINT uq_mds_release_code UNIQUE (release_code, tenant_id, deleted)
);
CREATE INDEX idx_release_domain ON mds_md_release (domain_code, status);

CREATE TABLE mds_md_release_item (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  release_id     VARCHAR(32) NOT NULL,
  entity_type    VARCHAR(32) NOT NULL,
  entity_ref     VARCHAR(128) NOT NULL,
  op_type        VARCHAR(8) NOT NULL,
  entity_revision VARCHAR(32),
  payload_ref    VARCHAR(256),
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_release_item PRIMARY KEY (id),
  CONSTRAINT uq_mds_release_item UNIQUE (release_id, entity_type, entity_ref, tenant_id, deleted),
  CONSTRAINT fk_relitem_release FOREIGN KEY (release_id) REFERENCES mds_md_release (id)
);

CREATE TABLE mds_md_delivery (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  release_id   VARCHAR(32) NOT NULL,
  consumer_id  VARCHAR(32) NOT NULL,
  status       VARCHAR(16) NOT NULL,
  delivered_at DATETIME(3),
  acked_at     DATETIME(3),
  retry_count  INT DEFAULT 0,
  error_msg    VARCHAR(512),
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_delivery PRIMARY KEY (id),
  CONSTRAINT uq_mds_delivery UNIQUE (release_id, consumer_id, tenant_id, deleted),
  CONSTRAINT fk_delivery_release FOREIGN KEY (release_id) REFERENCES mds_md_release (id),
  CONSTRAINT fk_delivery_consumer FOREIGN KEY (consumer_id) REFERENCES mds_md_consumer (id)
);
CREATE INDEX idx_delivery_status ON mds_md_delivery (status);
```

> 历史表 `mds_md_consumer_hist` / `mds_md_subscription_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.distribution
  ├── entity
  │   ├── MdConsumer.java             # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── MdSubscription.java          # @Entity 继承 BaseDefData
  │   ├── MdRelease.java               # @Entity 继承 BaseDefData
  │   ├── MdReleaseItem.java           # @Entity 继承 BaseDefData
  │   ├── MdDelivery.java              # @Entity 继承 BaseDefData
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── MdSubscriptionRepository.java
  │   └── MdReleaseRepository.java
  ├── service
  │   ├── ReleaseService.java           # 生成 SNAPSHOT/DELTA 批次；版本 +1；校验和
  │   ├── DeliveryService.java           # 投递/重试/回执处理；断点续传锚点
  │   ├── SubscriptionResolver.java       # 按 domain+scope 找出订阅者
  │   └── DeltaCollector.java             # 从各域 @History 采集增量（域适配器）
  └── web
      └── DistributionController.java     # 消费者/订阅/批次/回执查询与重投
```

---

## 8. 待补 / 后续

- **域适配器（DeltaCollector）**：每域一个适配器，把 `@History` 变更映射为 `release_item`（含实体版本与载荷生成）。
- **载荷格式约定**：每域定义标准 JSON 载荷 schema（供下游解析）；可与 [equipment-interface-design §8](equipment-interface-design.md) 的点表导出共用。
- **PUSH 通道**：依赖平台 M4 的 mq 能力（Kafka/MQTT/HTTP 回调）。
- **一致性与对账**：定期比对下游版本与 MDS 版本，发现"未同步"告警（用 `_delivery` + `last_ack_release`）。
- **与既有文档闭环**：本文为 [constraint-design §4.7](constraint-design.md) 的规则包、[equipment-interface-design §8](equipment-interface-design.md) 的点表、[sampling-spc-design §8](sampling-spc-design.md) 的 SPC 规则集、[uom-dict-design §8](uom-dict-design.md) 的码表提供**统一交付通道**；增量来源为各域 `@History(SNAPSHOT)`（平台 design §2）；触发方为 [change-mgmt-design §4.5](change-mgmt-design.md) 的 ECN 生效；域登记见 [md-governance-design §3.1](md-governance-design.md)。
