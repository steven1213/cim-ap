-- RMS W9/W10/W11：批量操作 + 签核通知 + Body 模板 + 实验通道 + 变更效果挂钩。
--
-- 1) rms_notification / rms_notification_read —— 站内通知（W9 审批实时通知）：
--    收件模型二选一：recipient=指定到人；audience=权限受众广播（APPROVER/INTEGRATION）。
--    已读 per-user 落 read 表（广播类互不影响）；UNIQUE(notification_id, username) 幂等。
-- 2) rms_body_template / rms_body_template_item —— Body 模板（W10，对齐 camLine ROBDEF）：
--    强制键 + 默认值 + 骨架 Body；enforced 模板在校验服务把缺键记为 error。
-- 3) rms_experiment —— 实验配方通道（W11）：REQUESTED→APPROVED/REJECTED→CLOSED；
--    放行 = APPROVED × 指定机台 × 指定版本 × 有效窗口（IF-E2 闸门接入，到期读取时派生）。
-- 4) rms_change_effect —— 变更效果登记（W11）：版本生效即落行（从/到版本 + 参数差异项数），
--    供质量侧按变更时点回溯 SPC/Cpk（spc_ref 预留跨域引用）。

CREATE TABLE rms_notification (
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
    type            VARCHAR(48)  NOT NULL,
    title           VARCHAR(128) NOT NULL,
    content         VARCHAR(512),
    recipient       VARCHAR(64),
    audience        VARCHAR(32),
    ref_type        VARCHAR(32),
    ref_id          VARCHAR(64),
    occurred_at     TIMESTAMP    NOT NULL,
    CONSTRAINT pk_rms_notification PRIMARY KEY (id)
);
CREATE INDEX idx_noti_recipient ON rms_notification (recipient);
CREATE INDEX idx_noti_audience ON rms_notification (audience);
CREATE INDEX idx_noti_occurred ON rms_notification (occurred_at);

CREATE TABLE rms_notification_read (
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
    notification_id VARCHAR(64)  NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    read_at         TIMESTAMP    NOT NULL,
    CONSTRAINT pk_rms_notification_read PRIMARY KEY (id),
    CONSTRAINT uk_noti_read UNIQUE (notification_id, username)
);

CREATE TABLE rms_body_template (
    id                   VARCHAR(64)  NOT NULL,
    description          VARCHAR(512),
    version              BIGINT,
    deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time          TIMESTAMP,
    event_time           TIMESTAMP,
    create_user          VARCHAR(64),
    event_user           VARCHAR(64),
    event_name           VARCHAR(128),
    event_comment        VARCHAR(512),
    trx_id               VARCHAR(64),
    tenant_id            VARCHAR(64),
    code                 VARCHAR(64)  NOT NULL,
    name                 VARCHAR(128) NOT NULL,
    body_format          VARCHAR(32)  NOT NULL,
    scope_device_type_id VARCHAR(64),
    enforced             BOOLEAN      NOT NULL DEFAULT TRUE,
    template_body        CLOB,
    CONSTRAINT pk_rms_body_template PRIMARY KEY (id),
    CONSTRAINT uk_btpl_code UNIQUE (code)
);
CREATE INDEX idx_btpl_scope ON rms_body_template (scope_device_type_id);

CREATE TABLE rms_body_template_item (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMP,
    event_time    TIMESTAMP,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    template_id   VARCHAR(64)  NOT NULL,
    param_key     VARCHAR(128) NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT TRUE,
    default_value VARCHAR(256),
    unit          VARCHAR(64),
    sort_no       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_rms_body_template_item PRIMARY KEY (id)
);
CREATE INDEX idx_bti_template ON rms_body_template_item (template_id);

CREATE TABLE rms_experiment (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMP,
    event_time    TIMESTAMP,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    code          VARCHAR(64)  NOT NULL,
    recipe_id     VARCHAR(64)  NOT NULL,
    recipe_code   VARCHAR(128),
    version_id    VARCHAR(64)  NOT NULL,
    version_no    INT,
    device_id     VARCHAR(64)  NOT NULL,
    device_code   VARCHAR(128),
    reason        VARCHAR(512),
    status        VARCHAR(32)  NOT NULL,
    requested_by  VARCHAR(64),
    decided_by    VARCHAR(64),
    decided_at    TIMESTAMP,
    valid_from    TIMESTAMP,
    valid_until   TIMESTAMP    NOT NULL,
    closed_at     TIMESTAMP,
    close_reason  VARCHAR(512),
    CONSTRAINT pk_rms_experiment PRIMARY KEY (id),
    CONSTRAINT uk_exp_code UNIQUE (code)
);
CREATE INDEX idx_exp_device ON rms_experiment (device_id);
CREATE INDEX idx_exp_status ON rms_experiment (status);

CREATE TABLE rms_change_effect (
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
    recipe_id        VARCHAR(64)  NOT NULL,
    recipe_code      VARCHAR(128),
    from_version_id  VARCHAR(64),
    from_version_no  INT,
    to_version_id    VARCHAR(64)  NOT NULL,
    to_version_no    INT,
    activated_by     VARCHAR(64),
    activated_at     TIMESTAMP    NOT NULL,
    param_diff_count INT          NOT NULL DEFAULT 0,
    change_summary   VARCHAR(512),
    spc_ref          VARCHAR(128),
    CONSTRAINT pk_rms_change_effect PRIMARY KEY (id)
);
CREATE INDEX idx_ce_recipe ON rms_change_effect (recipe_id);
CREATE INDEX idx_ce_activated ON rms_change_effect (activated_at);
