-- MDS 主数据 V2：Bay / Stage 两层 + 位置五表编辑锁三列（H2 内存库，开发与测试用）
-- 层级定名见 docs/business/mds-ap/server/location-design.md §1.1 / §8：
--   Site → Fab → Area → Bay → Stage（第五层原设计稿叫 SubBay，2026-11-13 定名 Stage；
--   Module 属设备域 SEMI E157 工艺腔，不入位置树）。
-- 编辑锁三列语义见同文档 §8.1（is_occupied/occupied_by/occupied_at，「人 vs 人」编辑互斥）。

-- ---------------------------------------------------------------- 编辑锁列（存量三表）
ALTER TABLE cim_site ADD COLUMN is_occupied  BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE cim_site ADD COLUMN occupied_by  VARCHAR(64) NULL;
ALTER TABLE cim_site ADD COLUMN occupied_at  TIMESTAMP   NULL;

ALTER TABLE cim_fab ADD COLUMN is_occupied   BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE cim_fab ADD COLUMN occupied_by   VARCHAR(64) NULL;
ALTER TABLE cim_fab ADD COLUMN occupied_at   TIMESTAMP   NULL;

ALTER TABLE cim_area ADD COLUMN is_occupied  BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE cim_area ADD COLUMN occupied_by  VARCHAR(64) NULL;
ALTER TABLE cim_area ADD COLUMN occupied_at  TIMESTAMP   NULL;

-- ---------------------------------------------------------------- 湾（Bay）
-- 物理湾道编号全厂不重复：bay_code 在 Fab 内唯一（ fab_code, bay_code, deleted）。
-- 归属两层都落列（fab_code + area_code）： fab 用于快捷过滤，area 是直接父层。
CREATE TABLE cim_bay (
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
    bay_code        VARCHAR(64)  NOT NULL,
    fab_code        VARCHAR(64)  NOT NULL,
    area_code       VARCHAR(64)  NOT NULL,
    bay_name        VARCHAR(128) NOT NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_bay PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_bay_code ON cim_bay (fab_code, bay_code, deleted);
CREATE INDEX idx_cim_bay_area ON cim_bay (fab_code, area_code);

-- ---------------------------------------------------------------- 设备位（Stage）
-- 单 Bay 内的物理落位（工具簇 / 机台位），设备将来以 location_id 落 Stage 或无细分的 Bay。
CREATE TABLE cim_stage (
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
    stage_code      VARCHAR(64)  NOT NULL,
    fab_code        VARCHAR(64)  NOT NULL,
    bay_code        VARCHAR(64)  NOT NULL,
    stage_name      VARCHAR(128) NOT NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_stage PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_stage_code ON cim_stage (fab_code, bay_code, stage_code, deleted);
CREATE INDEX idx_cim_stage_bay ON cim_stage (fab_code, bay_code);
