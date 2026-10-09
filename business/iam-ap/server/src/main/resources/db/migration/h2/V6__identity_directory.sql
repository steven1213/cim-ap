-- 身份目录与组织架构（Wave 0 / identity-directory.md §3）
-- 表结构 = BaseDefData/Auditable 基类列 + 子类列（统一小写蛇形命名）。
--
-- 权威源分工（§1）：AD 管「人」与行政组织（source=AD_SYNCED，只读同步，IAM 不覆盖）；
-- IAM 管制造组织（厂区→车间→产线→工序→责任区）与非 AD 人员（source=IAM_MANAGED）。
-- 同树混源靠 source 列隔离，同步器只写自己那一份。

-- 组织节点：单表自引用树 + 物化路径（path 前缀匹配即可查全部祖先授予）
CREATE TABLE org_node (
    id            VARCHAR(64)  NOT NULL,
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
    parent_id      VARCHAR(64),
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    node_type      VARCHAR(16)  NOT NULL,
    path           VARCHAR(512),
    sort_no        INT          DEFAULT 0,
    source         VARCHAR(16)  NOT NULL,
    external_id    VARCHAR(128),
    status         VARCHAR(16)  NOT NULL,
    updated_at     TIMESTAMP,
    CONSTRAINT pk_org_node PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_org_node_source_code ON org_node (source, code);
CREATE INDEX ix_org_node_parent ON org_node (parent_id);
CREATE INDEX ix_org_node_path ON org_node (path);

-- 用户档案：与 local_credential 解耦，靠 user_id 关联（互不要求存在）
CREATE TABLE user_profile (
    id            VARCHAR(64)  NOT NULL,
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
    user_id        VARCHAR(128) NOT NULL,
    employee_no    VARCHAR(64),
    display_name   VARCHAR(128),
    email          VARCHAR(128),
    mobile         VARCHAR(128),
    job_title      VARCHAR(64),
    status         VARCHAR(16)  NOT NULL,
    source         VARCHAR(16)  NOT NULL,
    synced_at      TIMESTAMP,
    CONSTRAINT pk_user_profile PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_profile_user ON user_profile (user_id);

-- 用户—组织归属：支持多归属（多能工 / 跨线支援，一人多工序）
CREATE TABLE user_org (
    id            VARCHAR(64)  NOT NULL,
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
    user_id        VARCHAR(128) NOT NULL,
    org_id         VARCHAR(64)  NOT NULL,
    is_primary     BOOLEAN      NOT NULL DEFAULT FALSE,
    source         VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_user_org PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_org ON user_org (user_id, org_id);
CREATE INDEX ix_user_org_org ON user_org (org_id);

-- 组织级准入授予（组织 → ap + 粗角色组；include_children 决定是否覆盖子组织）
CREATE TABLE org_app_assignment (
    id            VARCHAR(64)  NOT NULL,
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
    org_id         VARCHAR(64)  NOT NULL,
    app_code       VARCHAR(64)  NOT NULL,
    roles          VARCHAR(512),
    include_children BOOLEAN    NOT NULL DEFAULT TRUE,
    status         VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_org_app_assignment PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_org_app ON org_app_assignment (org_id, app_code);
CREATE INDEX ix_org_app_app ON org_app_assignment (app_code);

-- 目录水位（与 token_version 同构：变更即 +1，业务侧轻量比对后决定是否重拉）
CREATE TABLE directory_watermark (
    id            VARCHAR(64)  NOT NULL,
    scope         VARCHAR(32)  NOT NULL,
    version       BIGINT       NOT NULL,
    updated_at    TIMESTAMP,
    CONSTRAINT pk_directory_watermark PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_directory_watermark_scope ON directory_watermark (scope);
