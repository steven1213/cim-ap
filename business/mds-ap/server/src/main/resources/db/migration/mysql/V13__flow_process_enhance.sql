-- MDS V13：流程第一批工艺增强（2026-11-16 用户确认范围）
--   #1 子流程引用 SUBFLOW：节点级引用（被引用方是一条普通 Flow，嵌套=引用非包含，
--      Camstar 口径）。cim_flow_step.flow_ref 引用目标流程的 flow_code；
--      cim_flow.flow_kind 仅作 MAIN/SUB 分类与列表筛选（不强制单层嵌套，允许多层但须无环）。
--   #2 决策 bin 词表：cim_flow_bin 按 flow 存 bin 分档（code/name/type PASS|FAIL），
--      XML 里作为 DECISION 节点的 <bin> 子元素（解析产物）；出边 condition 引用 bin code，
--      守门：bin 词表已定义时 DECISION 出边 condition 必须命中词表。
--   #4 批处理模式：batch_mode SERIAL（串行）/PARALLEL（并行）/BATCH（合批加工）+
--      batch_size 装载量（片），MVP 存字段不实现调度逻辑。
--   #6 Hold 点：hold_point 关键站后强制暂停等释放 + hold_reason 说明，MVP 存字段。

-- ---------------------------------------------------------------- 主档：MAIN/SUB 分类
ALTER TABLE cim_flow ADD COLUMN flow_kind VARCHAR(8) NOT NULL DEFAULT 'MAIN';

-- ---------------------------------------------------------------- 步骤：新工艺字段
ALTER TABLE cim_flow_step ADD COLUMN flow_ref VARCHAR(64);
ALTER TABLE cim_flow_step ADD COLUMN batch_mode VARCHAR(16);
ALTER TABLE cim_flow_step ADD COLUMN batch_size INT;
ALTER TABLE cim_flow_step ADD COLUMN hold_point BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cim_flow_step ADD COLUMN hold_reason VARCHAR(256);
CREATE INDEX idx_cim_flow_step_flowref ON cim_flow_step (flow_ref);

-- ---------------------------------------------------------------- 决策 bin 词表（解析产物）
CREATE TABLE cim_flow_bin (
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
    flow_id         VARCHAR(64)   NOT NULL,
    node_id         VARCHAR(64)   NOT NULL,
    bin_code        VARCHAR(32)   NOT NULL,
    bin_name        VARCHAR(64),
    bin_type        VARCHAR(8)    NOT NULL,
    sort_no         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_flow_bin PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_flow_bin ON cim_flow_bin (flow_id, node_id, bin_code, deleted);
CREATE INDEX idx_cim_flow_bin_flow ON cim_flow_bin (flow_id);
