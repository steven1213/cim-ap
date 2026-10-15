-- RMS W6：GEM 设备事件层 + 机台配方清单（设备侧配方治理）。
--
-- 对齐 SEMI E30 (GEM) 的配方管理事件模型——这是行业「事件驱动」的配方治理习惯，
-- 与本系统原有的「轮询巡检（FR-C6）」互补，形成「事件 + 轮询」双轨漂移检测：
--
-- 1) rms_equipment_event —— GEM Collection Event 落库。
--    * PP_CHANGE            ：机台侧配方被创建/编辑/删除（PPChangeName + PPChangeStatus）。
--                             行业惯例：主机收到后须回读重同步，避免仓库与机台长期不一致（Req §1.2 P1）。
--    * PP_VERIFICATION_FAILED：机台校验下发的配方内容失败（PPError）。行业惯例：该配方不得使用。
--    idem_key 唯一 —— 落实 Req §5.2「消费端 eventId 去重」，EAP 断线重连重放不会重复处置。
--
-- 2) rms_tool_recipe_inventory —— S7F19（请求机台配方目录）结果的落库快照。
--    工业价值：回答「机台上一共装了哪些配方」——这是成熟 RMS 的合规控制点：
--      * ORPHAN（孤儿）：机台有、RMS 无映射 → 未纳管配方（合规/安全风险）；
--      * DRIFT（漂移） ：映射存在但机台 HASH ≠ 生效版 HASH → 机台侧被改过；
--      * OK            ：映射存在且 HASH 一致。
--    另有「MISSING（RMS 有映射、机台未上报）」在服务层按映射反查得出，不落本表。
--    UNIQUE(device_id, ppid_on_tool) 保证同一机台同一 PPID 只保留最新一条快照。
--
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花）。

CREATE TABLE rms_equipment_event (
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
    idem_key        VARCHAR(200) NOT NULL,
    tool_id         VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    ppid            VARCHAR(128),
    change_type     VARCHAR(16),
    op_id           VARCHAR(128),
    correlation_id  VARCHAR(128),
    payload         CLOB,
    occurred_at     TIMESTAMP,
    handled         BOOLEAN      NOT NULL DEFAULT FALSE,
    handling_result VARCHAR(256),
    drift_detected  BOOLEAN      NOT NULL DEFAULT FALSE,
    alarm_raised    BOOLEAN      NOT NULL DEFAULT FALSE,
    hold_applied    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_rms_equipment_event PRIMARY KEY (id),
    CONSTRAINT uk_ee_idem_key UNIQUE (idem_key)
);
CREATE INDEX idx_ee_tool ON rms_equipment_event (tool_id);
CREATE INDEX idx_ee_ppid ON rms_equipment_event (ppid);
CREATE INDEX idx_ee_type ON rms_equipment_event (event_type);
CREATE INDEX idx_ee_created ON rms_equipment_event (create_time);

CREATE TABLE rms_tool_recipe_inventory (
    id               VARCHAR(64)  NOT NULL,
    description      VARCHAR(512),
    version          BIGINT,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time      TIMESTAMP,
    event_time       TIMESTAMP,
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
    last_seen_at     TIMESTAMP,
    CONSTRAINT pk_rms_tool_recipe_inventory PRIMARY KEY (id),
    CONSTRAINT uk_tri_device_ppid UNIQUE (device_id, ppid_on_tool)
);
CREATE INDEX idx_tri_device ON rms_tool_recipe_inventory (device_id);
CREATE INDEX idx_tri_result ON rms_tool_recipe_inventory (reconcile_result);
