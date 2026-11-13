-- MDS 主数据 V3：AreaGroup（区域组，统计口径分类属性）+ cim_area 关联列（MySQL）
-- 设计见 docs/business/mds-ap/server/location-design.md §9.1：
--   用途=统计口径（前段/后段等工艺分段汇总），形态=树内虚拟分节，不作为位置树的正式一层；
--   权限复用 mds:area:*，不新增权限码/菜单。StageGroup 不建实体（§9.2 裁决：一机多位由
--   设备域 Equipment ↔ Stage 引用表达）。

-- ---------------------------------------------------------------- 区域组（AreaGroup）
-- group_code 在 Fab 内唯一（fab_code, group_code, deleted）。
CREATE TABLE cim_area_group (
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
    group_code      VARCHAR(64)  NOT NULL COMMENT '区域组编码，Fab 内唯一，例：FEOL',
    fab_code        VARCHAR(64)  NOT NULL COMMENT '归属 fab_code（逻辑引用）',
    group_name      VARCHAR(128) NOT NULL COMMENT '区域组名称，例：前段工艺',
    remark          VARCHAR(512) NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '停用后 Area 不可新入组',
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '正被某人编辑（编辑锁）',
    occupied_by     VARCHAR(64)  NULL COMMENT '占用者账号',
    occupied_at     TIMESTAMP    NULL COMMENT '抢锁时间（超时判定）',
    CONSTRAINT pk_cim_area_group PRIMARY KEY (id),
    CONSTRAINT uk_cim_area_group_code UNIQUE (fab_code, group_code, deleted),
    KEY idx_cim_area_group_fab (fab_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '区域组（AreaGroup），Area 的统计口径分类';

-- ---------------------------------------------------------------- Area 关联列
-- 可空：未分组的 Area 直接挂 Fab 下不分节。服务层校验组与 Area 同 Fab（§9.1）。
ALTER TABLE cim_area ADD COLUMN area_group_id VARCHAR(64) NULL COMMENT '所属区域组 id（可空逻辑引用）',
                     ADD INDEX idx_cim_area_group_id (area_group_id);
