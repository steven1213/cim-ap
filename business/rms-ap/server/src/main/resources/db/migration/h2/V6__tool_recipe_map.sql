-- RMS W4b：机台 PPID ↔ RMS 配方映射表（D7 / IF-E2）。
-- 业界惯例：EAP/设备只认机台侧配方名（PPID，GEM Process Program），RMS 侧以 UID 管理；
-- 二者映射由 RMS 维护（Req §5.3 IF-E2「与 RMS UID 的映射关系在 RMS 维护」）。
-- device_id 为空表示按机型（device_type_id）建立的通用映射。
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名；主键雪花）。

CREATE TABLE rms_tool_recipe_map (
    id             VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    version        BIGINT,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time    TIMESTAMP,
    event_time     TIMESTAMP,
    create_user    VARCHAR(64),
    event_user     VARCHAR(64),
    event_name     VARCHAR(128),
    event_comment  VARCHAR(512),
    trx_id         VARCHAR(64),
    tenant_id      VARCHAR(64),
    device_id      VARCHAR(64),
    device_type_id VARCHAR(64),
    ppid_on_tool   VARCHAR(128) NOT NULL,
    recipe_id      VARCHAR(64)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_rms_tool_recipe_map PRIMARY KEY (id)
);
CREATE INDEX idx_trm_device ON rms_tool_recipe_map (device_id);
CREATE INDEX idx_trm_device_type ON rms_tool_recipe_map (device_type_id);
CREATE INDEX idx_trm_ppid ON rms_tool_recipe_map (ppid_on_tool);
CREATE INDEX idx_trm_recipe ON rms_tool_recipe_map (recipe_id);
