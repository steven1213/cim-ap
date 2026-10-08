-- 登录失败锁定表（暴力破解防护，design.md §8.1(i)）
CREATE TABLE account_lock (
    id            VARCHAR(64)  NOT NULL,
    username      VARCHAR(128) NOT NULL,
    fail_count    INT          NOT NULL DEFAULT 0,
    first_fail_at DATETIME(3),
    last_fail_at  DATETIME(3),
    locked_until  DATETIME(3),
    CONSTRAINT pk_account_lock PRIMARY KEY (id),
    UNIQUE KEY uk_account_lock_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
