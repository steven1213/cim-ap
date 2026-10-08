-- IAM 系统表（MySQL，生产用）
-- 与 design.md §8 / T6.5 对应：ap 注册/准入 + 令牌版本存储/递增。
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名）。

CREATE TABLE app_registration (
    id            VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)  NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    app_code       VARCHAR(64)  NOT NULL,
    app_name       VARCHAR(128),
    status         VARCHAR(16)  NOT NULL,
    sort_no        INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_app_registration_code ON app_registration (app_code);

CREATE TABLE user_app_assignment (
    id            VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)  NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    user_id        VARCHAR(128) NOT NULL,
    app_code       VARCHAR(64)  NOT NULL,
    roles          VARCHAR(512),
    status         VARCHAR(16)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_user_app ON user_app_assignment (user_id, app_code);

CREATE TABLE token_version (
    id            VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(128) NOT NULL,
    token_version BIGINT       NOT NULL,
    bumped_at     DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_token_version_user ON token_version (user_id);
