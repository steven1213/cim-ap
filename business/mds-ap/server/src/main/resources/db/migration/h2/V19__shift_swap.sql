-- MDS V19：换班申请（2026-11-18 方案定稿：员工自助申请 → 对方确认 → 自动落库）。
--   建模核心：换班是「申请单事实」——申请单独立成表，生效动作落 cim_shift_assignment
--   （(work_date, user_id) 唯一约束不动，换班=改 user 两行同事务交换）：
--   · swap_type：SWAP 对等互换（双方各让一班）/ SUBSTITUTE 单向代班（对方顶班）；
--   · status：PENDING（待对方确认）→ APPLIED / REJECTED / CANCELLED；
--     APPROVING 为班长审批预留档位（本期确认即生效，不进审批）；
--   · approval_id：审批单号预留列——将来接 approval-ap（Flowable）时
--     PENDING → TARGET_CONFIRMED → APPROVING → APPLIED 平滑升级，申请单结构不变。
--   治理纪律：
--   · work_date 一律记班次开始那天（与排班/交接同口径）；过期班禁换（服务端守门）；
--   · 双方 username 快照冗余（人员变动不丢可读性）；
--   · 同一班次存在未完结申请（PENDING）时禁止重复申请（服务端守门）；
--   · 生效=对方确认同事务落库，落库前复检冲突（排班可能在申请期间被改）。

CREATE TABLE cim_shift_swap (
    id                    VARCHAR(64)   NOT NULL,
    description           VARCHAR(512),
    version               BIGINT,
    deleted               BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time           TIMESTAMP,
    event_time            TIMESTAMP,
    create_user           VARCHAR(64),
    event_user            VARCHAR(64),
    event_name            VARCHAR(128),
    event_comment         VARCHAR(512),
    trx_id                VARCHAR(64),
    tenant_id             VARCHAR(64),
    swap_type             VARCHAR(16)   NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    initiator_id          VARCHAR(64)   NOT NULL,
    initiator_name        VARCHAR(64),
    target_id             VARCHAR(64)   NOT NULL,
    target_name           VARCHAR(64),
    initiator_work_date   DATE          NOT NULL,
    initiator_shift_code  VARCHAR(32)   NOT NULL,
    target_work_date      DATE,
    target_shift_code     VARCHAR(32),
    reason                VARCHAR(512),
    target_confirm_by     VARCHAR(64),
    target_confirm_name   VARCHAR(64),
    target_confirm_at     TIMESTAMP,
    target_confirm_note   VARCHAR(512),
    reject_reason         VARCHAR(512),
    applied_at            TIMESTAMP,
    approval_id           VARCHAR(64),
    CONSTRAINT pk_cim_shift_swap PRIMARY KEY (id)
);
CREATE INDEX idx_cim_shift_swap_status ON cim_shift_swap (status);
CREATE INDEX idx_cim_shift_swap_initiator ON cim_shift_swap (initiator_id);
CREATE INDEX idx_cim_shift_swap_target ON cim_shift_swap (target_id);
