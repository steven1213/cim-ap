-- MDS V12：制造流程（Flow，XML 画布为源 + 有向图解析产物表）——MVP 版
-- 设计（2026-11-16 与用户定稿，v2：画布存储改为「XML 单一事实源」）：
--   行业对标 Camstar Flow/Operation、Applied Materials PROMIS Router、上扬 myCIM PRP；
--   SEMI 无统一 Route 标准，取 MES 通行口径收敛。
--   * 存储口径（2026-11-16 用户定稿）：
--       cim_flow.flow_xml = 画布 XML 原文件（节点+边+坐标的完整序列化）——渲染画布和
--       修改画布都以它为源（前端 DOMParser 渲染、保存时整份回传）；每次保存后由后端
--       重新解析，重建解析产物表。
--       cim_flow_step = 解析产物：按 XML 原顺序保留节点（step_no 即顺序号）+ 业务字段
--       解析成列（step_type/recipe_ref/equip_type/qtime/rework_limit），供结构化查询与
--       MES 消费；坐标等渲染信息不落表（只在 XML）。
--       cim_flow_edge = 解析产物：边结构（from/to/语义/条件），供下一步查询与选路。
--   * cim_flow：flow_code 走 NamingRule FLOW 对象占号（全局唯一建后不可改）；
--     tech_code 逻辑引用 cim_technology（可空）；
--     status 生命周期 DRAFT（草稿，可编辑）→ PUBLISHED（发布，只读）→ RETIRED（停用）；
--     发布前必须通过图校验守门（见 FlowAdminService.validateGraph）。
--   * cim_flow_step：step_type 六类——START（唯一入口）/ END（至少一出口）为结构性类型；
--     PROCESS（加工）/ INSPECT（检验）/ REWORK（返工）/ DECISION（分档决策）为业务类型，
--     挂平台字典 mds.process_step_type（Java 种子，MdsDictSeedService）。
--     recipe_ref 逻辑配方引用（RMS 域，MVP 不守门）、equip_type 约束设备类型（守门须存在）、
--     qtime_min/qtime_max 队留时间上下限（分钟）、rework_limit 返工次数上限。
--   * cim_flow_edge：edge_type 四类语义——MAIN（主线，蓝）/ PASS（决策·合格，绿）/
--     FAIL（决策·不合格，琥珀）/ REWORK（返工回边，红）；DECISION 出边必须 PASS/FAIL，
--     其余节点出边必须 MAIN/REWORK；condition_expr 条件表达式（MVP 存字段不解析执行）。
--   * 守门（保存/发布时）：节点类型白名单 + 边端点存在 + 边语义合法（解析时把关）；
--     唯一 START + 至少一 END + START 沿 MAIN 可达 END + REWORK 回边目标须是起点上游
--     （防死循环）+ 忽略 REWORK 边全图无环 + 引用真实（validate/publish 把关）。
--   * MVP 不做版本链（status+copy 演进已够）；污染等级/Future Action/EDC 联动留字段位后续迭代。

-- ---------------------------------------------------------------- 制造流程主档
CREATE TABLE cim_flow (
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
    flow_code       VARCHAR(64)   NOT NULL,
    flow_name       VARCHAR(128)  NOT NULL,
    tech_code       VARCHAR(64),
    status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    flow_xml        CLOB,
    remark          VARCHAR(512),
    CONSTRAINT pk_cim_flow PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_flow_code ON cim_flow (flow_code, deleted);
CREATE INDEX idx_cim_flow_tech ON cim_flow (tech_code);
CREATE INDEX idx_cim_flow_status ON cim_flow (status);

-- ---------------------------------------------------------------- 流程步骤节点（XML 解析产物，按原顺序）
CREATE TABLE cim_flow_step (
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
    step_code       VARCHAR(64)   NOT NULL,
    step_name       VARCHAR(128),
    step_type       VARCHAR(16)   NOT NULL,
    step_no         INT           NOT NULL DEFAULT 0,
    recipe_ref      VARCHAR(64),
    equip_type      VARCHAR(64),
    qtime_min       INT,
    qtime_max       INT,
    rework_limit    INT,
    CONSTRAINT pk_cim_flow_step PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_flow_step_code ON cim_flow_step (flow_id, step_code, deleted);
CREATE INDEX idx_cim_flow_step_flow ON cim_flow_step (flow_id);
CREATE INDEX idx_cim_flow_step_eqptype ON cim_flow_step (equip_type);

-- ---------------------------------------------------------------- 流程连接边（XML 解析产物）
CREATE TABLE cim_flow_edge (
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
    from_step_id    VARCHAR(64)   NOT NULL,
    to_step_id      VARCHAR(64)   NOT NULL,
    edge_type       VARCHAR(16)   NOT NULL,
    condition_expr  VARCHAR(512),
    CONSTRAINT pk_cim_flow_edge PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_flow_edge ON cim_flow_edge (flow_id, from_step_id, to_step_id, edge_type, deleted);
CREATE INDEX idx_cim_flow_edge_flow ON cim_flow_edge (flow_id);
