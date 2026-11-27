-- MDS V29：配方解算契约——工序可变参数（2026-11-27 定稿，docs/business/mds-ap/server/recipe-resolve-design.md）——MySQL 方言
-- 分层归属（不重建 RMS 真值层）：
--   * Logical Recipe（工艺意图）= cim_operation.recipe_ref（既有，ContextChain 继承/覆盖）；
--   * Wafer Recipe（片级可变参数）= 本表：哪些参数允许可变 + 默认值 + 防呆范围 + 允许覆盖层级，
--     挂 cim_operation 版本行（工艺意图层），不挂 cim_equip_assign（派工/资格层语义不合）；
--   * PPID / Checksum / 腔体 / 资格 = RMS 真值（IF-M2 解析端点供解算调用，本库不存）。
--   * min/max 为 MDS 侧防呆快照；范围真值锚点仍是 RMS param_spec（spec_type target/range/...）。

CREATE TABLE cim_operation_variable_param (
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
    op_id           VARCHAR(64)  NOT NULL,
    param_key       VARCHAR(64)  NOT NULL,
    default_value   VARCHAR(256),
    min_value       VARCHAR(64),
    max_value       VARCHAR(64),
    override_level  VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    sort_no         INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_operation_variable_param PRIMARY KEY (id),
    CONSTRAINT uk_operation_variable_param UNIQUE (op_id, param_key, deleted)
);
CREATE INDEX idx_op_var_param_op ON cim_operation_variable_param (op_id);
