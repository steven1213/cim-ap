-- MDS V15：流程独立规则层 cim_flow_rule（2026-11-17 用户确认范围）
--   规则与画布结构解耦：锚定「版本族 family_code + version_no」，规则条目不进
--   画布 XML——流程版本冻结后规则随版本只读，复制新版本时规则不自动复制
--   （规则可跨版本手选复用，先按显式维护口径）。
--   join_point 挂接点：BEFORE_STEP_ENTER（进站前）/ AFTER_STEP_COMPLETE
--   （步完出站前）/ BEFORE_DISPATCH（派工下发前）。
--   effect 处置：ALLOW（放行，显式白名单）/ HOLD（暂停等释放）/ SCRAP（报废）/
--   ROUTE_OVERRIDE（改派目标节点，effect_param 存 nodeId）/ INSERT_SEQUENCE
--   （插单，effect_param 存步骤编码）。
--   condition_expr 声明式条件（MPS 微语法：==/!=/>/>=/</<=、&&/||、括号、
--   '单引号字符串'/数字/true-false 字面量，标识符取运行期 vars），空 = 恒真。
--   priority 小者先评估；HOLD/SCRAP 终态短路。
--   template_key 标记内置模板来源（QTIME_OVER_HOLD / BIN_ROUTE）。

CREATE TABLE cim_flow_rule (
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
    family_code     VARCHAR(64)   NOT NULL,
    version_no      INT           NOT NULL DEFAULT 1,
    rule_code       VARCHAR(64)   NOT NULL,
    rule_name       VARCHAR(128),
    join_point      VARCHAR(32)   NOT NULL,
    step_code       VARCHAR(64),
    node_id         VARCHAR(64),
    edge_type       VARCHAR(16),
    condition_expr  VARCHAR(512),
    effect          VARCHAR(32)   NOT NULL,
    effect_param    VARCHAR(512),
    priority        INT           NOT NULL DEFAULT 100,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    template_key    VARCHAR(64),
    CONSTRAINT pk_cim_flow_rule PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_flow_rule ON cim_flow_rule (family_code, version_no, rule_code, deleted);
CREATE INDEX idx_cim_flow_rule_family ON cim_flow_rule (family_code, version_no);
