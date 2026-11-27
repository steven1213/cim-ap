-- V24 (P3-2，2026-11-25)：流程域事件历史表（拍板项 D）
-- 一张通用「动作级事件表」：生命周期动作（CREATE/UPDATE/DELETE/FREEZE/UNFREEZE/ACTIVATE/
-- DISABLE/COPY/SAVE_XML/REFRESH_OP/IMPORT）+ 覆盖数据变更（指派/KV 替换）。
-- detail = 变更摘要 JSON（计数/关键码），不做逐实体全行快照（平台 @History(SNAPSHOT) 仅在
-- AbstractJpaService 写路径自动触发，MDS 未用该基类，故自持动作级事件表；
-- 字段口径吸收平台 Hist：biz_id→flow_id、op_type→action、operator→actor、op_time→event_time）。
CREATE TABLE cim_flow_event_hist (
    id           VARCHAR(64)   NOT NULL,
    flow_id      VARCHAR(64)   NOT NULL,
    flow_code    VARCHAR(64)   NOT NULL,
    entity_type  VARCHAR(32)   NOT NULL,
    action       VARCHAR(32)   NOT NULL,
    actor        VARCHAR(64),
    detail       CLOB,
    event_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cim_flow_event_hist PRIMARY KEY (id)
);
CREATE INDEX idx_flow_event_flow ON cim_flow_event_hist (flow_id, event_time);
