-- MDS 设备域 V8：状态快照 / 设备子单元
-- 设计（2026-11-13 讨论定稿，行业口径 SEMI E120 CEM / E87 / E157 / E10）：
--   * Kind→Type→Equipment 三层模板：SEMI 不定义 Kind/Type，属 MES 自建两层模板+实例。
--     kind_code（8 大类）/equip_class_code（49 细类）已随 V7 建表，页面归属由 kind 推导：
--     MATERIAL→搬运设备页，其余→设备页（Process Tool / Non-Process Tool 两分法）。
--   * 状态=「词表+当前快照」：设备级 state_code（mds.equip_state，E10 六态口径
--     PRD/SBY/ENG/SDT/UDT/NST）；子单元 state_code（mds.unit_state，E87 端口态 /
--     E157 腔态 / E116 EPT 简化合并）。状态「流转」与告警归 EAP·SECS 运行时域，
--     本库不建流水表——快照先手工维护，后续接 EAP 事件推送。
--   * 子单元 cim_equipment_unit 按 E120 判据（「主动改变/测量晶圆 且 受 recipe 控制」
--     =MODULE，否则 SUBSYSTEM）：unit_class=MODULE（工艺腔）/ SUBSYSTEM（LoadPort、
--     Aligner、Robot、LoadLock 等）。unit_type 是具体形态枚举。
--   * 子单元不设独立编辑锁三列：写操作守门父设备编辑锁（与能力关联同口径）；
--     随设备删除物理级联（软删会撞 (equipment_id, unit_code, deleted) 唯一索引）。
--   * 词表走平台字典（Java 种子 MdsDictSeedService——MDS 迁移 V1-999 排在
--     system V1000 建表之前，SQL 种子引 sys_dict 必炸）。

ALTER TABLE cim_equipment ADD COLUMN state_code VARCHAR(32) NULL;

-- ---------------------------------------------------------------- 设备子单元
CREATE TABLE cim_equipment_unit (
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
    unit_code       VARCHAR(64)  NOT NULL,
    unit_name       VARCHAR(128) NOT NULL,
    unit_class      VARCHAR(16)  NOT NULL,
    unit_type       VARCHAR(32)  NOT NULL,
    state_code      VARCHAR(32)  NULL,
    port_no         INTEGER      NULL,
    capacity        INTEGER      NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_equipment_unit PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_unit ON cim_equipment_unit (equipment_id, unit_code, deleted);
CREATE INDEX idx_cim_unit_eqp ON cim_equipment_unit (equipment_id);

UPDATE cim_equipment SET state_code = 'PRD' WHERE equipment_code = 'EQP-OHT-001';
UPDATE cim_equipment SET state_code = 'SBY' WHERE equipment_code = 'EQP-OHT-002';
UPDATE cim_equipment SET state_code = 'SBY' WHERE equipment_code = 'EQP-STK-001';
UPDATE cim_equipment SET state_code = 'PRD' WHERE equipment_code = 'EQP-LITHO-01';
UPDATE cim_equipment SET state_code = 'UDT' WHERE equipment_code = 'EQP-ETCH-01';

-- ---------------------------------------------------------------- 演示种子（子单元：工艺机台按 E120 结构挂 Port/Aligner/Chamber）
INSERT INTO cim_equipment_unit (id, version, deleted, create_time, create_user,
                                equipment_id, unit_code, unit_name, unit_class, unit_type,
                                state_code, port_no, capacity, is_enabled)
VALUES ('seed-unit-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'LP1', '光刻机#1 载具端口 1', 'SUBSYSTEM', 'LOAD_PORT',
        'READY_TO_LOAD', 1, 25, TRUE),
       ('seed-unit-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'LP2', '光刻机#1 载具端口 2', 'SUBSYSTEM', 'LOAD_PORT',
        'READY_TO_UNLOAD', 2, 25, TRUE),
       ('seed-unit-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'AL1', '光刻机#1 预对准器', 'SUBSYSTEM', 'ALIGNER',
        'IDLE', NULL, NULL, TRUE),
       ('seed-unit-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-04', 'PM1', '光刻机#1 工艺腔 1', 'MODULE', 'PROCESS_CHAMBER',
        'EXECUTING', NULL, NULL, TRUE),
       ('seed-unit-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-05', 'LP1', '刻蚀机#1 载具端口 1', 'SUBSYSTEM', 'LOAD_PORT',
        'TRANSFER_BLOCKED', 1, 25, TRUE),
       ('seed-unit-06', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-05', 'PM1', '刻蚀机#1 工艺腔 1', 'MODULE', 'PROCESS_CHAMBER',
        'DOWN', NULL, NULL, TRUE),
       ('seed-unit-07', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-eqp-05', 'PM2', '刻蚀机#1 工艺腔 2', 'MODULE', 'PROCESS_CHAMBER',
        'IDLE', NULL, NULL, TRUE);
