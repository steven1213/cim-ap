-- MDS Reticle/光罩主数据 V32（2026-11-27，行业对照缺口③：Reticle Table）
-- 设计口径：
--   * Reticle（光罩/Photo Mask）是光刻工序的关键耗材级主数据：工序（CimOperation.reticleRef）、
--     流程步骤（CimFlowStep.reticleRef）、产品覆盖（CimProductFlow.reticleRef）此前只有
--     字符串引用——本表建表后引用闭环：写入侧校验存在性，光罩删除反向引用拒删。
--   * 层别 layer = 光刻层（如 LT/VT/MT/AA），自由文本（层名随工艺路线命名，不字典化）。
--   * status 挂字典 mds.reticle_status（在库/在用/损伤/过期/报废）；寿命 = 使用次数
--     （usage_count/usage_limit）+ 有效期（expiry_date）双轨，预警阈值判定归运行域。
--   * reticle_code 全局唯一、建后不可改（工序/步骤按 code 字符串引用）；带全库同款编辑锁三列。

-- ---------------------------------------------------------------- 光罩（Reticle）
CREATE TABLE cim_reticle (
    id                VARCHAR(64)  NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    reticle_code      VARCHAR(64)  NOT NULL,
    reticle_name      VARCHAR(128) NOT NULL,
    layer             VARCHAR(64)  NULL,
    status            VARCHAR(32)  NULL,
    home_equipment_id VARCHAR(64)  NULL,
    usage_count       INT          NOT NULL DEFAULT 0,
    usage_limit       INT          NULL,
    expiry_date       DATE         NULL,
    is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied       BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by       VARCHAR(64)  NULL,
    occupied_at       TIMESTAMP    NULL,
    CONSTRAINT pk_cim_reticle PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_reticle ON cim_reticle (reticle_code, deleted);
CREATE INDEX idx_cim_reticle_home ON cim_reticle (home_equipment_id);

-- ---------------------------------------------------------------- 演示种子（光罩两层）
INSERT INTO cim_reticle (id, version, deleted, create_time, create_user,
                         reticle_code, reticle_name, layer, status,
                         home_equipment_id, usage_count, usage_limit, expiry_date,
                         is_enabled, is_occupied)
VALUES ('seed-reticle-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'RT-LT-001', '光罩 LT 层 01 号', 'LT', 'ACTIVE', 'seed-eqp-03', 120, 5000, NULL, TRUE, FALSE),
       ('seed-reticle-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'RT-VT-001', '光罩 VT 层 01 号', 'VT', 'IN_STOCK', 'seed-eqp-03', 30, 5000, NULL, TRUE, FALSE);
