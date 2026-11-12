-- MDS 主数据 V1：场地层级（H2 内存库，开发与测试用）
-- 覆盖：Site → Fab → Area（Bay / Module 见 docs/business/mds-ap/server/location-design.md，随业务展开再建）。
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键应用层雪花）。
--
-- ⚠️ 与 MDS 早期设计稿（db/mysql/Site-Fab-Area-Bay-Module.sql）的差异：
--    设计稿用 bigint id + deleted_at + created_at/updated_at；本迁移改用平台基类约定
--    （VARCHAR(64) id + deleted 布尔 + create_time/event_time/... + tenant_id），
--    否则 Hibernate ddl-auto=validate 直接启动失败。业务字段（xxx_code / amhs_type 等）保持设计稿语义。
--    唯一键含 deleted，支持「逻辑删除后重建同编码」。

-- ---------------------------------------------------------------- 基地
CREATE TABLE cim_site (
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
    site_code       VARCHAR(64)  NOT NULL,
    site_name       VARCHAR(128) NOT NULL,
    address         VARCHAR(256),
    contact_person  VARCHAR(64),
    contact_phone   VARCHAR(32),
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_site PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_site_code ON cim_site (site_code, deleted);

-- ---------------------------------------------------------------- 晶圆厂
CREATE TABLE cim_fab (
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
    fab_code        VARCHAR(64)  NOT NULL,
    site_code       VARCHAR(64)  NOT NULL,
    fab_name        VARCHAR(128) NOT NULL,
    fab_type        VARCHAR(32),
    amhs_type       VARCHAR(32),
    clean_grade     VARCHAR(32),
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_fab PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_fab_code ON cim_fab (fab_code, deleted);
CREATE INDEX idx_cim_fab_site ON cim_fab (site_code);

-- ---------------------------------------------------------------- 区域
CREATE TABLE cim_area (
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
    area_code       VARCHAR(64)  NOT NULL,
    fab_code        VARCHAR(64)  NOT NULL,
    area_name       VARCHAR(128) NOT NULL,
    area_type       VARCHAR(32),
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_area PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_area_code ON cim_area (fab_code, area_code, deleted);
