-- 身份目录与组织架构（MySQL，生产用；Wave 0 / identity-directory.md §3）
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名）。

CREATE TABLE org_node (
    id            VARCHAR(64)  NOT NULL,
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
    parent_id      VARCHAR(64),
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    node_type      VARCHAR(16)  NOT NULL,
    path           VARCHAR(512),
    sort_no        INT          DEFAULT 0,
    source         VARCHAR(16)  NOT NULL,
    external_id    VARCHAR(128),
    status         VARCHAR(16)  NOT NULL,
    updated_at     DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_org_node_source_code ON org_node (source, code);
CREATE INDEX ix_org_node_parent ON org_node (parent_id);
CREATE INDEX ix_org_node_path ON org_node (path);

CREATE TABLE user_profile (
    id            VARCHAR(64)  NOT NULL,
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
    user_id        VARCHAR(128) NOT NULL,
    employee_no    VARCHAR(64),
    display_name   VARCHAR(128),
    email          VARCHAR(128),
    mobile         VARCHAR(128),
    job_title      VARCHAR(64),
    status         VARCHAR(16)  NOT NULL,
    source         VARCHAR(16)  NOT NULL,
    synced_at      DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_user_profile_user ON user_profile (user_id);

CREATE TABLE user_org (
    id            VARCHAR(64)  NOT NULL,
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
    user_id        VARCHAR(128) NOT NULL,
    org_id         VARCHAR(64)  NOT NULL,
    is_primary     TINYINT(1)   NOT NULL DEFAULT 0,
    source         VARCHAR(16)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_user_org ON user_org (user_id, org_id);
CREATE INDEX ix_user_org_org ON user_org (org_id);

CREATE TABLE org_app_assignment (
    id            VARCHAR(64)  NOT NULL,
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
    org_id         VARCHAR(64)  NOT NULL,
    app_code       VARCHAR(64)  NOT NULL,
    roles          VARCHAR(512),
    include_children TINYINT(1) NOT NULL DEFAULT 1,
    status         VARCHAR(16)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_org_app ON org_app_assignment (org_id, app_code);
CREATE INDEX ix_org_app_app ON org_app_assignment (app_code);

CREATE TABLE directory_watermark (
    id            VARCHAR(64)  NOT NULL,
    scope         VARCHAR(32)  NOT NULL,
    version       BIGINT       NOT NULL,
    updated_at    DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_directory_watermark_scope ON directory_watermark (scope);
