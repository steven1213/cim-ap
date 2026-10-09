-- 审计事件表（IAM 管理面可观测性：登录/登出/改密/应用注册/准入变更/强制下线）
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名）。

CREATE TABLE audit_event (
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
    type          VARCHAR(32)  NOT NULL,
    actor         VARCHAR(128),
    subject       VARCHAR(128),
    result        VARCHAR(16),
    detail        VARCHAR(512),
    client_ip     VARCHAR(64),
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);
CREATE INDEX ix_audit_event_type ON audit_event (type);
CREATE INDEX ix_audit_event_subject ON audit_event (subject);
