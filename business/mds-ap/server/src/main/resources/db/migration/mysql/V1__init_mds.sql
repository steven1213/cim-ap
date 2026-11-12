-- MDS 主数据 V1：场地层级（MySQL 8 / 生产）
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
    id              VARCHAR(64)  NOT NULL COMMENT '主键（应用层雪花，非 DB 自增）',
    description     VARCHAR(512) NULL,
    version         BIGINT       NULL COMMENT '乐观锁',
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '逻辑删除',
    create_time     TIMESTAMP    NULL,
    event_time      TIMESTAMP    NULL,
    create_user     VARCHAR(64)  NULL,
    event_user      VARCHAR(64)  NULL,
    event_name      VARCHAR(128) NULL,
    event_comment   VARCHAR(512) NULL,
    trx_id          VARCHAR(64)  NULL,
    tenant_id       VARCHAR(64)  NULL,
    site_code       VARCHAR(64)  NOT NULL COMMENT '基地编码，唯一，例：CD-SITE',
    site_name       VARCHAR(128) NOT NULL COMMENT '基地名称，例：成都园区',
    address         VARCHAR(256) NULL,
    contact_person  VARCHAR(64)  NULL COMMENT '基地负责人',
    contact_phone   VARCHAR(32)  NULL,
    remark          VARCHAR(512) NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '停用后不可被 Fab 新引用',
    CONSTRAINT pk_cim_site PRIMARY KEY (id),
    CONSTRAINT uk_cim_site_code UNIQUE (site_code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基地（Site），场地层级根节点';

-- ---------------------------------------------------------------- 晶圆厂
CREATE TABLE cim_fab (
    id              VARCHAR(64)  NOT NULL,
    description     VARCHAR(512) NULL,
    version         BIGINT       NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP    NULL,
    event_time      TIMESTAMP    NULL,
    create_user     VARCHAR(64)  NULL,
    event_user      VARCHAR(64)  NULL,
    event_name      VARCHAR(128) NULL,
    event_comment   VARCHAR(512) NULL,
    trx_id          VARCHAR(64)  NULL,
    tenant_id       VARCHAR(64)  NULL,
    fab_code        VARCHAR(64)  NOT NULL COMMENT 'Fab 编码，唯一，例：F1',
    site_code       VARCHAR(64)  NOT NULL COMMENT '归属 site_code（逻辑引用，不建 FK）',
    fab_name        VARCHAR(128) NOT NULL,
    fab_type        VARCHAR(32)  NULL COMMENT '6 寸 / 8 寸 / 12 寸 / Assembly',
    amhs_type       VARCHAR(32)  NULL COMMENT 'OHT / RGV / NONE',
    clean_grade     VARCHAR(32)  NULL,
    remark          VARCHAR(512) NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_fab PRIMARY KEY (id),
    CONSTRAINT uk_cim_fab_code UNIQUE (fab_code, deleted),
    KEY idx_cim_fab_site (site_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '晶圆厂（Fab）';

-- ---------------------------------------------------------------- 区域
CREATE TABLE cim_area (
    id              VARCHAR(64)  NOT NULL,
    description     VARCHAR(512) NULL,
    version         BIGINT       NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP    NULL,
    event_time      TIMESTAMP    NULL,
    create_user     VARCHAR(64)  NULL,
    event_user      VARCHAR(64)  NULL,
    event_name      VARCHAR(128) NULL,
    event_comment   VARCHAR(512) NULL,
    trx_id          VARCHAR(64)  NULL,
    tenant_id       VARCHAR(64)  NULL,
    area_code       VARCHAR(64)  NOT NULL COMMENT 'Area 编码，Fab 内唯一',
    fab_code        VARCHAR(64)  NOT NULL COMMENT '归属 fab_code（逻辑引用）',
    area_name       VARCHAR(128) NOT NULL,
    area_type       VARCHAR(32)  NULL COMMENT 'Etch / Photo / Depo / Diff',
    remark          VARCHAR(512) NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cim_area PRIMARY KEY (id),
    CONSTRAINT uk_cim_area_code UNIQUE (fab_code, area_code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '区域（Area）';
