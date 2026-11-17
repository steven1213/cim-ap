-- MDS V16：原因码主数据 + WIP 事件快照示例（2026-11-17 用户确认范围）。
--   建模核心：「主数据可演进，事件事实不可变」——两层物理分离：
--   · cim_reason_code_type：语义域（码用于哪类 WIP 事务），一个码只属一个 Type；
--   · cim_reason_code：码本体 + 治理属性（归属部门=平台字典、启停），
--     分类树不另立表——parent_id 自关联 + node_path 物化路径直接长在码上
--     （每码必挂树，杜绝「游离码」；层级语义=责任类→现象类→具体原因）；
--   · cim_wip_reason_event：WIP 事务示例表（Hold/Release/Scrap…），
--     reason 五个关键属性以「事件发生时刻快照」落库，主数据后续改名/挪部门/
--     挪树位均不回写历史（审计与跨年对比的事实地基）。
--   治理纪律：reason_code 全局唯一、建后不可改；不物理删除（软删）；
--   enabled=false 只影响未来下拉可见性，历史引用照常可解析；
--   处置权限（谁可释放）是「操作上下文→角色」映射的职责，不进码表。

CREATE TABLE cim_reason_code_type (
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
    type_key        VARCHAR(32)   NOT NULL,
    type_name       VARCHAR(64)   NOT NULL,
    sort_no         INT           NOT NULL DEFAULT 100,
    CONSTRAINT pk_cim_reason_code_type PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_reason_code_type ON cim_reason_code_type (type_key, deleted);

CREATE TABLE cim_reason_code (
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
    reason_code     VARCHAR(64)   NOT NULL,
    type_key        VARCHAR(32)   NOT NULL,
    short_name      VARCHAR(128),
    owning_dept     VARCHAR(64),
    parent_id       VARCHAR(64),
    node_path       VARCHAR(512)  NOT NULL,
    level_no        INT           NOT NULL DEFAULT 1,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_no         INT           NOT NULL DEFAULT 100,
    CONSTRAINT pk_cim_reason_code PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_reason_code ON cim_reason_code (reason_code, deleted);
CREATE INDEX idx_cim_reason_code_type ON cim_reason_code (type_key, deleted);
CREATE INDEX idx_cim_reason_code_parent ON cim_reason_code (parent_id, deleted);

CREATE TABLE cim_wip_reason_event (
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
    lot_id          VARCHAR(64)   NOT NULL,
    wafer_id        VARCHAR(64),
    operation_code  VARCHAR(64),
    equipment_code  VARCHAR(64),
    event_type      VARCHAR(32)   NOT NULL,
    reason_id       VARCHAR(64)   NOT NULL,
    occurred_at     TIMESTAMP     NOT NULL,
    operator        VARCHAR(64),
    -- 快照五件套：事件发生时刻的码/类型/树路径/归属部门/短名
    reason_code_snapshot    VARCHAR(64)   NOT NULL,
    reason_type_snapshot    VARCHAR(32)   NOT NULL,
    reason_path_snapshot    VARCHAR(512)  NOT NULL,
    owning_dept_snapshot    VARCHAR(64),
    reason_name_snapshot    VARCHAR(128),
    CONSTRAINT pk_cim_wip_reason_event PRIMARY KEY (id)
);
CREATE INDEX idx_cim_wip_reason_event_lot ON cim_wip_reason_event (lot_id, occurred_at);
CREATE INDEX idx_cim_wip_reason_event_reason ON cim_wip_reason_event (reason_id);
