-- RMS W3 表（H2 内存库）：签核单 + 操作审计（Req 41 / 17-19）。
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花）。

CREATE TABLE rms_signoff (
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
    version_id       VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    initiator        VARCHAR(64),
    approver         VARCHAR(64),
    decision_comment VARCHAR(512),
    decided_at       TIMESTAMP,
    CONSTRAINT pk_rms_signoff PRIMARY KEY (id)
);
CREATE INDEX idx_rms_signoff_recipe ON rms_signoff (recipe_id);
CREATE INDEX idx_rms_signoff_version ON rms_signoff (version_id);
CREATE INDEX idx_rms_signoff_status ON rms_signoff (status);

CREATE TABLE rms_audit_log (
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
    type             VARCHAR(64)  NOT NULL,
    result           VARCHAR(16)  NOT NULL,
    actor            VARCHAR(64),
    target           VARCHAR(128),
    detail           VARCHAR(512),
    CONSTRAINT pk_rms_audit_log PRIMARY KEY (id)
);
CREATE INDEX idx_rms_audit_type ON rms_audit_log (type);
