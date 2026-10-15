-- RMS W7：腔体（Chamber）维度 + PPID 绑定腔体（配方双视角的设备侧）。
--
-- 背景（对齐行业）：
--   * 多腔体机台是半导体常态，配方是按**腔体**装载的。TSMC US6968245 / CN115082253B 描述的
--     「机台主界面信息管理」正是「对每个机台的每个 Recipe 编辑/选择可用 Chamber」。
--   * SEMI E139 (RaP) 的 RecipeContext / RecipeInstance 也把「腔体」作为配方实例化的上下文维度之一。
--   缺腔体维度则「机台视角」不完整：同一配方在 CH-A / CH-B 上可能装载不同版本或不同 PPID。
--
-- 1) device_chamber —— 机台下的腔体（机台 1—N 腔体）。UNIQUE(device_id, code) 保证同机台腔体编码唯一。
-- 2) rms_tool_recipe_map.chamber_id —— 把 PPID 映射细化到腔体（可空 = 机台级映射，兼容既有数据）。
--
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花）。

CREATE TABLE device_chamber (
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
    device_id     VARCHAR(64)  NOT NULL,
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    sort_no       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_device_chamber PRIMARY KEY (id),
    CONSTRAINT uk_device_chamber_code UNIQUE (device_id, code)
);
CREATE INDEX idx_dc_device ON device_chamber (device_id);

ALTER TABLE rms_tool_recipe_map ADD COLUMN chamber_id VARCHAR(64);
CREATE INDEX idx_trm_chamber ON rms_tool_recipe_map (chamber_id);
