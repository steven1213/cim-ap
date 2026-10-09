-- 审计事件表（MySQL，生产用）
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名）。

CREATE TABLE audit_event (
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
    type          VARCHAR(32)  NOT NULL,
    actor         VARCHAR(128),
    subject       VARCHAR(128),
    result        VARCHAR(16),
    detail        VARCHAR(512),
    client_ip     VARCHAR(64),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_audit_event_type ON audit_event (type);
CREATE INDEX ix_audit_event_subject ON audit_event (subject);
