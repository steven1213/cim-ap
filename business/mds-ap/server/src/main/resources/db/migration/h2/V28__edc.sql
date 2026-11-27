-- MDS V28：EDC 工程数采主数据建模（2026-11-26 与用户定稿，需求文档 + 行业调研）
-- 边界（对标 SEMI E134/E125/E5、SiView SM、Opcenter DC、myCIM EDC）：
--   MDS 只定义「采集契约」静态元数据——采什么/在哪采/按什么规则采/超限怎么办；
--   不收运行时数据、不做判定执行（归 MES/SPC/FDC 运行域）。
-- 关键行业红线（调研定稿）：
--   * 参数是产品无关的语义锚点（编码/名称/类型/单位），【不带限值】——
--     规格限挂在「参数 × 产品 × 工序」上，本模型落在 cim_edc_plan_item
--     （= Measurement Spec 层），同一参数不同产品/工序可差异化管控。
--   * 参数模板 = 统一复用 cim_param_def（V22 字典，本版加 unit 列），不另建平行表。
--   * SVID/CEID 是设备接口元数据：显式报告模板（Report Template）承载 SVID→参数
--     列表（= GEM 报告结构，S2F33/35 由 EAP 按此推导；RPTID 是会话态标识不落 MDS），
--     设备接口映射只做「机台 × CEID → 报告模板」绑定（一个 CEID 可绑多模板，
--     一个模板可被多机台/事件复用）；型号级映射 + 机台覆盖（SEMI 惯例）留后续版本。
--   * EDC 方案四态生命周期镜像 Flow（UNFROZEN→FROZEN→ACTIVE→DISABLED）：
--     activate 自动降级同族旧 ACTIVE；明细仅 UNFROZEN 可编辑（发布数据只读）。

-- ---------------------------------------------------------------- 参数定义扩展
ALTER TABLE cim_param_def ADD COLUMN unit VARCHAR(32);

-- ---------------------------------------------------------------- 参数集（打包层）
CREATE TABLE cim_edc_param_set (
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
    set_code        VARCHAR(64)  NOT NULL,
    set_name        VARCHAR(128) NOT NULL,
    op_code         VARCHAR(64)  NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_edc_param_set PRIMARY KEY (id),
    CONSTRAINT uk_edc_param_set UNIQUE (set_code, deleted)
);

CREATE TABLE cim_edc_param_set_item (
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
    set_id          VARCHAR(64)  NOT NULL,
    attr_key        VARCHAR(64)  NOT NULL,
    sort_no         INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_edc_param_set_item PRIMARY KEY (id),
    CONSTRAINT uk_edc_param_set_item UNIQUE (set_id, attr_key, deleted)
);
CREATE INDEX idx_edc_set_item_set ON cim_edc_param_set_item (set_id);

-- ---------------------------------------------------------------- 报告模板（GEM 报告结构显式化）
CREATE TABLE cim_edc_report_tpl (
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
    tpl_code        VARCHAR(64)  NOT NULL,
    tpl_name        VARCHAR(128) NOT NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_edc_report_tpl PRIMARY KEY (id),
    CONSTRAINT uk_edc_report_tpl UNIQUE (tpl_code, deleted)
);

CREATE TABLE cim_edc_report_tpl_item (
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
    tpl_id          VARCHAR(64)  NOT NULL,
    svid            INT          NOT NULL,
    attr_key        VARCHAR(64)  NOT NULL,
    sort_no         INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_edc_report_tpl_item PRIMARY KEY (id),
    CONSTRAINT uk_edc_report_tpl_item UNIQUE (tpl_id, svid, deleted)
);
CREATE INDEX idx_edc_tpl_item_tpl ON cim_edc_report_tpl_item (tpl_id);

-- ---------------------------------------------------------------- 设备接口映射（机台 × CEID → 报告模板）
CREATE TABLE cim_edc_equip_map (
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
    ceid            INT          NOT NULL,
    tpl_id          VARCHAR(64)  NOT NULL,
    source_channel  VARCHAR(16)  NOT NULL DEFAULT 'GEM',
    CONSTRAINT pk_cim_edc_equip_map PRIMARY KEY (id),
    CONSTRAINT uk_edc_equip_map UNIQUE (equipment_code, ceid, tpl_id, deleted)
);
CREATE INDEX idx_edc_equip_map_eqp ON cim_edc_equip_map (equipment_code);

-- ---------------------------------------------------------------- EDC 方案（聚合层，版本化）
CREATE TABLE cim_edc_plan (
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
    plan_code        VARCHAR(64)  NOT NULL,
    version_no       INT          NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'UNFROZEN',
    product_code     VARCHAR(64)  NULL,
    flow_family_code VARCHAR(64)  NOT NULL,
    flow_version_no  INT          NOT NULL,
    op_code          VARCHAR(64)  NOT NULL,
    set_code         VARCHAR(64)  NULL,
    activated_at     TIMESTAMP    NULL,
    activated_by     VARCHAR(64)  NULL,
    is_occupied      BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by      VARCHAR(64)  NULL,
    occupied_at      TIMESTAMP    NULL,
    CONSTRAINT pk_cim_edc_plan PRIMARY KEY (id),
    CONSTRAINT uk_edc_plan UNIQUE (plan_code, version_no, deleted)
);
CREATE INDEX idx_edc_plan_family ON cim_edc_plan (plan_code, status);

CREATE TABLE cim_edc_plan_item (
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
    plan_id          VARCHAR(64)  NOT NULL,
    attr_key         VARCHAR(64)  NOT NULL,
    collect_mode     VARCHAR(16)  NOT NULL DEFAULT 'AUTO',
    sample_rule      VARCHAR(16)  NOT NULL DEFAULT 'BY_LOT',
    interval_minutes INT          NULL,
    required         BOOLEAN      NOT NULL DEFAULT FALSE,
    target           DECIMAL(18,6) NULL,
    lsl              DECIMAL(18,6) NULL,
    usl              DECIMAL(18,6) NULL,
    ooc_action       VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    sort_no          INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_edc_plan_item PRIMARY KEY (id),
    CONSTRAINT uk_edc_plan_item UNIQUE (plan_id, attr_key, deleted)
);
CREATE INDEX idx_edc_plan_item_plan ON cim_edc_plan_item (plan_id);
