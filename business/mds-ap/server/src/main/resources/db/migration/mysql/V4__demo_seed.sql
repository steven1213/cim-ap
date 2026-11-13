-- MDS 演示种子 V4：位置树最小可用数据链（站点 → 晶圆厂 → 区域分组/区域 → Bay → 机台位）
-- 目的：H2 内存库每次重启清空后自动重建演示底座，免去手工新建。
-- 约定：
--   * id 用 'seed-*' 固定串标识种子身份；Flyway 只跑一次，无需幂等。
--   * 本文件一经应用不可修改（checksum 校验）；要改数据请新增 V5+，不要删本文件。
--   * 分组语义见 location-design.md §9.1：AreaGroup=统计口径（前段/后段），树内虚拟分节。

-- ---------------------------------------------------------------- 厂区
INSERT INTO cim_site (id, version, deleted, create_time, create_user,
                      site_code, site_name, address, is_enabled, is_occupied)
VALUES ('seed-site-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'SITE-01', '华南六寸基地', '深圳市', TRUE, FALSE);

-- ---------------------------------------------------------------- 晶圆厂
INSERT INTO cim_fab (id, version, deleted, create_time, create_user,
                     fab_code, site_code, fab_name, fab_type, amhs_type, clean_grade,
                     is_enabled, is_occupied)
VALUES ('seed-fab-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'FAB-01', 'SITE-01', '六英寸一厂', '6寸', 'OHT', 'Class 1000', TRUE, FALSE),
       ('seed-fab-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'FAB-02', 'SITE-01', '六英寸二厂', '6寸', 'OHT', 'Class 1000', TRUE, FALSE);

-- ---------------------------------------------------------------- 区域分组（统计口径）
INSERT INTO cim_area_group (id, version, deleted, create_time, create_user,
                            group_code, fab_code, group_name, is_enabled, is_occupied)
VALUES ('seed-ag-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AreaGroup-01', 'FAB-01', '前段工艺', TRUE, FALSE),
       ('seed-ag-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AreaGroup-02', 'FAB-01', '后段工艺', TRUE, FALSE),
       ('seed-ag-11', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AreaGroup-11', 'FAB-02', '前段工艺', TRUE, FALSE);

-- ---------------------------------------------------------------- 区域
INSERT INTO cim_area (id, version, deleted, create_time, create_user,
                      area_code, fab_code, area_name, area_type, area_group_id,
                      is_enabled, is_occupied)
VALUES ('seed-area-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AREA-01', 'FAB-01', '光刻区', 'PHOTO', 'seed-ag-01', TRUE, FALSE),
       ('seed-area-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AREA-02', 'FAB-01', '刻蚀区', 'ETCH', 'seed-ag-01', TRUE, FALSE),
       ('seed-area-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AREA-03', 'FAB-01', '封装测试区', 'F/T', 'seed-ag-02', TRUE, FALSE),
       ('seed-area-11', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'AREA-11', 'FAB-02', '光刻区', 'PHOTO', NULL, TRUE, FALSE);

-- ---------------------------------------------------------------- Bay
INSERT INTO cim_bay (id, version, deleted, create_time, create_user,
                     bay_code, fab_code, area_code, bay_name,
                     is_enabled, is_occupied)
VALUES ('seed-bay-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'BAY-01', 'FAB-01', 'AREA-01', 'Bay-01', TRUE, FALSE),
       ('seed-bay-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'BAY-02', 'FAB-01', 'AREA-01', 'Bay-02', TRUE, FALSE),
       ('seed-bay-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'BAY-03', 'FAB-01', 'AREA-02', 'Bay-03', TRUE, FALSE);

-- ---------------------------------------------------------------- 机台位（Stage）
INSERT INTO cim_stage (id, version, deleted, create_time, create_user,
                       stage_code, fab_code, bay_code, stage_name,
                       is_enabled, is_occupied)
VALUES ('seed-stage-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'STG-01', 'FAB-01', 'BAY-01', '机台位-01', TRUE, FALSE),
       ('seed-stage-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'STG-02', 'FAB-01', 'BAY-01', '机台位-02', TRUE, FALSE),
       ('seed-stage-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'STG-03', 'FAB-01', 'BAY-02', '机台位-03', TRUE, FALSE),
       ('seed-stage-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'STG-04', 'FAB-01', 'BAY-03', '机台位-04', TRUE, FALSE);
