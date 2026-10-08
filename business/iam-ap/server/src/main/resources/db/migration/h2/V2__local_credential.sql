-- IAM 本地凭证表（M-login：口令两层派生，design.md §8 / README §5.1）
-- 与 BaseDefData/Auditable 同构：基类列 + 子类列。仅存第二层派生散列，不存明文口令。
CREATE TABLE local_credential (
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
    username       VARCHAR(128) NOT NULL,
    user_id        VARCHAR(128) NOT NULL,
    client_salt    VARCHAR(255) NOT NULL,
    server_hash    VARCHAR(512) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_local_credential PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_local_credential_username ON local_credential (username);
CREATE UNIQUE INDEX uk_local_credential_user_id ON local_credential (user_id);
