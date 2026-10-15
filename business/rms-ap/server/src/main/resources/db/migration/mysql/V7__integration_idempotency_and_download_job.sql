-- RMS W5：接口幂等去重表 + IF-E2 下发任务阶段机表（MySQL 变体，字段与 h2 版本一一对应）。
--
-- 1) rms_if_idempotency —— Req §5.2 IF-COM「幂等」：重复 correlationId 返回首次结果，
--    UNIQUE(scope, idem_key) 保证并发重试只有一个能落库。
-- 2) rms_recipe_download_job —— Req §5.3 IF-E2 `RecipeDownloadProgress` 阶段机：
--    QUEUED→GRANTED→SENT_TO_TOOL→TOOL_ACKED→VERIFIED/FAILED。

CREATE TABLE rms_if_idempotency (
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
    scope         VARCHAR(32)  NOT NULL,
    idem_key      VARCHAR(160) NOT NULL,
    result_code   VARCHAR(32),
    response_json LONGTEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_if_idem_scope_key (scope, idem_key),
    KEY idx_if_idem_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rms_recipe_download_job (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    create_time    DATETIME,
    event_time     DATETIME,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    correlation_id VARCHAR(128) NOT NULL,
    device_id      VARCHAR(64)  NOT NULL,
    recipe_id      VARCHAR(64)  NOT NULL,
    version_id     VARCHAR(64),
    ppid_on_tool   VARCHAR(128),
    batch_id       VARCHAR(64),
    lot_id         VARCHAR(64),
    requestor      VARCHAR(128),
    download_mode  VARCHAR(16)  NOT NULL DEFAULT 'IMMEDIATE',
    stage          VARCHAR(24)  NOT NULL,
    tool_ack       VARCHAR(32),
    error_desc     VARCHAR(512),
    body_hash      VARCHAR(128),
    verified       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rdj_correlation (correlation_id),
    KEY idx_rdj_device (device_id),
    KEY idx_rdj_recipe (recipe_id),
    KEY idx_rdj_stage (stage),
    KEY idx_rdj_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
