-- MDS 产品主数据 V6：Product（产品）+ 演示种子
-- 设计（2026-11-13 讨论定稿，对标 Auto3/SiView ProductSpec，按行业通用口径裁剪）：
--   * Product = 产品主数据（SiView：Technology → Product → Route）。
--   * product_code 全局唯一；tech_code 逻辑引用 cim_technology（不做 FK），
--     反向守门：被产品引用的制程技术不可删（TechnologyAdminService）。
--   * product_type 行业枚举：PROD=量产产品 / NPW=陪片（Non-Product Wafer）。
--   * product_group 先做 varchar 分组码（轻实现，分组管理需求出现再升级实体）。
--   * wafer_type/owner 为行业常见分类与责任人字段（可空）。
--   * Material Control / Process / Plan / CDA / Bonding / MES Roles 不建模（归 MES 或平台）。
--   * 编辑锁三列与全库主数据同款（§8.1「人 vs 人」互斥）。

-- ---------------------------------------------------------------- 产品
CREATE TABLE cim_product (
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
    product_code    VARCHAR(64)  NOT NULL,
    product_name    VARCHAR(128) NOT NULL,
    tech_code       VARCHAR(64),
    product_type    VARCHAR(16)  NOT NULL DEFAULT 'PROD',
    product_group   VARCHAR(64),
    wafer_type      VARCHAR(64),
    owner           VARCHAR(64),
    remark          VARCHAR(512),
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_product PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_product_code ON cim_product (product_code, deleted);
CREATE INDEX idx_cim_product_tech ON cim_product (tech_code);

-- ---------------------------------------------------------------- 演示种子
INSERT INTO cim_product (id, version, deleted, create_time, create_user,
                         product_code, product_name, tech_code, product_type,
                         product_group, wafer_type, owner,
                         is_enabled, is_occupied)
VALUES ('seed-prod-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'PROD-01', '0.35um CIS 图像传感器', 'TECH-01', 'PROD',
        'CIS', '外延片', NULL, TRUE, FALSE),
       ('seed-prod-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'PROD-02', '0.5um 电源管理 IC', 'TECH-02', 'PROD',
        'PMIC', 'P 型衬底', NULL, TRUE, FALSE),
       ('seed-prod-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'NPW-01', '陪片（工艺监控）', 'TECH-01', 'NPW',
        'NPW', 'P 型衬底', NULL, TRUE, FALSE);
