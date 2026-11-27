-- MDS 设备域 V8（v2 重写，2026-11-24）：状态快照 / 设备子单元增强 / 机台组 / 自定义属性
-- v2 设计：
--   * 子单元 cim_equipment_unit 按 E120 判据不变（MODULE/SUBSYSTEM）；增强：
--     - LOAD_PORT 端口行为属性（E87 口径）：carrier_category（CASSETTE/FOUP/FOSB/
--       MAGAZINE/OTHER，6/8/12 寸载具差异用属性表达）、load_purpose（BOTH/INPUT/OUTPUT）。
--     - MODULE 可挂 contamination_level（污染等级，前道交叉污染控制，行业 1-5 级，可空）。
--     - capacity 通用化：不再 LOAD_PORT 专用，BUFFER（机台内缓存位）同样维护容量。
--   * 机台组 cim_tool_group（ISA-95 Work Center）：派工候选池——组绑定机型
--     （class_code），成员守门同机型才能入组（ToolGroupAdminService）；组带编辑锁三列。
--   * 自定义属性 cim_equipment_attr（对标行业 Customer Defined Attribute）：
--     KV 扩展骨架，一期只建表+实体，页面入口二期。
--   * 词表走平台字典（Java 种子 MdsDictSeedService——SQL 种子引 sys_dict 时序必炸）。
--   * H2 内存库重启即清 + 种子重跑，v2 直接重写本迁移（不做兼容）。

ALTER TABLE cim_equipment ADD COLUMN state_code VARCHAR(32) NULL;

-- ---------------------------------------------------------------- 设备子单元
CREATE TABLE cim_equipment_unit (
    id                 VARCHAR(64)  NOT NULL,
    description        VARCHAR(512),
    version            BIGINT,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time        TIMESTAMP,
    event_time         TIMESTAMP,
    create_user        VARCHAR(64),
    event_user         VARCHAR(64),
    event_name         VARCHAR(128),
    event_comment      VARCHAR(512),
    trx_id             VARCHAR(64),
    tenant_id          VARCHAR(64),
    equipment_id       VARCHAR(64)  NOT NULL,
    unit_code          VARCHAR(64)  NOT NULL,
    unit_name          VARCHAR(128) NOT NULL,
    unit_class         VARCHAR(16)  NOT NULL,
    unit_type          VARCHAR(32)  NOT NULL,
    state_code         VARCHAR(32)  NULL,
    port_no            INTEGER      NULL,
    capacity           INTEGER      NULL,
    carrier_category   VARCHAR(16)  NULL,
    load_purpose       VARCHAR(16)  NULL,
    contamination_level VARCHAR(16) NULL,
    is_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_equipment_unit PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_unit ON cim_equipment_unit (equipment_id, unit_code, deleted);
CREATE INDEX idx_cim_unit_eqp ON cim_equipment_unit (equipment_id);

-- ---------------------------------------------------------------- 机台组（ISA-95 Work Center）
CREATE TABLE cim_tool_group (
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
    group_code      VARCHAR(64)  NOT NULL,
    group_name      VARCHAR(128) NOT NULL,
    class_code      VARCHAR(64)  NOT NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_tool_group PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_tool_group_code ON cim_tool_group (group_code, deleted);
CREATE INDEX idx_cim_tool_group_class ON cim_tool_group (class_code);

-- ---------------------------------------------------------------- 机台组成员
CREATE TABLE cim_tool_group_member (
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
    group_id        VARCHAR(64)  NOT NULL,
    equipment_id    VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_cim_tool_group_member PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_tool_group_member ON cim_tool_group_member (group_id, equipment_id, deleted);
CREATE INDEX idx_cim_tgm_eqp ON cim_tool_group_member (equipment_id);

-- ---------------------------------------------------------------- 设备自定义属性（KV 扩展骨架）
CREATE TABLE cim_equipment_attr (
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
    attr_key        VARCHAR(64)  NOT NULL,
    attr_value      VARCHAR(512) NULL,
    attr_type       VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    CONSTRAINT pk_cim_equipment_attr PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_attr ON cim_equipment_attr (equipment_id, attr_key, deleted);
CREATE INDEX idx_cim_attr_eqp ON cim_equipment_attr (equipment_id);

-- ---------------------------------------------------------------- 演示种子（设备状态快照）
UPDATE cim_equipment SET state_code = 'PRD' WHERE equipment_code = 'EQP-OHT-001';
UPDATE cim_equipment SET state_code = 'SBY' WHERE equipment_code = 'EQP-OHT-002';
UPDATE cim_equipment SET state_code = 'SBY' WHERE equipment_code = 'EQP-STK-001';
UPDATE cim_equipment SET state_code = 'PRD' WHERE equipment_code = 'EQP-LITHO-01';
UPDATE cim_equipment SET state_code = 'UDT' WHERE equipment_code = 'EQP-ETCH-01';

-- ---------------------------------------------------------------- 演示种子（子单元：工艺机台按 E120 结构挂 Port/Aligner/Chamber）
INSERT INTO cim_equipment_unit (id, version, deleted, create_time, create_user,
                                equipment_id, unit_code, unit_name, unit_class, unit_type,
                                state_code, port_no, capacity,
                                carrier_category, load_purpose, contamination_level, is_enabled)
VALUES ('seed-unit-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'LP1', '光刻机#1 载具端口 1', 'SUBSYSTEM', 'LOAD_PORT',
        'READY_TO_LOAD', 1, 25, 'CASSETTE', 'BOTH', NULL, TRUE),
       ('seed-unit-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'LP2', '光刻机#1 载具端口 2', 'SUBSYSTEM', 'LOAD_PORT',
        'READY_TO_UNLOAD', 2, 25, 'CASSETTE', 'BOTH', NULL, TRUE),
       ('seed-unit-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'AL1', '光刻机#1 预对准器', 'SUBSYSTEM', 'ALIGNER',
        'IDLE', NULL, NULL, NULL, NULL, NULL, TRUE),
       ('seed-unit-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'PM1', '光刻机#1 工艺腔 1', 'MODULE', 'PROCESS_CHAMBER',
        'EXECUTING', NULL, NULL, NULL, NULL, '1', TRUE),
       ('seed-unit-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-05', 'LP1', '刻蚀机#1 载具端口 1', 'SUBSYSTEM', 'LOAD_PORT',
        'TRANSFER_BLOCKED', 1, 25, 'CASSETTE', 'BOTH', NULL, TRUE),
       ('seed-unit-06', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-05', 'PM1', '刻蚀机#1 工艺腔 1', 'MODULE', 'PROCESS_CHAMBER',
        'DOWN', NULL, NULL, NULL, NULL, '2', TRUE),
       ('seed-unit-07', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-05', 'PM2', '刻蚀机#1 工艺腔 2', 'MODULE', 'PROCESS_CHAMBER',
        'IDLE', NULL, NULL, NULL, NULL, '2', TRUE);

-- ---------------------------------------------------------------- 演示种子（机台组：同机型派工池）
INSERT INTO cim_tool_group (id, version, deleted, create_time, create_user,
                            group_code, group_name, class_code,
                            is_enabled, is_occupied)
VALUES ('seed-tg-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'TG-LITHO', '光刻机台组', 'ET-LITHO', TRUE, FALSE),
       ('seed-tg-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'TG-ETCH', '刻蚀机台组', 'ET-ETCH', TRUE, FALSE);

INSERT INTO cim_tool_group_member (id, version, deleted, create_time, create_user,
                                   group_id, equipment_id)
VALUES ('seed-tgm-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-tg-01', 'seed-eqp-04'),
       ('seed-tgm-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-tg-02', 'seed-eqp-05');
