-- MDS V11：编码规则（NamingRule，有序段列表模型）+ 分桶流水号计数器
-- 设计（2026-11-16 与用户定稿，v2：单字符串模板升级为「有序段列表 + 段类型解析器」）：
--   * 半导体行业无 SEMI 统一编码标准——业界通行模式是「区域码 + 机型码 + 流水号」分段模板，
--     MES 产品普遍内置配置化 Key Generation（模板段 + 按作用域分桶计数器），本迁移即其主数据落点。
--   * segments 为 JSON 段列表（列表顺序即编码顺序），段四类型各有解析器：
--       LITERAL 常量（text）          → 原样输出
--       ENUM    枚举（var+dictKey|values）→ 取上下文变量值并校验在字典/自定义值列表内
--       DATE    日期（pattern）       → 按当前时间格式化
--       SEQ     序号（length/start/scope）→ 分桶占号（参数段级化，一规则可含多 SEQ 段）
--     每段声明 length（字符长度）：生成时逐段校验输出长度一致（整码定长，扫码/解析惯例）；
--     服务端按段类型派生正则做手填守门；解析器注册表分发，新增段类型零侵入。
--   * 「同一 object_type 只允许一条启用规则」用 active_marker 列落库级保证：
--     enabled=TRUE 时 marker='ENABLED'（(object_type, marker) 唯一索引挡并发双开），
--     停用时 marker=id（互不冲突，可存多条停用规则备切换）。
--   * cim_naming_counter：(rule_id, segment_index, scope_key) 分桶计数——段级化后每个
--     SEQ 段独立计数器；scope 支持 GLOBAL/BY_PARENT/BY_LOCATION/BY_TYPE/BY_DATE（按天重置，
--     scope_key 带口径前缀防撞）；current_value 只增不回收；占号在业务 create 同事务 FOR UPDATE。
--   * 强守门口径：create 编码留空 + 规则启用 → 自动生成占号；手填编码不匹配段派生正则
--     → 400 拒绝；规则停用 → 放行（回退人工命名）；种子默认停用，不改变演示数据行为。
--   * 种子：EQUIPMENT 一条演示规则（EQP- + 两位流水），停用态。

-- ---------------------------------------------------------------- 编码规则
CREATE TABLE cim_naming_rule (
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
    object_type     VARCHAR(32)   NOT NULL,
    rule_name       VARCHAR(128)  NOT NULL,
    segments        VARCHAR(2000) NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    active_marker   VARCHAR(64)   NOT NULL,
    remark          VARCHAR(512),
    CONSTRAINT pk_cim_naming_rule PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_naming_rule_active ON cim_naming_rule (object_type, active_marker, deleted);
CREATE INDEX idx_cim_naming_rule_type ON cim_naming_rule (object_type);

-- ---------------------------------------------------------------- 流水号计数器（段级）
CREATE TABLE cim_naming_counter (
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
    rule_id         VARCHAR(64)  NOT NULL,
    segment_index   INT          NOT NULL,
    scope_key       VARCHAR(128) NOT NULL,
    current_value   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_naming_counter PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_naming_counter ON cim_naming_counter (rule_id, segment_index, scope_key);

-- ---------------------------------------------------------------- 种子：EQUIPMENT 演示规则（停用态）
INSERT INTO cim_naming_rule (id, version, deleted, create_time, create_user,
                             object_type, rule_name, segments,
                             enabled, active_marker, remark)
VALUES ('seed-naming-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', '设备编码·前缀+流水',
        '[{"type":"LITERAL","text":"EQP-","length":4},{"type":"SEQ","length":2,"start":1,"scope":"GLOBAL"}]',
        FALSE, 'seed-naming-01',
        '演示段列表：常量前缀 + 两位全局流水；启用前请先试算验证');
