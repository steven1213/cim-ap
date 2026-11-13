-- MDS 产品主数据 V5：Technology（制程技术）+ 演示种子
-- 设计（2026-11-13 讨论定稿）：
--   * Technology=产品之上的制程技术节点分类（SiView SM 参照系：Technology → Product → Route → Operation），
--     挂「产品管理」菜单域，与位置五层（cim_site/fab/area/bay/stage）平行。
--   * tech_code 全局唯一（技术节点是产品属性、跨厂复用，不按 Fab 隔离）。
--   * 编辑锁三列与五表同款（is_occupied/occupied_by/occupied_at，§8.1「人 vs 人」互斥）。
--   * Manufacturing Layer 不建模（Route 的分段属性，归 MES/工艺路线域，本表不管）。

-- ---------------------------------------------------------------- 制程技术
CREATE TABLE cim_technology (
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
    tech_code       VARCHAR(64)  NOT NULL,
    tech_name       VARCHAR(128) NOT NULL,
    tech_node       VARCHAR(32),
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_technology PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_technology_code ON cim_technology (tech_code, deleted);

-- ---------------------------------------------------------------- 演示种子
INSERT INTO cim_technology (id, version, deleted, create_time, create_user,
                            tech_code, tech_name, tech_node,
                            is_enabled, is_occupied)
VALUES ('seed-tech-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'TECH-01', 'CMOS Logic', '0.35um', TRUE, FALSE),
       ('seed-tech-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'TECH-02', 'BCD Power', '0.5um', TRUE, FALSE);
