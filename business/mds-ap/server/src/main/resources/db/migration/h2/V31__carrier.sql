-- MDS 载具主数据 V31（2026-11-27，行业对照缺口①：Carrier/Cassette 台账）
-- 设计口径：
--   * 载具 = 晶圆/光罩在 AMHS 全链（OHT → Stocker/OHB/NTB → 机台）中的物理载体，行业标准名
--     Carrier / Cassette（SEMI E87 CMS Carrier 口径），光罩盒对应 Reticle Carrier。
--   * 载具号（carrier_id）是 MCS/MES 全链追踪的主键口径——本表即主数据落点：
--     号码/类型/状态/所属库/内容物绑定。E87 的运行时状态机（AccessingState/CSPort）归 MCS，
--     主数据只存台账级状态（在库/使用中/清洗中/维护中/报废，字典 mds.carrier_status）。
--   * home_equipment_id = 归属存储设备（MATERIAL 大类：Stocker/OHB/NTB），宽口径同 Bank；
--     current_lot_id = 内容物绑定（MES 批次号字符串引用，批主数据在 MES，主数据不 FK）。
--   * carrier_code 全局唯一、建后不可改（全库跨域引用口径）；带全库同款编辑锁三列。

-- ---------------------------------------------------------------- 载具（Carrier/Cassette）
CREATE TABLE cim_carrier (
    id                VARCHAR(64)  NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    carrier_code      VARCHAR(64)  NOT NULL,
    carrier_name      VARCHAR(128) NOT NULL,
    carrier_type      VARCHAR(32)  NULL,
    status            VARCHAR(32)  NULL,
    home_equipment_id VARCHAR(64)  NULL,
    current_lot_id    VARCHAR(64)  NULL,
    wafer_qty         INT          NULL,
    is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied       BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by       VARCHAR(64)  NULL,
    occupied_at       TIMESTAMP    NULL,
    CONSTRAINT pk_cim_carrier PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_carrier ON cim_carrier (carrier_code, deleted);
CREATE INDEX idx_cim_carrier_home ON cim_carrier (home_equipment_id);

-- ---------------------------------------------------------------- 演示种子（一号 Stocker 载具）
INSERT INTO cim_carrier (id, version, deleted, create_time, create_user,
                         carrier_code, carrier_name, carrier_type, status,
                         home_equipment_id, wafer_qty, is_enabled, is_occupied)
VALUES ('seed-carrier-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'C-0001', '一号库·标准花篮 01', 'CASSETTE', 'IN_STOCK', 'seed-eqp-03', 25, TRUE, FALSE),
       ('seed-carrier-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'C-0002', '一号库·标准花篮 02', 'CASSETTE', 'IN_STOCK', 'seed-eqp-03', 25, TRUE, FALSE),
       ('seed-carrier-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'RC-0001', '一号库·光罩盒 01', 'RETICLE_BOX', 'IN_USE', 'seed-eqp-03', NULL, TRUE, FALSE);
