-- MDS 消耗品 BOM V38（2026-11-28，消耗品规格/BOM 建模定稿第二期）
-- 设计口径（docs/business/mds-ap/server/consumable-bom-design.md）：
--   * 消耗品 BOM = 「工艺路线 × 消耗品规格」的消耗契约：头表绑定上下文
--     产品 + 流程 + 工序（+ 机台可选，腔体级覆盖），行表定义消耗规则
--     （固定用量 / 基于工艺参数的变量消耗）。库存扣减、齐套运算、FEFO 拣配
--     归 MES/ERP 运行时，MDS 只交付唯一、版本化、可硬校验的基准数据。
--   * 头状态机：DRAFT ──激活──▶ ACTIVE ──激活新版──▶ SUPERSEDED（自动置位，
--     只读追溯）；ACTIVE 手动停用 ──▶ DISABLED。铁律：ACTIVE 不回 DRAFT、
--     SUPERSEDED/DISABLED 不可删。同上下文（产品×流程×工序×机台）任意时刻
--     最多一个 ACTIVE（应用层守门，激活新版时旧 ACTIVE 自动 SUPERSEDED）。
--   * source_op_version 记录激活时工序 version_no——Recipe/工序版本联动锚点，
--     工序升版后页面展示「版本漂移」告警（ECN 治理归变更管理，MDS 不硬联动）。
--   * 行全量替换维护（页内行编辑一次提交，同检验标准 V33 模式）；删除头级联
--     清行——同事务先删后插必 flush。spec 引用守门：必须 ACTIVE 且
--     material_kind=CONSUMABLE；VARIABLE 行 param_key 必须在 cim_param_def
--     注册且启用。消耗类型/用量基准/失效策略挂业务字典。
--   * bom_code 走 NamingRule CONS_BOM 对象占号，全局唯一建后不可改；
--     version_no 为同上下文激活序号（激活时计算 max+1）。头表带编辑锁三列，
--     行表轻量不带锁。

-- ---------------------------------------------------------------- 消耗品 BOM（头）
CREATE TABLE cim_cons_bom (
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
    bom_code          VARCHAR(64)  NOT NULL,
    bom_name          VARCHAR(128) NOT NULL,
    product_id        VARCHAR(64)  NOT NULL,
    flow_id           VARCHAR(64)  NOT NULL,
    operation_id      VARCHAR(64)  NOT NULL,
    equipment_id      VARCHAR(64)  NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    version_no        INT          NOT NULL DEFAULT 0,
    source_op_version INT          NULL,
    is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied       BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by       VARCHAR(64)  NULL,
    occupied_at       TIMESTAMP    NULL,
    CONSTRAINT pk_cim_cons_bom PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_cons_bom_code ON cim_cons_bom (bom_code, deleted);
CREATE INDEX idx_cim_cons_bom_ctx ON cim_cons_bom (product_id, flow_id, operation_id, equipment_id, deleted);
CREATE INDEX idx_cim_cons_bom_status ON cim_cons_bom (status);

-- ---------------------------------------------------------------- BOM 消耗明细行（全量替换）
CREATE TABLE cim_cons_bom_line (
    id                VARCHAR(64)    NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    bom_id            VARCHAR(64)    NOT NULL,
    spec_id           VARCHAR(64)    NOT NULL,
    consume_type      VARCHAR(16)    NOT NULL DEFAULT 'FIXED',
    basis             VARCHAR(16)    NOT NULL DEFAULT 'PER_LOT',
    std_qty           DECIMAL(18, 4) NOT NULL,
    tolerance_pct     DECIMAL(6, 3)  NULL,
    loss_factor       DECIMAL(8, 4)  NULL,
    param_key         VARCHAR(64)    NULL,
    param_qty_per_unit DECIMAL(18, 6) NULL,
    substitute_group  VARCHAR(64)    NULL,
    sub_priority      INT            NULL,
    expiry_policy     VARCHAR(16)    NULL,
    sort_no           INT            NULL,
    note              VARCHAR(512)   NULL,
    CONSTRAINT pk_cim_cons_bom_line PRIMARY KEY (id)
);
CREATE INDEX idx_cim_cons_bom_line_bom ON cim_cons_bom_line (bom_id, deleted);
CREATE UNIQUE INDEX uk_cim_cons_bom_line ON cim_cons_bom_line (bom_id, spec_id, deleted);
