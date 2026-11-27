-- approval-ap 扩展表 V1（H2，开发与测试用；Flowable ACT_/FLW_ 表由引擎自建，不经 Flyway）
-- 表结构 = 平台 Auditable/BaseDefData 基类列 + 业务列（统一小写蛇形命名；主键应用层雪花）。
-- 审计列：create_time/event_time/create_user/event_user/event_name/event_comment/trx_id/tenant_id；
-- 定义表（BaseDefData）另含 description/version/deleted，唯一键含 deleted 支持逻辑删后重建。

-- ---------------------------------------------------------------- 审批附件（元数据）
CREATE TABLE approval_attachment (
    id                  VARCHAR(64)  NOT NULL,
    description         VARCHAR(512),
    version             BIGINT,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time         TIMESTAMP,
    event_time          TIMESTAMP,
    create_user         VARCHAR(64),
    event_user          VARCHAR(64),
    event_name          VARCHAR(128),
    event_comment       VARCHAR(512),
    trx_id              VARCHAR(64),
    tenant_id           VARCHAR(64),
    business_key        VARCHAR(128) NOT NULL,
    process_instance_id VARCHAR(64),
    file_name           VARCHAR(255) NOT NULL,
    content_type        VARCHAR(128),
    size_bytes          BIGINT       NOT NULL,
    storage_path        VARCHAR(255) NOT NULL,
    CONSTRAINT pk_approval_attachment PRIMARY KEY (id)
);
CREATE INDEX idx_approval_attachment_biz ON approval_attachment (business_key);

-- ---------------------------------------------------------------- 审计日志（哈希链，追加不可变）
CREATE TABLE approval_audit (
    id                  VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP,
    event_time          TIMESTAMP,
    create_user         VARCHAR(64),
    event_user          VARCHAR(64),
    event_name          VARCHAR(128),
    event_comment       VARCHAR(512),
    trx_id              VARCHAR(64),
    tenant_id           VARCHAR(64),
    seq                 BIGINT       NOT NULL,
    business_key        VARCHAR(128),
    app_code            VARCHAR(64),
    process_instance_id VARCHAR(64),
    task_id             VARCHAR(64),
    action              VARCHAR(32)  NOT NULL,
    actor               VARCHAR(64)  NOT NULL,
    before_state        VARCHAR(64),
    after_state         VARCHAR(64),
    detail              CLOB,
    prev_hash           VARCHAR(64)  NOT NULL,
    hash                VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_approval_audit PRIMARY KEY (id)
);
CREATE INDEX idx_approval_audit_biz ON approval_audit (business_key, seq);
CREATE INDEX idx_approval_audit_app ON approval_audit (app_code);

-- ---------------------------------------------------------------- 回调 Outbox（可靠投递）
CREATE TABLE callback_outbox (
    id            VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP,
    event_time    TIMESTAMP,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    message_id    VARCHAR(64)  NOT NULL,
    app_code      VARCHAR(64)  NOT NULL,
    business_key  VARCHAR(128),
    event_type    VARCHAR(64),
    payload       CLOB         NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    attempts      INT          NOT NULL,
    max_attempts  INT          NOT NULL,
    next_retry_at TIMESTAMP,
    last_error    VARCHAR(1024),
    sent_at       TIMESTAMP,
    CONSTRAINT pk_callback_outbox PRIMARY KEY (id),
    CONSTRAINT uk_callback_outbox_message UNIQUE (message_id)
);
CREATE INDEX idx_callback_outbox_app ON callback_outbox (app_code);

-- ---------------------------------------------------------------- 回调注册表
CREATE TABLE callback_registration (
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
    app_code      VARCHAR(64)  NOT NULL,
    base_url      VARCHAR(512) NOT NULL,
    secret        VARCHAR(128) NOT NULL,
    enabled       BOOLEAN      NOT NULL,
    CONSTRAINT pk_callback_registration PRIMARY KEY (id),
    CONSTRAINT uk_callback_registration_app UNIQUE (app_code, deleted)
);
