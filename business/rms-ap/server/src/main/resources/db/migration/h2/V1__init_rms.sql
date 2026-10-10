-- RMS 配方管理系统 W1 表（H2 内存库，开发与测试用）
-- 覆盖：设备类型/区域/台账（Req 46-48）+ 配方主档/版本（Req 1/6/13/14/42）。
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花，见 Req.md §8）。

CREATE TABLE device_type (
    id            VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time    TIMESTAMP,
    event_time     TIMESTAMP,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    CONSTRAINT pk_device_type PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_device_type_code ON device_type (code);

CREATE TABLE device_area (
    id            VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time    TIMESTAMP,
    event_time     TIMESTAMP,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    parent_id      VARCHAR(64),
    sort_no        INT          DEFAULT 0,
    CONSTRAINT pk_device_area PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_device_area_code ON device_area (code);

CREATE TABLE device (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time    TIMESTAMP,
    event_time     TIMESTAMP,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    device_type_id VARCHAR(64)  NOT NULL,
    area_id        VARCHAR(64),
    status         VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_device PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_device_code ON device (code);
CREATE INDEX idx_device_type ON device (device_type_id);
CREATE INDEX idx_device_area ON device (area_id);

CREATE TABLE recipe (
    id                 VARCHAR(64)  NOT NULL,
    description        VARCHAR(512),
    version            BIGINT,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time        TIMESTAMP,
    event_time         TIMESTAMP,
    create_user        VARCHAR(64),
    event_user         VARCHAR(64),
    event_name         VARCHAR(128),
    event_comment      VARCHAR(512),
    trx_id             VARCHAR(64),
    tenant_id          VARCHAR(64),
    code               VARCHAR(128) NOT NULL,
    name               VARCHAR(128) NOT NULL,
    device_type_id     VARCHAR(64)  NOT NULL,
    area_id            VARCHAR(64),
    golden             BOOLEAN      NOT NULL DEFAULT FALSE,
    active_version_id  VARCHAR(64),
    CONSTRAINT pk_recipe PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_recipe_code ON recipe (code);
CREATE INDEX idx_recipe_type ON recipe (device_type_id);
CREATE INDEX idx_recipe_area ON recipe (area_id);

CREATE TABLE recipe_version (
    id                VARCHAR(64)   NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    recipe_id         VARCHAR(64)   NOT NULL,
    version_no        INT           NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    body_format       VARCHAR(16)   NOT NULL,
    body_base64       CLOB,
    body_hash         VARCHAR(64),
    param_snapshot    CLOB,
    change_summary    VARCHAR(512),
    source_version_id VARCHAR(64),
    activated_at      TIMESTAMP,
    activated_by      VARCHAR(64),
    CONSTRAINT pk_recipe_version PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_recipe_version_no ON recipe_version (recipe_id, version_no);
CREATE INDEX idx_recipe_version_recipe ON recipe_version (recipe_id);
