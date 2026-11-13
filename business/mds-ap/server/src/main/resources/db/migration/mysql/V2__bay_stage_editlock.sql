-- MDS 主数据 V2：Bay / Stage 两层 + 位置五表编辑锁三列（MySQL 8 / 生产）
-- 层级定名见 docs/business/mds-ap/server/location-design.md §1.1 / §8：
--   Site → Fab → Area → Bay → Stage（第五层原设计稿叫 SubBay，2026-11-13 定名 Stage；
--   Module 属设备域 SEMI E157 工艺腔，不入位置树）。
-- 编辑锁三列语义见同文档 §8.1（is_occupied/occupied_by/occupied_at，「人 vs 人」编辑互斥）。

-- ---------------------------------------------------------------- 编辑锁列（存量三表）
ALTER TABLE cim_site ADD COLUMN is_occupied BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '正被某人编辑（编辑锁）',
                       ADD COLUMN occupied_by VARCHAR(64) NULL COMMENT '占用者账号',
                       ADD COLUMN occupied_at TIMESTAMP   NULL COMMENT '抢锁时间（超时判定）';

ALTER TABLE cim_fab ADD COLUMN is_occupied BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '正被某人编辑（编辑锁）',
                    ADD COLUMN occupied_by VARCHAR(64)  NULL COMMENT '占用者账号',
                    ADD COLUMN occupied_at TIMESTAMP   NULL COMMENT '抢锁时间（超时判定）';

ALTER TABLE cim_area ADD COLUMN is_occupied BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '正被某人编辑（编辑锁）',
                     ADD COLUMN occupied_by VARCHAR(64) NULL COMMENT '占用者账号',
                     ADD COLUMN occupied_at TIMESTAMP   NULL COMMENT '抢锁时间（超时判定）';

-- ---------------------------------------------------------------- 湾（Bay）
-- 物理湾道编号全厂不重复：bay_code 在 Fab 内唯一（ fab_code, bay_code, deleted）。
-- 归属两层都落列（fab_code + area_code）： fab 用于快捷过滤，area 是直接父层。
CREATE TABLE cim_bay (
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
    bay_code        VARCHAR(64)  NOT NULL COMMENT 'Bay 编码，Fab 内唯一，例：B01',
    fab_code        VARCHAR(64)  NOT NULL COMMENT '归属 fab_code（逻辑引用）',
    area_code       VARCHAR(64)  NOT NULL COMMENT '归属 area_code（直接父层）',
    bay_name        VARCHAR(128) NOT NULL COMMENT 'Bay 名称，例：B01 湾',
    remark          VARCHAR(512) NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '停用后不可被 Stage 新引用',
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '正被某人编辑（编辑锁）',
    occupied_by     VARCHAR(64)  NULL COMMENT '占用者账号',
    occupied_at     TIMESTAMP    NULL COMMENT '抢锁时间（超时判定）',
    CONSTRAINT pk_cim_bay PRIMARY KEY (id),
    CONSTRAINT uk_cim_bay_code UNIQUE (fab_code, bay_code, deleted),
    KEY idx_cim_bay_area (fab_code, area_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '湾（Bay），物理作业区';

-- ---------------------------------------------------------------- 设备位（Stage）
-- 单 Bay 内的物理落位（工具簇 / 机台位），设备将来以 location_id 落 Stage 或无细分的 Bay。
CREATE TABLE cim_stage (
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
    stage_code      VARCHAR(64)  NOT NULL COMMENT 'Stage 编码，Bay 内唯一，例：S01',
    fab_code        VARCHAR(64)  NOT NULL COMMENT '归属 fab_code（经 Bay 冗余，便于过滤）',
    bay_code        VARCHAR(64)  NOT NULL COMMENT '归属 bay_code（直接父层）',
    stage_name      VARCHAR(128) NOT NULL COMMENT 'Stage 名称',
    remark          VARCHAR(512) NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '停用后设备不可新落位',
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '正被某人编辑（编辑锁）',
    occupied_by     VARCHAR(64)  NULL COMMENT '占用者账号',
    occupied_at     TIMESTAMP    NULL COMMENT '抢锁时间（超时判定）',
    CONSTRAINT pk_cim_stage PRIMARY KEY (id),
    CONSTRAINT uk_cim_stage_code UNIQUE (fab_code, bay_code, stage_code, deleted),
    KEY idx_cim_stage_bay (fab_code, bay_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备位（Stage），Bay 内物理落位';
