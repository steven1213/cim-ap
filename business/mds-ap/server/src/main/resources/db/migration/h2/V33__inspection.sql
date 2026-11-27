-- MDS 检验标准 V33（2026-11-27，行业对照缺口④：AQL/检验项模板）
-- 设计口径：
--   * 检验标准 = 「检什么/怎么检/判多严」的静态契约：头表（适用产品分组 + 抽样方案 + AQL 允收
--     水准）+ 明细（检验项：规格/方法/量具/缺陷等级）。检验执行与判定归 MES/QMS 运行域，
--     主数据只存标准模板——与 EDC 方案「静态契约」同口径。
--   * product_group 挂字典 mds.product_group（产品管理域既有分组口径）；sampling_type 挂
--     mds.sampling_type（全检/抽检/首件/周期）；aql_level 挂 mds.aql_level
--     （GB/T 2828.1 / ANSI/ASQ Z1.4 特殊检验水准 S 系列常用值）；defect_level 挂
--     mds.defect_level（CRITICAL/MAJOR/MINOR）。
--   * 明细全量替换维护（页内行编辑一次提交）；删除标准级联清明细（同事务先删后插必 flush）。
--   * std_code 全局唯一、建后不可改；头表带编辑锁三列，明细轻量不带锁。

-- ---------------------------------------------------------------- 检验标准（头）
CREATE TABLE cim_inspection_std (
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
    std_code        VARCHAR(64)  NOT NULL,
    std_name        VARCHAR(128) NOT NULL,
    product_group   VARCHAR(32)  NULL,
    sampling_type   VARCHAR(32)  NULL,
    aql_level       VARCHAR(32)  NULL,
    sample_qty      INT          NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied     BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by     VARCHAR(64)  NULL,
    occupied_at     TIMESTAMP    NULL,
    CONSTRAINT pk_cim_inspection_std PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_inspection_std ON cim_inspection_std (std_code, deleted);

-- ---------------------------------------------------------------- 检验项（明细）
CREATE TABLE cim_inspection_item (
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
    std_id          VARCHAR(64)  NOT NULL,
    item_code       VARCHAR(64)  NOT NULL,
    item_name       VARCHAR(128) NOT NULL,
    spec            VARCHAR(512) NULL,
    method          VARCHAR(255) NULL,
    instrument      VARCHAR(128) NULL,
    defect_level    VARCHAR(32)  NULL,
    sort_no         INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_inspection_item PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_inspection_item ON cim_inspection_item (std_id, item_code, deleted);
CREATE INDEX idx_cim_inspection_item_std ON cim_inspection_item (std_id);

-- ---------------------------------------------------------------- 演示种子（CIS 外观检验标准）
INSERT INTO cim_inspection_std (id, version, deleted, create_time, create_user,
                                std_code, std_name, product_group, sampling_type,
                                aql_level, sample_qty, is_enabled, is_occupied)
VALUES ('seed-insp-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'INS-CIS-01', 'CIS 晶圆外观检验标准', 'CIS', 'SAMPLING', '0.65', 32, TRUE, FALSE);

INSERT INTO cim_inspection_item (id, version, deleted, create_time, create_user,
                                 std_id, item_code, item_name, spec, method, instrument,
                                 defect_level, sort_no)
VALUES ('seed-inspitem-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-insp-01', 'CHK-SCRATCH', '表面划伤', '不允许长度 > 2mm 的线性划伤', '目检', '40x 显微镜',
        'MAJOR', 10),
       ('seed-inspitem-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'seed-insp-01', 'CHK-PARTICLE', '颗粒污染', '≥ 0.5μm 颗粒 ≤ 30 颗/片', '自动检查', '颗粒检测仪',
        'MINOR', 20);
