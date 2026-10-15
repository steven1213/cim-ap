-- RMS W7：腔体（Chamber）维度 + PPID 绑定腔体（MySQL 变体，字段与 h2 版本一一对应）。
--
-- 1) device_chamber —— 机台下的腔体（机台 1—N）。对齐 TSMC US6968245「每机台每 Recipe 选可用 Chamber」。
-- 2) rms_tool_recipe_map.chamber_id —— PPID 映射细化到腔体（可空 = 机台级，兼容既有数据）。

CREATE TABLE device_chamber (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    create_time   DATETIME,
    event_time    DATETIME,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    device_id     VARCHAR(64)  NOT NULL,
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    sort_no       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_chamber_code (device_id, code),
    KEY idx_dc_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE rms_tool_recipe_map ADD COLUMN chamber_id VARCHAR(64);
ALTER TABLE rms_tool_recipe_map ADD INDEX idx_trm_chamber (chamber_id);
