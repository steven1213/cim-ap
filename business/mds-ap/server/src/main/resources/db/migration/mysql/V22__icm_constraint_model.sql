-- MDS V22：ICM 继承式约束模型落地（2026-11-25 与用户定稿，flow-modeling/20-our-design.md）
-- 设计要点（对标 Camstar/SiView 规格引用、myCIM 节点强类型参数 + 多层级 Context、
-- Applied 运行时校验——三家收敛点「工序级复用 + 节点级差异」）：
--   * ContextChain（MDS 主数据范围）：L0 OPERATION 工序默认 → L1 FLOW 流程覆盖 →
--     L2 STEP 节点覆盖（P3 预留 PRODUCT）；解析取最特异层（STEP > FLOW > OPERATION）
--     并标注来源。节点覆盖列与工序默认列逐一同名（V12-V21 已天然对齐），NULL = 继承。
--   * cim_equip_assign 统一指派表：scope_type=OPERATION/FLOW/STEP 三用——同一张表/
--     同一套服务/前端组件/Excel 模板覆盖全链（P3 加 PRODUCT 只扩枚举）。
--     ⚠️ 唯一键 NULL 陷阱：上下文三列 NOT NULL DEFAULT '' 空串占位，唯一键才真实生效
--     （cim_tool_group_member 同款经验）。
--   * 资格门 vs 候选池：cim_flow_step.equip_type（ET-*）= 资格门硬约束；
--     cim_equip_assign = 候选池（派工清单），解析取交集，ASSIGN_MATCHES_EQUIP_TYPE 守门。
--   * 生效闸：cim_flow.validate_state（NOT_VALID/VALID/INVALID）= activate 硬前置；
--     明细落 cim_flow_validate_result（rule_code 表驱动可启停，severity ERROR/WARN）。
--   * 编辑锁三列（评审定稿）：cim_flow 原无锁，画布保存是「XML 整份回传覆盖」——
--     两人同时编辑互相静默覆盖，全系统并发风险最高点。锁管「人 vs 人」，
--     四态管「版本生命周期」，两层独立叠加。
--   * 多方案/多场景不加列：同产品多工艺方案 = 多 flow family（ENG-*/HVM- 命名约定）；
--     rework_of_family 仅作「返工流程」标注列（跳转时机归 MES，MDS 只管定义与筛选）。
--   * stage_code 站点分组轴（P2 填数据，拍板项 G=P2）。

-- ---------------------------------------------------------------- cim_flow 增强
ALTER TABLE cim_flow ADD COLUMN validate_state  VARCHAR(16) NOT NULL DEFAULT 'NOT_VALID';
ALTER TABLE cim_flow ADD COLUMN validate_time   TIMESTAMP;
ALTER TABLE cim_flow ADD COLUMN validate_user   VARCHAR(64);
ALTER TABLE cim_flow ADD COLUMN validate_msg    VARCHAR(1024);
ALTER TABLE cim_flow ADD COLUMN stage_code      VARCHAR(64);
ALTER TABLE cim_flow ADD COLUMN rework_of_family VARCHAR(64);
ALTER TABLE cim_flow ADD COLUMN is_occupied     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cim_flow ADD COLUMN occupied_by     VARCHAR(64);
ALTER TABLE cim_flow ADD COLUMN occupied_at     TIMESTAMP;
CREATE INDEX idx_cim_flow_validate ON cim_flow (validate_state);
CREATE INDEX idx_cim_flow_stage ON cim_flow (stage_code);

-- ---------------------------------------------------------------- 校验明细（rule_code 表驱动）
CREATE TABLE cim_flow_validate_result (
    id           VARCHAR(64)  NOT NULL,
    description  VARCHAR(512),
    version      BIGINT,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time  TIMESTAMP,
    event_time   TIMESTAMP,
    create_user  VARCHAR(64),
    event_user   VARCHAR(64),
    event_name   VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id       VARCHAR(64),
    tenant_id    VARCHAR(64),
    flow_id      VARCHAR(64)  NOT NULL,
    step_id      VARCHAR(64),
    rule_code    VARCHAR(64)  NOT NULL,
    severity     VARCHAR(16)  NOT NULL,
    message      VARCHAR(512) NOT NULL,
    CONSTRAINT pk_cim_flow_validate_result PRIMARY KEY (id)
);
CREATE INDEX idx_fvr_flow ON cim_flow_validate_result (flow_id);

-- ---------------------------------------------------------------- 统一指派表（ICM 核心）
CREATE TABLE cim_equip_assign (
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
    scope_type      VARCHAR(16)  NOT NULL,
    operation_code  VARCHAR(64)  NOT NULL DEFAULT '',
    flow_id         VARCHAR(64)  NOT NULL DEFAULT '',
    step_id         VARCHAR(64)  NOT NULL DEFAULT '',
    assign_type     VARCHAR(16)  NOT NULL,
    assign_ref      VARCHAR(64)  NOT NULL,
    chamber_rule    VARCHAR(512),
    recipe_ref      VARCHAR(64),
    recipe_rule     VARCHAR(16)  NOT NULL DEFAULT 'FIXED',
    recipe_match    VARCHAR(512),
    priority        INT          NOT NULL DEFAULT 100,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(512),
    CONSTRAINT pk_cim_equip_assign PRIMARY KEY (id),
    CONSTRAINT uk_ea UNIQUE (scope_type, operation_code, flow_id, step_id, assign_type, assign_ref, priority, deleted)
);
CREATE INDEX idx_ea_scope ON cim_equip_assign (scope_type, operation_code, flow_id, step_id);
CREATE INDEX idx_ea_ref ON cim_equip_assign (assign_type, assign_ref);

-- ---------------------------------------------------------------- 节点覆盖列（NULL=继承工序默认）
ALTER TABLE cim_flow_step ADD COLUMN reticle_ref      VARCHAR(256);
ALTER TABLE cim_flow_step ADD COLUMN contam_level     VARCHAR(16);
ALTER TABLE cim_flow_step ADD COLUMN contam_forbidden VARCHAR(128);

-- ---------------------------------------------------------------- 参数定义字典
CREATE TABLE cim_param_def (
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
    attr_key        VARCHAR(64)  NOT NULL,
    attr_name       VARCHAR(128) NOT NULL,
    group_code      VARCHAR(64)  NOT NULL,
    value_type      VARCHAR(16)  NOT NULL,
    enum_dict       VARCHAR(64),
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    scopes          VARCHAR(64)  NOT NULL DEFAULT 'STEP',
    sort_no         INT          NOT NULL DEFAULT 100,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_param_def PRIMARY KEY (id),
    CONSTRAINT uk_param_def UNIQUE (attr_key, deleted)
);

-- ---------------------------------------------------------------- KV 值表（ContextChain 泛化）
CREATE TABLE cim_constraint_attr (
    id             VARCHAR(64)   NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time    TIMESTAMP,
    event_time     TIMESTAMP,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    context_type   VARCHAR(16)   NOT NULL,
    operation_code VARCHAR(64)   NOT NULL DEFAULT '',
    flow_id        VARCHAR(64)   NOT NULL DEFAULT '',
    step_id        VARCHAR(64)   NOT NULL DEFAULT '',
    attr_key       VARCHAR(64)   NOT NULL,
    attr_value     VARCHAR(1024),
    CONSTRAINT pk_cim_constraint_attr PRIMARY KEY (id),
    CONSTRAINT uk_cattr UNIQUE (context_type, operation_code, flow_id, step_id, attr_key, deleted)
);
CREATE INDEX idx_cattr_scope ON cim_constraint_attr (context_type, operation_code, flow_id, step_id);

-- ---------------------------------------------------------------- 产品↔流程绑定
CREATE TABLE cim_product_flow (
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
    product_code        VARCHAR(64) NOT NULL,
    flow_family_code    VARCHAR(64) NOT NULL,
    flow_version_no     INT         NOT NULL,
    bind_state          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    active_time         TIMESTAMP,
    active_user         VARCHAR(64),
    is_occupied     BOOLEAN NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64),
    occupied_at     TIMESTAMP,
    CONSTRAINT pk_cim_product_flow PRIMARY KEY (id),
    CONSTRAINT uk_cim_product_flow UNIQUE (product_code, flow_family_code, flow_version_no, deleted)
);
CREATE INDEX idx_pf_product ON cim_product_flow (product_code);
CREATE INDEX idx_pf_family ON cim_product_flow (flow_family_code, flow_version_no);
