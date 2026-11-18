-- MDS V17：班组排班域（2026-11-18 方案定稿：排到人、无换班申请、含交接班记录本）。
--   建模核心：「定义可演进，排班与交接是事实」——两层物理分离：
--   · cim_shift_def / cim_shift_pattern_def：定义类（班次时间段、轮班周期模式），
--     低频变更、被排班与交接记录引用，引用中禁删（服务端守门）；
--   · cim_shift_assignment：排班事实（一人一天一个班，(work_date, user_id) 唯一），
--     source 记录来源（MANUAL 人工格不被批量生成覆盖）；
--   · cim_shift_log：交接班记录本（交班人提交 → 接班人签收 → 锁定只读），
--     设备异常/遗留事项/备注三结构化字段，责任文书签收后不可改。
--   治理纪律：
--   · 跨天班次（cross_day，如 22:00-06:00）work_date 一律记班次开始那天；
--   · user 只存 id + username 快照（主数据在 IAM，人员变动不丢历史可读性）；
--   · shift_code 逻辑引用（同 Site→Fab 惯例，不建外键）。

CREATE TABLE cim_shift_def (
    id              VARCHAR(64)   NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP,
    event_time      TIMESTAMP,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    shift_code      VARCHAR(32)   NOT NULL,
    shift_name      VARCHAR(64)   NOT NULL,
    start_time      VARCHAR(5)    NOT NULL,
    end_time        VARCHAR(5)    NOT NULL,
    cross_day       BOOLEAN       NOT NULL DEFAULT FALSE,
    color           VARCHAR(16),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_no         INT           NOT NULL DEFAULT 100,
    CONSTRAINT pk_cim_shift_def PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_shift_def ON cim_shift_def (shift_code, deleted);

CREATE TABLE cim_shift_pattern_def (
    id              VARCHAR(64)   NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP,
    event_time      TIMESTAMP,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    pattern_code    VARCHAR(32)   NOT NULL,
    pattern_name    VARCHAR(64)   NOT NULL,
    cycle_days      INT           NOT NULL,
    pattern_detail  VARCHAR(512)  NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_no         INT           NOT NULL DEFAULT 100,
    CONSTRAINT pk_cim_shift_pattern_def PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_shift_pattern_def ON cim_shift_pattern_def (pattern_code, deleted);

CREATE TABLE cim_shift_assignment (
    id              VARCHAR(64)   NOT NULL,
    description     VARCHAR(512),
    version         BIGINT,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP,
    event_time      TIMESTAMP,
    create_user     VARCHAR(64),
    event_user      VARCHAR(64),
    event_name      VARCHAR(128),
    event_comment   VARCHAR(512),
    trx_id          VARCHAR(64),
    tenant_id       VARCHAR(64),
    work_date       DATE          NOT NULL,
    user_id         VARCHAR(64)   NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    shift_code      VARCHAR(32)   NOT NULL,
    source          VARCHAR(16)   NOT NULL,
    CONSTRAINT pk_cim_shift_assignment PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_shift_assignment ON cim_shift_assignment (work_date, user_id, deleted);
CREATE INDEX idx_cim_shift_assignment_date ON cim_shift_assignment (work_date);

CREATE TABLE cim_shift_log (
    id               VARCHAR(64)   NOT NULL,
    description      VARCHAR(512),
    version          BIGINT,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time      TIMESTAMP,
    event_time       TIMESTAMP,
    create_user      VARCHAR(64),
    event_user       VARCHAR(64),
    event_name       VARCHAR(128),
    event_comment    VARCHAR(512),
    trx_id           VARCHAR(64),
    tenant_id        VARCHAR(64),
    work_date        DATE          NOT NULL,
    shift_code       VARCHAR(32)   NOT NULL,
    handover_user    VARCHAR(64)   NOT NULL,
    handover_name    VARCHAR(64),
    receiver_user    VARCHAR(64),
    receiver_name    VARCHAR(64),
    equipment_issue  VARCHAR(2000),
    pending_items    VARCHAR(2000),
    remarks          VARCHAR(2000),
    acked            BOOLEAN       NOT NULL DEFAULT FALSE,
    ack_by           VARCHAR(64),
    ack_name         VARCHAR(64),
    ack_at           TIMESTAMP,
    CONSTRAINT pk_cim_shift_log PRIMARY KEY (id)
);
CREATE INDEX idx_cim_shift_log_date ON cim_shift_log (work_date);
