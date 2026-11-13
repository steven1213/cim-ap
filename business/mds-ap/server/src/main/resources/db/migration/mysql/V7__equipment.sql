-- MDS 设备域 V7：EquipmentType / Equipment / Capability + 关联 + 演示种子
-- 设计（2026-11-13 讨论定稿，行业口径 SiView / SEMI / ISA-95）：
--   * Kind→Type—Instance—Capability 层级（ISA-95 设备对象模型）。
--   * 设备大类（kind_code）按行业口径定稿 8 类：PROCESS（工艺）/ METRO（量测检测）/
--     TEST（测试）/ ASSY（封装）/ MATERIAL（物料搬运·仓储）/ FACILITY（厂务·含环境监控）/
--     TOOL（工装治具，行业属 Durable/Tooling 域，本期台账占位）/ OTHER。
--     页面归属由 kind 推导：MATERIAL→搬运设备页（AMHS 静态台账），其余→设备页。
--   * 细类（equip_class_code，如 ETCH/DEP/PROBER）是 kind 的二级分类（mds.equip_class
--     字典，49 项），从属关系在 EquipmentTypeAdminService.KIND_CLASSES 守门。
--   * OHT 等车辆的动态状态（实时位置/电量/故障）归 MCS（SEMI E82 车辆模型），
--     本库只存静态主数据，不建任何动态字段。
--   * 位置引用用 location_type + location_id（挂主键）：Area/Bay/Stage 编码是
--     组合唯一（fab+area 等），全局不唯一，单 code 引用有歧义。
--   * 位置校验（EquipmentAdminService）：kind=MATERIAL 绑 FAB/AREA/BAY
--     （OHT 挂线区、Stocker 挂 Bay）；其余 kind 绑 STAGE（工艺/量测/测试机台绑机台位）。
--   * 能力 = 字典 cim_capability + cim_equipment_capability 关联（ISA-95 Present
--     Capability 的首期落地；类型级默认能力 / Request Capability 匹配留待 MES 域）。
--   * 编辑锁三列与全库主数据同款（§8.1「人 vs 人」互斥）；关联表不带锁。

-- ---------------------------------------------------------------- 设备类型
CREATE TABLE cim_equipment_type (
    id               VARCHAR(64)  NOT NULL,
    description      VARCHAR(512),
    version          BIGINT,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time      TIMESTAMP,
    event_time       TIMESTAMP,
    create_user      VARCHAR(64),
    event_user       VARCHAR(64),
    event_name       VARCHAR(128),
    event_comment    VARCHAR(512),
    trx_id           VARCHAR(64),
    tenant_id        VARCHAR(64),
    type_code        VARCHAR(64)  NOT NULL,
    type_name        VARCHAR(128) NOT NULL,
    kind_code        VARCHAR(16)  NOT NULL,
    equip_class_code VARCHAR(16)  NULL,
    remark           VARCHAR(512),
    is_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied      BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by      VARCHAR(64)  NULL,
    occupied_at      TIMESTAMP    NULL,
    CONSTRAINT pk_cim_equipment_type PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_type_code ON cim_equipment_type (type_code, deleted);
CREATE INDEX idx_cim_equipment_type_kind ON cim_equipment_type (kind_code);

-- ---------------------------------------------------------------- 设备
CREATE TABLE cim_equipment (
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
    equipment_code  VARCHAR(64)  NOT NULL,
    equipment_name  VARCHAR(128) NOT NULL,
    type_code       VARCHAR(64)  NOT NULL,
    location_type   VARCHAR(16)  NOT NULL,
    location_id     VARCHAR(64)  NOT NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_equipment PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_code ON cim_equipment (equipment_code, deleted);
CREATE INDEX idx_cim_equipment_type ON cim_equipment (type_code);
CREATE INDEX idx_cim_equipment_location ON cim_equipment (location_type, location_id);

-- ---------------------------------------------------------------- 设备能力（字典）
CREATE TABLE cim_capability (
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
    capability_code VARCHAR(64)  NOT NULL,
    capability_name VARCHAR(128) NOT NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_capability PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_capability_code ON cim_capability (capability_code, deleted);

-- ---------------------------------------------------------------- 设备↔能力关联
CREATE TABLE cim_equipment_capability (
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
    capability_code VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_cim_equipment_capability PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_eqp_cap ON cim_equipment_capability (equipment_id, capability_code, deleted);
CREATE INDEX idx_cim_eqp_cap_cap ON cim_equipment_capability (capability_code);

-- ---------------------------------------------------------------- 演示种子（类型）
INSERT INTO cim_equipment_type (id, version, deleted, create_time, create_user,
                                type_code, type_name, kind_code, equip_class_code,
                                is_enabled, is_occupied)
VALUES ('seed-eqptype-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-OHT', 'OHT 天车', 'MATERIAL', 'OHT', TRUE, FALSE),
       ('seed-eqptype-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-OHS', 'OHS 轨道', 'MATERIAL', 'OHS', TRUE, FALSE),
       ('seed-eqptype-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-STOCKER', 'Stocker 存储', 'MATERIAL', 'STOCKER', TRUE, FALSE),
       ('seed-eqptype-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-OHB', 'OHB 缓存', 'MATERIAL', 'OHB', TRUE, FALSE),
       ('seed-eqptype-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-NTB', 'NTB 缓存', 'MATERIAL', 'NTB', TRUE, FALSE),
       ('seed-eqptype-11', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-LITHO', '光刻机', 'PROCESS', 'LITHO', TRUE, FALSE),
       ('seed-eqptype-12', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-ETCH', '刻蚀机', 'PROCESS', 'ETCH', TRUE, FALSE),
       ('seed-eqptype-13', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-CLEAN', '清洗机', 'PROCESS', 'CLEAN', TRUE, FALSE);

-- ---------------------------------------------------------------- 演示种子（能力）
INSERT INTO cim_capability (id, version, deleted, create_time, create_user,
                            capability_code, capability_name,
                            is_enabled, is_occupied)
VALUES ('seed-cap-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'CAP-6INCH', '6 英寸晶圆加工能力', TRUE, FALSE),
       ('seed-cap-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'CAP-E84', 'E84 接口能力', TRUE, FALSE),
       ('seed-cap-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'CAP-DUAL-PORT', '双端口并行搬运', TRUE, FALSE),
       ('seed-cap-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'CAP-CLEAN', '清洗工艺能力', TRUE, FALSE);

-- ---------------------------------------------------------------- 演示种子（设备，位置引用 V4 种子）
INSERT INTO cim_equipment (id, version, deleted, create_time, create_user,
                           equipment_code, equipment_name, type_code,
                           location_type, location_id,
                           is_enabled, is_occupied)
VALUES ('seed-eqp-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-OHT-001', 'OHT 001 号车', 'ET-OHT',
        'AREA', 'seed-area-01', TRUE, FALSE),
       ('seed-eqp-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-OHT-002', 'OHT 002 号车', 'ET-OHT',
        'AREA', 'seed-area-02', TRUE, FALSE),
       ('seed-eqp-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-STK-001', '一号 Stocker', 'ET-STOCKER',
        'BAY', 'seed-bay-01', TRUE, FALSE),
       ('seed-eqp-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-LITHO-01', '光刻机 #1', 'ET-LITHO',
        'STAGE', 'seed-stage-01', TRUE, FALSE),
       ('seed-eqp-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-ETCH-01', '刻蚀机 #1', 'ET-ETCH',
        'STAGE', 'seed-stage-02', TRUE, FALSE);

-- ---------------------------------------------------------------- 演示种子（设备↔能力）
INSERT INTO cim_equipment_capability (id, version, deleted, create_time, create_user,
                                      equipment_id, capability_code)
VALUES ('seed-eqpcap-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-01', 'CAP-E84'),
       ('seed-eqpcap-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-01', 'CAP-DUAL-PORT'),
       ('seed-eqpcap-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-03', 'CAP-E84'),
       ('seed-eqpcap-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-04', 'CAP-6INCH'),
       ('seed-eqpcap-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-05', 'CAP-6INCH'),
       ('seed-eqpcap-06', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-05', 'CAP-CLEAN');
