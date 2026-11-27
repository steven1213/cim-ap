-- MDS 耗材台账 V34（2026-11-27，行业对照缺口⑤：Consumable 台账）
-- 设计口径：
--   * 耗材 = 生产辅助物料（化学品/研磨液/特气/备件/消耗工具）的台账级主数据：
--     编码/规格型号/类别/计量单位/寿命管理/库存水位。领用出库与盘点流水归 MES/物料域，
--     主数据只存当前水位（stock_qty）与安全水位（min_qty）。
--   * 寿命管理三轨（字典 mds.consumable_life_type）：CYCLE 按使用次数 / DAYS 按天数 /
--     QTY 按产量；life_limit = 寿命上限、life_used = 已消耗、warn_percent = 预警阈值（%），
--     到期判定与预警触发归运行域。
--   * cons_code 全局唯一、建后不可改；带全库同款编辑锁三列。

-- ---------------------------------------------------------------- 耗材（Consumable）
CREATE TABLE cim_consumable (
    id                VARCHAR(64)   NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    cons_code         VARCHAR(64)   NOT NULL,
    cons_name         VARCHAR(128)  NOT NULL,
    spec_model        VARCHAR(128)  NULL,
    cons_type         VARCHAR(32)   NULL,
    unit              VARCHAR(16)   NULL,
    life_type         VARCHAR(16)   NULL,
    life_limit        NUMERIC(12,2) NULL,
    life_used         NUMERIC(12,2) NOT NULL DEFAULT 0,
    warn_percent      INT           NULL,
    stock_qty         NUMERIC(12,2) NOT NULL DEFAULT 0,
    min_qty           NUMERIC(12,2) NULL,
    home_equipment_id VARCHAR(64)   NULL,
    is_enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    is_occupied       BOOLEAN       NOT NULL DEFAULT FALSE,
    occupied_by       VARCHAR(64)   NULL,
    occupied_at       TIMESTAMP     NULL,
    CONSTRAINT pk_cim_consumable PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_consumable ON cim_consumable (cons_code, deleted);
CREATE INDEX idx_cim_consumable_home ON cim_consumable (home_equipment_id);

-- ---------------------------------------------------------------- 演示种子（两类耗材）
INSERT INTO cim_consumable (id, version, deleted, create_time, create_user,
                            cons_code, cons_name, spec_model, cons_type, unit,
                            life_type, life_limit, life_used, warn_percent,
                            stock_qty, min_qty, home_equipment_id,
                            is_enabled, is_occupied)
VALUES ('seed-cons-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'CM-SL-001', 'CMP 研磨液 A 型', 'SL-200L', 'SLURRY', 'BOTTLE',
        'NONE', NULL, 0, 30, 8.5, 4.0, 'seed-eqp-03', TRUE, FALSE),
       ('seed-cons-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'CM-PD-001', '抛光垫（Polishing Pad）', 'IC1000-6IN', 'PART', 'EA',
        'CYCLE', 300, 150, 20, 6, 3.0, 'seed-eqp-03', TRUE, FALSE);
