-- MDS 主数据 V3：AreaGroup（区域组，统计口径分类属性）+ cim_area 关联列
-- 设计见 docs/business/mds-ap/server/location-design.md §9.1：
--   用途=统计口径（前段/后段等工艺分段汇总），形态=树内虚拟分节，不作为位置树的正式一层；
--   权限复用 mds:area:*，不新增权限码/菜单。StageGroup 不建实体（§9.2 裁决：一机多位由
--   设备域 Equipment ↔ Stage 引用表达）。

-- ---------------------------------------------------------------- 区域组（AreaGroup）
-- group_code 在 Fab 内唯一（fab_code, group_code, deleted）。
CREATE TABLE cim_area_group (
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
    group_code      VARCHAR(64)  NOT NULL,
    fab_code        VARCHAR(64)  NOT NULL,
    group_name      VARCHAR(128) NOT NULL,
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_area_group PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_area_group_code ON cim_area_group (fab_code, group_code, deleted);
CREATE INDEX idx_cim_area_group_fab ON cim_area_group (fab_code);

-- ---------------------------------------------------------------- Area 关联列
-- 可空：未分组的 Area 直接挂 Fab 下不分节。服务层校验组与 Area 同 Fab（§9.1）。
ALTER TABLE cim_area ADD COLUMN area_group_id VARCHAR(64) NULL;
CREATE INDEX idx_cim_area_group_id ON cim_area (area_group_id);
