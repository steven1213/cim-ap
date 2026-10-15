-- RMS W5：接口幂等去重表 + IF-E2 下发任务阶段机表。
--
-- 1) rms_if_idempotency —— 落实 Req §5.2 IF-COM「幂等」基线：每消息 `id` 全局唯一、消费端去重。
--    EAP 网络超时重发同一 correlationId 时必须返回<b>首次</b>结果，绝不重复执行业务
--    （否则重试会生成重复配方版本 / 重复 Hold / 重复报警）。
--    UNIQUE(scope, idem_key) 是并发下的最后一道闸门（两个并发重试只有一个能插入）。
--
-- 2) rms_recipe_download_job —— 落实 Req §5.3 IF-E2 `RecipeDownloadProgress` 阶段机：
--    QUEUED→GRANTED→SENT_TO_TOOL→TOOL_ACKED→VERIFIED/FAILED，使「下发」成为可跟踪、
--    可回执、可回读校验的闭环流程（此前下发只返回一次 QUEUED，无任何推进入口）。
--    UNIQUE(correlation_id) 保证一次下发请求只有一条任务。
--
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花）。

CREATE TABLE rms_if_idempotency (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMP,
    event_time    TIMESTAMP,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
    scope         VARCHAR(32)  NOT NULL,
    idem_key      VARCHAR(160) NOT NULL,
    result_code   VARCHAR(32),
    response_json CLOB,
    CONSTRAINT pk_rms_if_idempotency PRIMARY KEY (id),
    CONSTRAINT uk_if_idem_scope_key UNIQUE (scope, idem_key)
);
CREATE INDEX idx_if_idem_created ON rms_if_idempotency (create_time);

CREATE TABLE rms_recipe_download_job (
    id            VARCHAR(64)  NOT NULL,
    description   VARCHAR(512),
    version       BIGINT,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMP,
    event_time    TIMESTAMP,
    create_user   VARCHAR(64),
    event_user    VARCHAR(64),
    event_name    VARCHAR(128),
    event_comment VARCHAR(512),
    trx_id        VARCHAR(64),
    tenant_id     VARCHAR(64),
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
    verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_rms_recipe_download_job PRIMARY KEY (id),
    CONSTRAINT uk_rdj_correlation UNIQUE (correlation_id)
);
CREATE INDEX idx_rdj_device ON rms_recipe_download_job (device_id);
CREATE INDEX idx_rdj_recipe ON rms_recipe_download_job (recipe_id);
CREATE INDEX idx_rdj_stage ON rms_recipe_download_job (stage);
CREATE INDEX idx_rdj_batch ON rms_recipe_download_job (batch_id);
