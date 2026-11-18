-- MDS V18：卡控开关（2026-11-18 方案定稿）。
--   定位：通用「卡控点启停注册表」——一行一个卡控点（如 shift.on_duty_check 当班校验、
--   shift.handover_freeze 交接冻结），MES 等消费方拉取后决定自己的卡控代码是否生效：
--   enabled=true 执行卡控判定，false 逻辑常驻但直接放行。
--   治理纪律：
--   · control_code 全局唯一、建后不可改（消费方按 code 引用，改名等于断约）；
--   · domain_code 分组（mshift/mrms/mes…），查询按域批量拉取（MES TTL 缓存源）；
--   · 不建独立管理页：开关 UI 就地嵌在各域业务页（动线不割裂），本表只是后端事实源；
--   · 默认 enabled=false——卡控默认不生效，由运营显式打开。

CREATE TABLE cim_control_switch (
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
    control_code    VARCHAR(64)   NOT NULL,
    domain_code     VARCHAR(32)   NOT NULL,
    name            VARCHAR(64)   NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_cim_control_switch PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_control_switch ON cim_control_switch (control_code, deleted);
CREATE INDEX idx_cim_control_switch_domain ON cim_control_switch (domain_code);
