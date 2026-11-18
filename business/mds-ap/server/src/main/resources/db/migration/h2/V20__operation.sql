-- MDS V20：工序库 Operation（四层工艺域建模 O 层落地，2026-11-18 与用户定稿）
-- 行业对标（2026-11-18 调研：赛美特 PlantU P-T-R-O 四层建模、Camstar Operation+Spec+Routing
-- 版本化、通用 fab Process→Route→Step 三层口径、Applied step 级追溯）：
--   * P 产品层 / T 工艺平台层已有（V5/V6）；R 路线层 = cim_flow（版本四态即 Camstar 式
--     版本化 Routing）；本版补齐 O 工序层——独立主数据「工序库」，跨路线复用。
--   * 术语纪律（用户定稿「Operation/Step 双词」）：
--       Operation = 工序（独立主数据，本表）；Step = 路线内工序实例（cim_flow_step）。
--   * 工序库只收业务工序三类：PROCESS 加工 / INSPECT 检验 / REWORK 返工；
--     DECISION/SPLIT/MERGE/START/END 是路线结构工件，不进工序库。
--   * 生命周期沿用 Flow 同款四态：UNFROZEN → FROZEN → ACTIVE → DISABLED；
--     同版本族（op_family_code）唯一 ACTIVE，激活时旧 ACTIVE 自动降级 FROZEN。
--     op_code 全局唯一建后不可改（手填；工序是追溯锚点，不可重号）。
--   * 标准卡控属性 = cim_flow_step 字面面上移：recipe_ref/equip_type（守门须存在）/
--     qtime_min/max/rework_limit/hold_point/hold_reason/batch_mode/batch_size/
--     inspect_plan/sampling_rate（仅 INSPECT）。
--   * 12 寸预留（建列不启用，用户定稿）：reticle_ref 光罩 / test_program CP 测试程序 /
--     inhibit_group 污染互斥组 / npw_plan 监测片计划 / chamber_select 腔体约束。
--   * cim_flow_step 加 operation_code 引用列（可空）：节点引用工序库 + 局部覆盖
--     （引用即带出标准值，本路线内可覆盖）；守门：须存在且 ACTIVE。

-- ---------------------------------------------------------------- 工序库主数据
CREATE TABLE cim_operation (
    id              VARCHAR(64)   NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP,
    event_time      TIMESTAMP,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    op_code         VARCHAR(64)   NOT NULL,
    op_name         VARCHAR(128)  NOT NULL,
    op_type         VARCHAR(16)   NOT NULL,
    tech_code       VARCHAR(64),
    status          VARCHAR(16)   NOT NULL DEFAULT 'UNFROZEN',
    op_family_code  VARCHAR(64),
    version_no      INT           NOT NULL DEFAULT 1,
    recipe_ref      VARCHAR(64),
    equip_type      VARCHAR(64),
    qtime_min       INT,
    qtime_max       INT,
    rework_limit    INT,
    hold_point      BOOLEAN       NOT NULL DEFAULT FALSE,
    hold_reason     VARCHAR(256),
    batch_mode      VARCHAR(16),
    batch_size      INT,
    inspect_plan    VARCHAR(64),
    sampling_rate   DECIMAL(5,2),
    reticle_ref     VARCHAR(64),
    test_program    VARCHAR(64),
    inhibit_group   VARCHAR(64),
    npw_plan        VARCHAR(64),
    chamber_select  VARCHAR(64),
    remark          VARCHAR(512),
    CONSTRAINT pk_cim_operation PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_operation_code ON cim_operation (op_code, deleted);
CREATE INDEX idx_cim_operation_family ON cim_operation (op_family_code);
CREATE INDEX idx_cim_operation_status ON cim_operation (status);
CREATE INDEX idx_cim_operation_tech ON cim_operation (tech_code);

-- ---------------------------------------------------------------- 路线步骤引用工序
ALTER TABLE cim_flow_step ADD COLUMN operation_code VARCHAR(64);
CREATE INDEX idx_cim_flow_step_op ON cim_flow_step (operation_code);
