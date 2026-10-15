-- RMS W4 治理域（MySQL）：配方层级 / 参数 Spec / 模板 / 设备授权 / Bypass /
-- 资格矩阵 / Golden 机差 / EC-SV / Hold / Alarm（Req 11/15/16/22/23/29-36/43-45 + FR-Q/G/SEC）。
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花）。

-- 1) 配方主档扩展：主/子配方层级（Req 15，不限层级）
ALTER TABLE recipe ADD COLUMN parent_id      VARCHAR(64);
ALTER TABLE recipe ADD COLUMN recipe_level   INT NOT NULL DEFAULT 0;
CREATE INDEX idx_recipe_parent ON recipe (parent_id);

-- 2) 参数 Spec（FR-S1：target/range/list_in/tolerance 四类 + 百分比/固定值双轨容差）
CREATE TABLE param_spec (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    recipe_id      VARCHAR(64)  NOT NULL,
    param_key      VARCHAR(128) NOT NULL,
    param_name     VARCHAR(128),
    spec_type      VARCHAR(16)  NOT NULL,
    data_type      VARCHAR(16)  NOT NULL DEFAULT 'NUMBER',
    target_value   VARCHAR(128),
    range_min      VARCHAR(64),
    range_max      VARCHAR(64),
    list_values    VARCHAR(1024),
    tolerance_pct  DOUBLE,
    tolerance_abs  DOUBLE,
    unit           VARCHAR(32),
    enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    template_code  VARCHAR(64),
    CONSTRAINT pk_param_spec PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_param_spec_recipe ON param_spec (recipe_id);

-- 3) Spec 模板
CREATE TABLE spec_template (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time   DATETIME,
    event_time    DATETIME,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    template_ver  INT          NOT NULL DEFAULT 1,
    scope_type    VARCHAR(32),
    CONSTRAINT pk_spec_template PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_spec_template_code ON spec_template (code);

CREATE TABLE spec_template_item (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time   DATETIME,
    event_time    DATETIME,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    template_id   VARCHAR(64)  NOT NULL,
    param_key     VARCHAR(128) NOT NULL,
    param_name    VARCHAR(128),
    spec_type     VARCHAR(16)  NOT NULL,
    data_type     VARCHAR(16)  NOT NULL DEFAULT 'NUMBER',
    target_value  VARCHAR(128),
    range_min     VARCHAR(64),
    range_max     VARCHAR(64),
    list_values   VARCHAR(1024),
    tolerance_pct DOUBLE,
    tolerance_abs DOUBLE,
    unit          VARCHAR(32),
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    sort_no       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_spec_template_item PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_spec_template_item_tpl ON spec_template_item (template_id);

-- 4) 用户↔设备授权（FR-P4 / Req 11）
CREATE TABLE user_device_auth (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    subject        VARCHAR(64)  NOT NULL,
    subject_type   VARCHAR(16)  NOT NULL DEFAULT 'USER',
    device_id      VARCHAR(64),
    device_type_id VARCHAR(64),
    area_id        VARCHAR(64),
    can_operate    TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_user_device_auth PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_user_device_auth_subject ON user_device_auth (subject);

-- 5) Bypass 豁免（FR-B1/B2 / Req 22/23）
CREATE TABLE rms_bypass (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    scope          VARCHAR(16)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    target_label   VARCHAR(128),
    bypass_type    VARCHAR(16)  NOT NULL,
    reason         VARCHAR(512) NOT NULL,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    effective_from DATETIME,
    effective_to   DATETIME,
    created_by     VARCHAR(64),
    CONSTRAINT pk_rms_bypass PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_rms_bypass_scope_target ON rms_bypass (scope, target_id);

-- 6) 配方-机台资格矩阵（FR-Q1~Q4）
CREATE TABLE recipe_qualification (
    id              VARCHAR(64)  NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    create_time     DATETIME,
    event_time      DATETIME,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    recipe_id       VARCHAR(64)  NOT NULL,
    version_policy  VARCHAR(16)  NOT NULL DEFAULT 'LATEST_ACTIVE',
    version_id      VARCHAR(64),
    device_id       VARCHAR(64),
    device_type_id  VARCHAR(64),
    area_id         VARCHAR(64),
    basis           VARCHAR(512),
    status          VARCHAR(16)  NOT NULL DEFAULT 'GRANTED',
    effective_from  DATETIME,
    effective_to    DATETIME,
    CONSTRAINT pk_recipe_qualification PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_recipe_qual_recipe ON recipe_qualification (recipe_id);
CREATE INDEX idx_recipe_qual_device ON recipe_qualification (device_id);

-- 7) Golden 机差覆盖层（FR-G2 / Req 31）
CREATE TABLE golden_override (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time   DATETIME,
    event_time    DATETIME,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    device_id     VARCHAR(64)  NOT NULL,
    recipe_id     VARCHAR(64),
    param_key     VARCHAR(128),
    allow_min     VARCHAR(64),
    allow_max     VARCHAR(64),
    reason        VARCHAR(512),
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    approved_by   VARCHAR(64),
    approved_at   DATETIME,
    CONSTRAINT pk_golden_override PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_golden_override_device ON golden_override (device_id);

-- 8) EC/SV 规格（FR-D3 / Req 44）
CREATE TABLE ecsv_spec (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time   DATETIME,
    event_time    DATETIME,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    scope         VARCHAR(16)  NOT NULL,
    ref_id        VARCHAR(64)  NOT NULL,
    ecsv_type     VARCHAR(8)   NOT NULL,
    sv_id         VARCHAR(64),
    param_key     VARCHAR(128) NOT NULL,
    spec_type     VARCHAR(16)  NOT NULL,
    target_value  VARCHAR(128),
    range_min     VARCHAR(64),
    range_max     VARCHAR(64),
    tolerance_pct DOUBLE,
    tolerance_abs DOUBLE,
    unit          VARCHAR(32),
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_ecsv_spec PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ecsv_spec_ref ON ecsv_spec (scope, ref_id);

-- 9) EC/SV 上传值与比对结果（FR-D2/D4 / Req 43/45）
CREATE TABLE ecsv_value (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time   DATETIME,
    event_time    DATETIME,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    device_id     VARCHAR(64)  NOT NULL,
    ecsv_type     VARCHAR(8)   NOT NULL,
    param_key     VARCHAR(128) NOT NULL,
    actual_value  VARCHAR(128),
    spec_id       VARCHAR(64),
    result        VARCHAR(16)  NOT NULL DEFAULT 'PASS',
    detail        VARCHAR(512),
    captured_at   DATETIME,
    CONSTRAINT pk_ecsv_value PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ecsv_value_device ON ecsv_value (device_id);

-- 10) Hold 记录（Req 21）
CREATE TABLE rms_hold (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    device_id      VARCHAR(64),
    recipe_id      VARCHAR(64),
    recipe_code    VARCHAR(128),
    reason         VARCHAR(512),
    source         VARCHAR(32)  NOT NULL,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    released_by    VARCHAR(64),
    released_at    DATETIME,
    release_reason VARCHAR(512),
    CONSTRAINT pk_rms_hold PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_rms_hold_device ON rms_hold (device_id);
CREATE INDEX idx_rms_hold_active ON rms_hold (active);

-- 11) Alarm 记录（IF-A1）
CREATE TABLE rms_alarm (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    alarm_code     VARCHAR(64)  NOT NULL,
    severity       VARCHAR(16)  NOT NULL,
    device_id      VARCHAR(64),
    lot_id         VARCHAR(64),
    recipe_code    VARCHAR(128),
    message        VARCHAR(512),
    correlation_id VARCHAR(64),
    occurred_at    DATETIME,
    acknowledged   TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT pk_rms_alarm PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_rms_alarm_code ON rms_alarm (alarm_code);
