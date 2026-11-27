-- MDS 设备域 V7（v2 重写，2026-11-24 设备建模评审定稿）：EquipmentClass / Equipment / Capability + 关联 + 演示种子
-- v2 设计（ISA-95 Class—Instance—PhysicalAsset 三分 + 行业 CIM 设备页签对标）：
--   * EquipmentClass（机型模板）取代旧 EquipmentType：class 侧新增「默认约束」
--     （默认片径/载具类型/batch/标准产能），实例新建时带入、可覆盖——新机台上线
--     动线「模板先行 → 派生实例」的数据基础。
--   * Equipment（机台实例）：owner_user（责任人，IAM username 逻辑引用，接入前手填）；
--     control_mode（通信控制模式，SEMI E30 口径字典 mds.equip_control_mode）；
--     production_mode（生产/工程模式，工程机台不参与正常派工，字典 mds.equip_production_mode）；
--     资产四件套 vendor/model/serial/asset_no + install_date（ISA-95 PhysicalAsset 语义，
--     角色与资产分离：搬迁换机头角色不变资产变，追溯不断）；
--     实例约束 wafer_sizes/carrier_types（CSV 多值，6/8/12 寸通用性用多值表达，
--     不按寸硬编码）+ batch_min/max + standard_wph。
--   * 设备大类（kind_code）沿用 8 类口径：PROCESS/METRO/TEST/ASSY/MATERIAL/FACILITY/TOOL/OTHER；
--     细类（equip_class_code）挂 mds.equip_class 字典，从属关系在
--     EquipmentClassAdminService.KIND_CLASSES 守门。
--   * OHT 等车辆的动态状态（实时位置/电量/故障）归 MCS（SEMI E82），本库只存静态主数据。
--   * 位置引用用 location_type + location_id（挂主键）；校验：MATERIAL 绑 FAB/AREA/BAY，
--     其余 kind 绑 STAGE。
--   * 能力 = 字典 cim_capability + cim_equipment_capability 关联（ISA-95 Present Capability）。
--   * 编辑锁三列与全库主数据同款（§8.1「人 vs 人」互斥）；关联表不带锁。
--   * H2 内存库重启即清 + 种子重跑，v2 直接重写本迁移（不写补丁迁移，不做兼容）。

-- ---------------------------------------------------------------- 机型模板（EquipmentClass）
CREATE TABLE cim_equipment_class (
    id                    VARCHAR(64)  NOT NULL,
    description           VARCHAR(512),
    version               BIGINT,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time           TIMESTAMP,
    event_time            TIMESTAMP,
    create_user           VARCHAR(64),
    event_user            VARCHAR(64),
    event_name            VARCHAR(128),
    event_comment         VARCHAR(512),
    trx_id                VARCHAR(64),
    tenant_id             VARCHAR(64),
    class_code            VARCHAR(64)  NOT NULL,
    class_name            VARCHAR(128) NOT NULL,
    kind_code             VARCHAR(16)  NOT NULL,
    equip_class_code      VARCHAR(16)  NULL,
    default_wafer_sizes   VARCHAR(64)  NULL,
    default_carrier_types VARCHAR(128) NULL,
    default_batch_min     INTEGER      NULL,
    default_batch_max     INTEGER      NULL,
    default_standard_wph  DECIMAL(10,2) NULL,
    remark                VARCHAR(512),
    is_enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied           BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by           VARCHAR(64)  NULL,
    occupied_at           TIMESTAMP    NULL,
    CONSTRAINT pk_cim_equipment_class PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_class_code ON cim_equipment_class (class_code, deleted);
CREATE INDEX idx_cim_equipment_class_kind ON cim_equipment_class (kind_code);

-- ---------------------------------------------------------------- 机台实例（Equipment）
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
    class_code      VARCHAR(64)  NOT NULL,
    location_type   VARCHAR(16)  NOT NULL,
    location_id     VARCHAR(64)  NOT NULL,
    owner_user      VARCHAR(64)  NULL,
    control_mode    VARCHAR(16)  NULL,
    production_mode VARCHAR(16)  NOT NULL DEFAULT 'PRODUCTION',
    vendor          VARCHAR(128) NULL,
    model_no        VARCHAR(128) NULL,
    serial_no       VARCHAR(128) NULL,
    asset_no        VARCHAR(64)  NULL,
    install_date    DATE         NULL,
    wafer_sizes     VARCHAR(64)  NULL,
    carrier_types   VARCHAR(128) NULL,
    batch_min       INTEGER      NULL,
    batch_max       INTEGER      NULL,
    standard_wph    DECIMAL(10,2) NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_equipment PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_equipment_code ON cim_equipment (equipment_code, deleted);
CREATE INDEX idx_cim_equipment_class ON cim_equipment (class_code);
CREATE INDEX idx_cim_equipment_location ON cim_equipment (location_type, location_id);
CREATE INDEX idx_cim_equipment_owner ON cim_equipment (owner_user);

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

-- ---------------------------------------------------------------- 演示种子（机型模板，默认约束随模板给）
INSERT INTO cim_equipment_class (id, version, deleted, create_time, create_user,
                                 class_code, class_name, kind_code, equip_class_code,
                                 default_wafer_sizes, default_carrier_types,
                                 default_batch_min, default_batch_max, default_standard_wph,
                                 is_enabled, is_occupied)
VALUES ('seed-eqpclass-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-OHT', 'OHT 天车', 'MATERIAL', 'OHT',
        NULL, NULL, NULL, NULL, NULL, TRUE, FALSE),
       ('seed-eqpclass-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-OHS', 'OHS 轨道', 'MATERIAL', 'OHS',
        NULL, NULL, NULL, NULL, NULL, TRUE, FALSE),
       ('seed-eqpclass-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-STOCKER', 'Stocker 存储', 'MATERIAL', 'STOCKER',
        NULL, NULL, NULL, NULL, NULL, TRUE, FALSE),
       ('seed-eqpclass-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-OHB', 'OHB 缓存', 'MATERIAL', 'OHB',
        NULL, NULL, NULL, NULL, NULL, TRUE, FALSE),
       ('seed-eqpclass-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-NTB', 'NTB 缓存', 'MATERIAL', 'NTB',
        NULL, NULL, NULL, NULL, NULL, TRUE, FALSE),
       ('seed-eqpclass-11', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-LITHO', '光刻机', 'PROCESS', 'LITHO',
        '150', 'CASSETTE', 1, 1, 40.00, TRUE, FALSE),
       ('seed-eqpclass-12', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-ETCH', '刻蚀机', 'PROCESS', 'ETCH',
        '150', 'CASSETTE', 1, 2, 60.00, TRUE, FALSE),
       ('seed-eqpclass-13', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'ET-CLEAN', '清洗机', 'PROCESS', 'CLEAN',
        '150', 'CASSETTE', 1, 3, 80.00, TRUE, FALSE);

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

-- ---------------------------------------------------------------- 演示种子（机台实例，位置引用 V4 种子；Owner 手填工号示范）
INSERT INTO cim_equipment (id, version, deleted, create_time, create_user,
                           equipment_code, equipment_name, class_code,
                           location_type, location_id,
                           owner_user, control_mode, production_mode,
                           vendor, model_no, serial_no,
                           wafer_sizes, carrier_types, batch_min, batch_max, standard_wph,
                           is_enabled, is_occupied)
VALUES ('seed-eqp-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-OHT-001', 'OHT 001 号车', 'ET-OHT',
        'AREA', 'seed-area-01',
        'pe.zhang', 'ONLINE_REMOTE', 'PRODUCTION',
        'Daifuku', 'OHT-V500', 'DFK-2026-0001',
        NULL, NULL, NULL, NULL, NULL,
        TRUE, FALSE),
       ('seed-eqp-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-OHT-002', 'OHT 002 号车', 'ET-OHT',
        'AREA', 'seed-area-02',
        'pe.zhang', 'ONLINE_REMOTE', 'PRODUCTION',
        'Daifuku', 'OHT-V500', 'DFK-2026-0002',
        NULL, NULL, NULL, NULL, NULL,
        TRUE, FALSE),
       ('seed-eqp-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-STK-001', '一号 Stocker', 'ET-STOCKER',
        'BAY', 'seed-bay-01',
        'pe.liu', 'ONLINE_REMOTE', 'PRODUCTION',
        'Murata', 'STK-OH8', 'MRT-2026-0101',
        NULL, NULL, NULL, NULL, NULL,
        TRUE, FALSE),
       ('seed-eqp-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-LITHO-01', '光刻机 #1', 'ET-LITHO',
        'STAGE', 'seed-stage-01',
        'pe.wang', 'ONLINE_LOCAL', 'PRODUCTION',
        'Canon', 'FPA-552i', 'CN-2026-0777',
        '150', 'CASSETTE', 1, 1, 40.00,
        TRUE, FALSE),
       ('seed-eqp-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQP-ETCH-01', '刻蚀机 #1', 'ET-ETCH',
        'STAGE', 'seed-stage-02',
        'pe.chen', 'ONLINE_LOCAL', 'ENGINEERING',
        'LAM', 'Versys-660', 'LAM-2026-0331',
        '150', 'CASSETTE', 1, 2, 60.00,
        TRUE, FALSE);

-- ---------------------------------------------------------------- 演示种子（设备↔能力）
INSERT INTO cim_equipment_capability (id, version, deleted, create_time, create_user,
                                      equipment_id, capability_code)
VALUES ('seed-eqpcap-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-01', 'CAP-E84'),
       ('seed-eqpcap-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-01', 'CAP-DUAL-PORT'),
       ('seed-eqpcap-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-03', 'CAP-E84'),
       ('seed-eqpcap-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-04', 'CAP-6INCH'),
       ('seed-eqpcap-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-05', 'CAP-6INCH'),
       ('seed-eqpcap-06', 0, FALSE, CURRENT_TIMESTAMP, 'seed', 'seed-eqp-05', 'CAP-CLEAN');
