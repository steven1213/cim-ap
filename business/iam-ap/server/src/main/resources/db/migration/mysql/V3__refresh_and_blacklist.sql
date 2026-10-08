-- 刷新令牌表（M-refresh：刷新令牌轮转，design.md §8.1(h)）
CREATE TABLE refresh_token (
    id               VARCHAR(64)  NOT NULL,
    user_id          VARCHAR(128) NOT NULL,
    token_hash       VARCHAR(128) NOT NULL,
    access_token_jti VARCHAR(128),
    expires_at       DATETIME(3)  NOT NULL,
    revoked          TINYINT(1)   NOT NULL DEFAULT 0,
    revoked_at       DATETIME(3),
    CONSTRAINT pk_refresh_token PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_refresh_token_hash ON refresh_token (token_hash);

-- 令牌黑名单表（M-refresh：登出 / 主动吊销单条访问令牌，design.md §8.1(h)）
CREATE TABLE token_blacklist (
    id          VARCHAR(64)  NOT NULL,
    jti         VARCHAR(128) NOT NULL,
    user_id     VARCHAR(128),
    expires_at  DATETIME(3),
    revoked_at  DATETIME(3)  NOT NULL,
    CONSTRAINT pk_token_blacklist PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_token_blacklist_jti ON token_blacklist (jti);
