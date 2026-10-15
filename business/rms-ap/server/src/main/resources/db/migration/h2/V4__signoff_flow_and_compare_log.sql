-- RMS W3.1：签核流做全（Req 3/20/41、FR-L2/L4/L5、IF-W1）+ 比对校验日志（Req 17/18）。
-- 1) rms_signoff 扩展：发起原因 / 签核路由 / 参数级差异附件 / 预设限时使用 / 电子签证据链
ALTER TABLE rms_signoff ADD COLUMN reason            VARCHAR(512);
ALTER TABLE rms_signoff ADD COLUMN sign_route        VARCHAR(16);
ALTER TABLE rms_signoff ADD COLUMN param_diff        CLOB;
ALTER TABLE rms_signoff ADD COLUMN temp_allow_from   TIMESTAMP;
ALTER TABLE rms_signoff ADD COLUMN temp_allow_to     TIMESTAMP;
ALTER TABLE rms_signoff ADD COLUMN temp_allow_scope  VARCHAR(128);
ALTER TABLE rms_signoff ADD COLUMN temp_allow_reason VARCHAR(512);
ALTER TABLE rms_signoff ADD COLUMN temp_allow_by     VARCHAR(64);
ALTER TABLE rms_signoff ADD COLUMN temp_allow_at     TIMESTAMP;
ALTER TABLE rms_signoff ADD COLUMN e_sign_hash       VARCHAR(128);

-- 2) 签核事件时间线（全程留痕 + 电子签证据链，FR-L4 / IF-W1）
CREATE TABLE rms_signoff_event (
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
    signoff_id    VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(32)  NOT NULL,
    actor         VARCHAR(64),
    comment       VARCHAR(512),
    detail        VARCHAR(512),
    occurred_at   TIMESTAMP,
    CONSTRAINT pk_rms_signoff_event PRIMARY KEY (id)
);
CREATE INDEX idx_rms_signoff_event_sid ON rms_signoff_event (signoff_id);

-- 3) 比对校验日志（Req 17 表格化 + 失败参数逐条可溯 / Req 18 汇总统计）
CREATE TABLE rms_compare_log (
    id                  VARCHAR(64)  NOT NULL,
    description         VARCHAR(512),
    version             BIGINT,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time         TIMESTAMP,
    event_time          TIMESTAMP,
    create_user         VARCHAR(64),
    event_user          VARCHAR(64),
    event_name          VARCHAR(128),
    event_comment       VARCHAR(512),
    trx_id              VARCHAR(64),
    tenant_id           VARCHAR(64),
    batch_id            VARCHAR(64),
    mode                VARCHAR(16)  NOT NULL,
    baseline_version_id VARCHAR(64),
    target_version_id   VARCHAR(64)  NOT NULL,
    recipe_code         VARCHAR(128),
    identical           BOOLEAN      NOT NULL DEFAULT FALSE,
    diff_count          INT          NOT NULL DEFAULT 0,
    result              VARCHAR(16)  NOT NULL,
    diff_items          CLOB,
    CONSTRAINT pk_rms_compare_log PRIMARY KEY (id)
);
CREATE INDEX idx_rms_compare_log_batch ON rms_compare_log (batch_id);
CREATE INDEX idx_rms_compare_log_result ON rms_compare_log (result);
