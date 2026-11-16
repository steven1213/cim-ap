-- MDS 设备域 V9：Stocker 存储区（Bank）+ Bank 间转运规则
-- 设计（2026-11-13 讨论定稿，行业口径 SEMI E88 Zone / E82 / 厂内 Bank 管理惯例）：
--   * Bank 不是 SEMI 标准术语——标准对应物是 E88 的 zone（5.2.30）：ZoneName / ZoneType /
--     ZoneSize / ZoneCapacity；各家 MCS 把 zone 本地命名为 Bank。本表即 zone 的主数据落点。
--   * 层级：Stocker（cim_equipment，MATERIAL 大类）→ Bank（本表）→ Shelf 单库位。
--     Shelf 不建模（E88 实务口径：设备黑箱 + 区级容量即可，秒级移动不进主数据）；
--     剩余容量（ZoneCapacity）是运行时状态归 MCS，主数据只存 capacity（总储位数）。
--   * bank_code 在所属设备内唯一（E88 ZoneName 作用域 = 单台 Stocker）；跨库引用走 id。
--   * bank_type 挂业务用途字典 mds.stocker_bank_type（原料/WIP/成品/不良/控片/回收/普通）——
--     MES 统计与派工真正吃的口径；E88 物理口径 ZoneType(Shelf/Port) 不建模（Port 已是设备 Port）。
--   * cim_bank_transfer_rule = Bank 间转运 allowlist 路由表：E88 6.1.1「目的地由 Host 指定」、
--     6.1.2「容量校验责任在 Host」——本表即 Host 侧路由主数据；from≠to 守门，跨 Stocker 允许
--     （跨库=换巷道，物理上必经 OHT/OHS，属正常路径）。priority 沿 E88 Priority 1-99 口径。
--   * 规则表轻量不带编辑锁三列（与 cim_equipment_capability 同口径）；Bank 表带全库同款锁三列。
--   * Bank In/Out 批次状态、Bank Period（≤6 个月）、Bank Quota = MES 业务域，本期不建。

-- ---------------------------------------------------------------- Stocker 存储区（Bank）
CREATE TABLE cim_stocker_bank (
    id              VARCHAR(64)  NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP,
    event_time      TIMESTAMP,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    equipment_id    VARCHAR(64)  NOT NULL,
    bank_code       VARCHAR(64)  NOT NULL,
    bank_name       VARCHAR(128) NOT NULL,
    bank_type       VARCHAR(32)  NULL,
    capacity        INT          NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_stocker_bank PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_stocker_bank ON cim_stocker_bank (equipment_id, bank_code, deleted);
CREATE INDEX idx_cim_stocker_bank_eqp ON cim_stocker_bank (equipment_id);

-- ---------------------------------------------------------------- Bank 间转运规则
CREATE TABLE cim_bank_transfer_rule (
    id              VARCHAR(64)  NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP,
    event_time      TIMESTAMP,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    from_bank_id    VARCHAR(64)  NOT NULL,
    to_bank_id      VARCHAR(64)  NOT NULL,
    priority        INT          NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_bank_transfer_rule PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_bank_transfer_rule ON cim_bank_transfer_rule (from_bank_id, to_bank_id, deleted);
CREATE INDEX idx_cim_bank_rule_from ON cim_bank_transfer_rule (from_bank_id);
CREATE INDEX idx_cim_bank_rule_to ON cim_bank_transfer_rule (to_bank_id);

-- ---------------------------------------------------------------- 演示种子（二号 Stocker，跨库规则演示用）
INSERT INTO cim_equipment (id, version, deleted, create_time, create_user,
                           equipment_code, equipment_name, type_code,
                           location_type, location_id,
                           is_enabled, is_occupied)
VALUES ('seed-eqp-06', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-STK-002', '二号 Stocker', 'ET-STOCKER',
        'BAY', 'seed-bay-02', TRUE, FALSE);

-- ---------------------------------------------------------------- 演示种子（存储区 Bank）
INSERT INTO cim_stocker_bank (id, version, deleted, create_time, create_user,
                              equipment_id, bank_code, bank_name, bank_type, capacity,
                              is_enabled, is_occupied)
VALUES ('seed-bank-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-03', 'BK-A01', '一号库·原料区', 'RAW', 120, TRUE, FALSE),
       ('seed-bank-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-03', 'BK-W01', '一号库·WIP 区', 'WIP', 80, TRUE, FALSE),
       ('seed-bank-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-03', 'BK-R01', '一号库·不良品区', 'SCRAP', 40, TRUE, FALSE),
       ('seed-bank-11', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-06', 'BK-F01', '二号库·成品区', 'FG', 100, TRUE, FALSE),
       ('seed-bank-12', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-06', 'BK-A02', '二号库·原料区', 'RAW', 60, TRUE, FALSE);

-- ---------------------------------------------------------------- 演示种子（转运规则，含跨库）
INSERT INTO cim_bank_transfer_rule (id, version, deleted, create_time, create_user,
                                    from_bank_id, to_bank_id, priority, is_enabled)
VALUES ('seed-bankrule-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-bank-01', 'seed-bank-02', 10, TRUE),
       ('seed-bankrule-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-bank-02', 'seed-bank-11', 20, TRUE),
       ('seed-bankrule-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-bank-12', 'seed-bank-01', 30, TRUE);
