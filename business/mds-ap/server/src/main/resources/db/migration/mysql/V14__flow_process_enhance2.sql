-- MDS V14：流程第二批工艺增强 + 版本生命周期改造（2026-11-16 用户确认范围）
--   #1 检验配置：INSPECT 节点检验计划引用 inspect_plan（逻辑引用 EDC 检验计划编码，
--      MVP 存字段不守门存在性）+ sampling_rate 抽样率（百分比 0-100）。
--   #2 跨站 Q-time：flow 级站间约束 cim_flow_qtime——从 from 站离开到 to 站到达的
--      最大允许间隔（分钟），超限动作 action：HOLD（暂停等释放）/ SCRAP（报废）。
--      from/to 引用本流程节点的 XML node_id；XML 里作为 <canvas> 下的 <qtime> 子元素。
--   #3 分批/合批结构节点：step_type 新增 SPLIT（一个批次拆分为多条并行支路）/
--      MERGE（多条支路汇合回一个批次）。守门：SPLIT 出边 ≥2、MERGE 入边 ≥2（发布时），
--      出入边语义仅 MAIN；调度语义 MVP 存字段不实现。
--   #4 版本生命周期（用户定稿）：status 改四态 UNFROZEN（未冻结/草稿，可编辑）→
--      FROZEN（已冻结/待激活）→ ACTIVE（生效，生产线现行）→ DISABLED（禁用/淘汰）；
--      同一版本族（family_code）同一时刻最多 0/1 个 ACTIVE，激活新版时旧 ACTIVE
--      自动降级 FROZEN。family_code + version_no 标识「一套 Flow 的第几个版本」。
--      旧状态映射：DRAFT→UNFROZEN、PUBLISHED→ACTIVE、RETIRED→DISABLED。

-- ---------------------------------------------------------------- 版本族与状态四态化
ALTER TABLE cim_flow ADD COLUMN family_code VARCHAR(64);
ALTER TABLE cim_flow ADD COLUMN version_no INT NOT NULL DEFAULT 1;
UPDATE cim_flow SET status = 'UNFROZEN' WHERE status = 'DRAFT';
UPDATE cim_flow SET status = 'ACTIVE'   WHERE status = 'PUBLISHED';
UPDATE cim_flow SET status = 'DISABLED' WHERE status = 'RETIRED';
UPDATE cim_flow SET family_code = flow_code WHERE family_code IS NULL;
CREATE INDEX idx_cim_flow_family ON cim_flow (family_code);

-- ---------------------------------------------------------------- 步骤：检验配置
ALTER TABLE cim_flow_step ADD COLUMN inspect_plan VARCHAR(64);
ALTER TABLE cim_flow_step ADD COLUMN sampling_rate NUMERIC(5,2);

-- ---------------------------------------------------------------- 跨站 Q-time（解析产物）
CREATE TABLE cim_flow_qtime (
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
    from_node_id    VARCHAR(64)   NOT NULL,
    to_node_id      VARCHAR(64)   NOT NULL,
    max_minutes     INT           NOT NULL,
    action          VARCHAR(16)   NOT NULL,
    CONSTRAINT pk_cim_flow_qtime PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_flow_qtime ON cim_flow_qtime (flow_id, from_node_id, to_node_id, deleted);
CREATE INDEX idx_cim_flow_qtime_flow ON cim_flow_qtime (flow_id);
