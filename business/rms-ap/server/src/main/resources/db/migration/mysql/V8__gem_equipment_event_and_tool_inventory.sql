-- RMS W6：GEM 设备事件层 + 机台配方清单（MySQL 变体，字段与 h2 版本一一对应）。
--
-- 1) rms_equipment_event —— SEMI E30 GEM Collection Event（PP_CHANGE / PP_VERIFICATION_FAILED）落库，
--    idem_key 唯一落实 Req §5.2「消费端 eventId 去重」。
-- 2) rms_tool_recipe_inventory —— S7F19 机台配方目录快照，支撑孤儿配方（ORPHAN）与漂移（DRIFT）判定。

CREATE TABLE rms_equipment_event (
    id              VARCHAR(64)  NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    create_time     DATETIME,
    event_time      DATETIME,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    idem_key        VARCHAR(200) NOT NULL,
    tool_id         VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    ppid            VARCHAR(128),
    change_type     VARCHAR(16),
    op_id           VARCHAR(128),
    correlation_id  VARCHAR(128),
    payload         LONGTEXT,
    occurred_at     DATETIME,
    handled         TINYINT(1)   NOT NULL DEFAULT 0,
    handling_result VARCHAR(256),
    drift_detected  TINYINT(1)   NOT NULL DEFAULT 0,
    alarm_raised    TINYINT(1)   NOT NULL DEFAULT 0,
    hold_applied    TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ee_idem_key (idem_key),
    KEY idx_ee_tool (tool_id),
    KEY idx_ee_ppid (ppid),
    KEY idx_ee_type (event_type),
    KEY idx_ee_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rms_tool_recipe_inventory (
    id               VARCHAR(64)  NOT NULL,
    description      VARCHAR(512),
    version          BIGINT,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    create_time      DATETIME,
    event_time       DATETIME,
    create_user      VARCHAR(64),
    event_user       VARCHAR(64),
    event_name       VARCHAR(128),
    event_comment    VARCHAR(512),
    trx_id           VARCHAR(64),
    tenant_id        VARCHAR(64),
    device_id        VARCHAR(64)  NOT NULL,
    ppid_on_tool     VARCHAR(128) NOT NULL,
    tool_body_hash   VARCHAR(128),
    mapped_recipe_id VARCHAR(64),
    expected_hash    VARCHAR(128),
    reconcile_result VARCHAR(16)  NOT NULL DEFAULT 'OK',
    last_seen_at     DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tri_device_ppid (device_id, ppid_on_tool),
    KEY idx_tri_device (device_id),
    KEY idx_tri_result (reconcile_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
