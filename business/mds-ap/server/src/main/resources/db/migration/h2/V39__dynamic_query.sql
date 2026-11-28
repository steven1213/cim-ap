-- MDS 动态查询/视图引擎 V39（2026-11-28，元数据驱动列表配置定稿）
-- 设计口径（用户拍板：Query 允许保存 SQL，但保存前必须通过服务端三层验证）：
--   * 三层模型：ViewModel（UI 渲染契约：列显隐/顺序/宽度/格式化）→ 按 code 关联
--     Query（取数契约：sql_text + 声明参数）→ 物理表（直接引用，无视图白名单层）。
--   * SQL 保存三层验证（DynamicQuerySqlValidator）：
--     ① 语法层：JSqlParser 解析必须成功，且必须是单条 SELECT（拒绝
--        INSERT/UPDATE/DELETE/MERGE/DDL/多语句/SELECT...INTO）；
--     ② 参数层：sql_text 中 :name 占位（跳过字符串字面量）与声明参数完全一致
--        （无未声明占位、无声明未使用）；列名/表名标识符由解析器保证合法形态；
--     ③ 真跑层：只读连接（setReadOnly）+ 语句超时 + LIMIT 1 dry-run——表/列
--        真实存在、语句可执行才允许保存。
--   * 运行时执行同款防护：只读连接、queryTimeout、maxRows 封顶、强制分页
--     （外层包装 LIMIT/OFFSET，页大小取 min(请求值, query.page_size_max)）；
--     值一律参数绑定，杜绝拼接。
--   * ViewModel 列 col_key 保存时对所属 Query 的 dry-run 结果集元数据校验，
--     杜绝"配置了不存在的列"。格式化类型 NONE/DATE/DATETIME/DICT/NUMBER
--     （DICT 时 format_arg=业务字典码）。标题 i18n 键优先、字面量兜底。
--   * 引用一律用 code 不用 id（跨环境迁移幂等）；查询/视图管理页带编辑锁三列。
--   * MES 后期直接复用契约：GET /view-models/{code}/meta + POST /data + GET /export。

-- ---------------------------------------------------------------- 查询定义（头）
CREATE TABLE cim_query_def (
    id                VARCHAR(64)  NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    query_code        VARCHAR(64)  NOT NULL,
    query_name        VARCHAR(128) NOT NULL,
    sql_text          CLOB         NOT NULL,
    page_size_max     INT          NOT NULL DEFAULT 500,
    is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    validated_at      TIMESTAMP    NULL,
    is_occupied       BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by       VARCHAR(64)  NULL,
    occupied_at       TIMESTAMP    NULL,
    CONSTRAINT pk_cim_query_def PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_query_def_code ON cim_query_def (query_code, deleted);

-- ---------------------------------------------------------------- 查询声明参数（全量替换）
CREATE TABLE cim_query_param (
    id                VARCHAR(64)  NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    query_id          VARCHAR(64)  NOT NULL,
    param_key         VARCHAR(64)  NOT NULL,
    param_label       VARCHAR(128) NOT NULL,
    param_type        VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    is_required       BOOLEAN      NOT NULL DEFAULT FALSE,
    dict_code         VARCHAR(64)  NULL,
    sort_no           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_query_param PRIMARY KEY (id)
);
CREATE INDEX idx_cim_query_param_query ON cim_query_param (query_id, deleted);

-- ---------------------------------------------------------------- 视图模型（头）
CREATE TABLE cim_view_model (
    id                VARCHAR(64)  NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    view_code         VARCHAR(64)  NOT NULL,
    view_name         VARCHAR(128) NOT NULL,
    query_code        VARCHAR(64)  NOT NULL,
    perm_code         VARCHAR(64)  NULL,
    allow_export      BOOLEAN      NOT NULL DEFAULT FALSE,
    page_size         INT          NOT NULL DEFAULT 20,
    is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_occupied       BOOLEAN      NOT NULL DEFAULT FALSE,
    occupied_by       VARCHAR(64)  NULL,
    occupied_at       TIMESTAMP    NULL,
    CONSTRAINT pk_cim_view_model PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_view_model_code ON cim_view_model (view_code, deleted);
CREATE INDEX idx_cim_view_model_query ON cim_view_model (query_code, deleted);

-- ---------------------------------------------------------------- 视图模型列（全量替换）
CREATE TABLE cim_view_model_col (
    id                VARCHAR(64)  NOT NULL,
    description       VARCHAR(512),
    version           BIGINT,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP,
    event_time        TIMESTAMP,
    create_user       VARCHAR(64),
    event_user        VARCHAR(64),
    event_name        VARCHAR(128),
    event_comment     VARCHAR(512),
    trx_id            VARCHAR(64),
    tenant_id         VARCHAR(64),
    view_model_id     VARCHAR(64)  NOT NULL,
    col_key           VARCHAR(64)  NOT NULL,
    col_title         VARCHAR(128) NULL,
    title_key         VARCHAR(128) NULL,
    width             INT          NULL,
    align             VARCHAR(8)   NOT NULL DEFAULT 'LEFT',
    is_visible        BOOLEAN      NOT NULL DEFAULT TRUE,
    format_type       VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    format_arg        VARCHAR(64)  NULL,
    sort_no           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cim_view_model_col PRIMARY KEY (id)
);
CREATE INDEX idx_cim_view_model_col_view ON cim_view_model_col (view_model_id, deleted);
