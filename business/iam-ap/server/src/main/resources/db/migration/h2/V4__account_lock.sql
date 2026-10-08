-- 登录失败锁定表（暴力破解防护，design.md §8.1(i)）
CREATE TABLE account_lock (
    id            VARCHAR(64)  NOT NULL,
    username      VARCHAR(128) NOT NULL,
    fail_count    INT          NOT NULL DEFAULT 0,
    first_fail_at TIMESTAMP,
    last_fail_at  TIMESTAMP,
    locked_until  TIMESTAMP,
    CONSTRAINT pk_account_lock PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_account_lock_username ON account_lock (username);
