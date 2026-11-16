-- MDS V10：状态模型（StateModel）+ 状态转换规则（StateTransition）
-- 设计（2026-11-13 讨论定稿，行业口径 SEMI E10 / E30 GEM / E87 / E157）：
--   * E30 GEM 状态模型方法论：状态模型 = 状态集合 + 转换集合 + 每条转换的触发条件；
--     转换图是设备侧与 Host 侧的契约（State Transition Reporting：上报「旧态→新态」，
--     Host 按表校验、非法迁移拒绝）。本迁移把该契约落成两张主数据表。
--   * 通用机制而非设备专用：MDS 里持有状态的对象不止设备（子单元按形态各有词表，
--     后续 SeasonJob / Lot 等域落地时各配模型），故模型表带 object_type + unit_form，
--     各业务域改状态时按 model_code 调 StateModelAdminService.validateTransition 守门。
--   * cim_state_model：model_code 唯一；dict_code 挂平台字典词表（值合法性由字典守门）；
--     state_codes 为本模型的有序状态集（CSV，须为词表子集——unit_state 合并词表按形态取子集）；
--     unit_form 仅 EQUIPMENT_UNIT 域模型有值（形态→模型映射的后端落点，替代前端硬编码）。
--   * cim_state_transition：(model_code, from_state, to_state) 唯一 allowlist；
--     trigger_note = E30「转换=契约」的触发条件说明（如 SBY→PRD=派工开始）。
--     矩阵勾选开关 = 建/物理删行（同 cim_equipment_capability 口径，软删会撞唯一索引）。
--   * 状态变更历史不建（沿用「流转归 EAP 运行时，主数据只存快照」口径）。
--   * 种子：EQUIPMENT=E10 六态 15 条标准转换（SBY 枢纽，UDT 例外直连 PRD/ENG）；
--     UNIT_LOAD_PORT=E87 端口态 6 条（TRANSFER_BLOCKED 为枢纽）；UNIT_CHAMBER=E157 简化
--     4 态 7 条；UNIT_GENERIC=IDLE/BUSY/DOWN 5 条。

-- ---------------------------------------------------------------- 状态模型
CREATE TABLE cim_state_model (
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
    model_code      VARCHAR(64)  NOT NULL,
    model_name      VARCHAR(128) NOT NULL,
    dict_code       VARCHAR(128) NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    unit_form       VARCHAR(32)  NULL,
    state_codes     VARCHAR(512) NOT NULL,
    CONSTRAINT pk_cim_state_model PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_state_model ON cim_state_model (model_code, deleted);

-- ---------------------------------------------------------------- 状态转换规则
CREATE TABLE cim_state_transition (
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
    model_code      VARCHAR(64)  NOT NULL,
    from_state      VARCHAR(64)  NOT NULL,
    to_state        VARCHAR(64)  NOT NULL,
    trigger_note    VARCHAR(255) NULL,
    CONSTRAINT pk_cim_state_transition PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cim_state_transition ON cim_state_transition (model_code, from_state, to_state, deleted);
CREATE INDEX idx_cim_state_transition_model ON cim_state_transition (model_code);

-- ---------------------------------------------------------------- 种子：状态模型
INSERT INTO cim_state_model (id, version, deleted, create_time, create_user,
                             model_code, model_name, dict_code, object_type, unit_form, state_codes)
VALUES ('seed-statemodel-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', '设备状态模型', 'mds.equip_state', 'EQUIPMENT', NULL,
        'PRD,SBY,ENG,SDT,UDT,NST'),
       ('seed-statemodel-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', '端口状态模型', 'mds.unit_state', 'EQUIPMENT_UNIT', 'LOAD_PORT',
        'READY_TO_LOAD,READY_TO_UNLOAD,TRANSFER_BLOCKED,OUT_OF_SERVICE'),
       ('seed-statemodel-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', '腔体状态模型', 'mds.unit_state', 'EQUIPMENT_UNIT', 'PROCESS_CHAMBER',
        'IDLE,EXECUTING,DOWN,PM'),
       ('seed-statemodel-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_GENERIC', '通用子单元状态模型', 'mds.unit_state', 'EQUIPMENT_UNIT', NULL,
        'IDLE,BUSY,DOWN');

-- ---------------------------------------------------------------- 种子：E10 六态标准转换（15 条，SBY 枢纽）
INSERT INTO cim_state_transition (id, version, deleted, create_time, create_user,
                                  model_code, from_state, to_state, trigger_note)
VALUES ('seed-strule-01', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SBY', 'PRD', '派工开始，机台投入生产'),
       ('seed-strule-02', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'PRD', 'SBY', '生产完成释放'),
       ('seed-strule-03', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SBY', 'ENG', '转工程验证/调机'),
       ('seed-strule-04', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'ENG', 'SBY', '工程验证完成'),
       ('seed-strule-05', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SBY', 'SDT', '进入计划保养/校准'),
       ('seed-strule-06', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SDT', 'SBY', '保养完成恢复'),
       ('seed-strule-07', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SBY', 'UDT', '待机中故障'),
       ('seed-strule-08', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'UDT', 'SBY', '修复完成回到待机'),
       ('seed-strule-09', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SBY', 'NST', '退出排程/停用'),
       ('seed-strule-10', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'NST', 'SBY', '重新纳入排程'),
       ('seed-strule-11', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'PRD', 'UDT', '生产中故障'),
       ('seed-strule-12', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'ENG', 'UDT', '工程中故障'),
       ('seed-strule-13', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'SDT', 'UDT', '保养中发现故障转维修'),
       ('seed-strule-14', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'UDT', 'PRD', '修复后直接验证生产'),
       ('seed-strule-15', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'EQUIPMENT', 'UDT', 'ENG', '修复后转工程验证');

-- ---------------------------------------------------------------- 种子：E87 端口态转换（6 条，TRANSFER_BLOCKED 枢纽）
INSERT INTO cim_state_transition (id, version, deleted, create_time, create_user,
                                  model_code, from_state, to_state, trigger_note)
VALUES ('seed-strule-21', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', 'OUT_OF_SERVICE', 'TRANSFER_BLOCKED', '启用服务（Enable）'),
       ('seed-strule-22', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', 'TRANSFER_BLOCKED', 'OUT_OF_SERVICE', '停用服务（Disable）'),
       ('seed-strule-23', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', 'TRANSFER_BLOCKED', 'READY_TO_LOAD', '放载具请求（RequestLoad）'),
       ('seed-strule-24', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', 'TRANSFER_BLOCKED', 'READY_TO_UNLOAD', '取载具请求（RequestUnload）'),
       ('seed-strule-25', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', 'READY_TO_LOAD', 'TRANSFER_BLOCKED', '载具到位/取消/超时'),
       ('seed-strule-26', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_LOAD_PORT', 'READY_TO_UNLOAD', 'TRANSFER_BLOCKED', '载具取走/取消/超时');

-- ---------------------------------------------------------------- 种子：腔体态转换（7 条）
INSERT INTO cim_state_transition (id, version, deleted, create_time, create_user,
                                  model_code, from_state, to_state, trigger_note)
VALUES ('seed-strule-31', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'IDLE', 'EXECUTING', '开始加工（配方启动）'),
       ('seed-strule-32', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'EXECUTING', 'IDLE', '加工完成'),
       ('seed-strule-33', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'IDLE', 'PM', '进入保养'),
       ('seed-strule-34', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'PM', 'IDLE', '保养完成'),
       ('seed-strule-35', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'IDLE', 'DOWN', '故障'),
       ('seed-strule-36', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'EXECUTING', 'DOWN', '加工中故障'),
       ('seed-strule-37', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_CHAMBER', 'DOWN', 'IDLE', '修复完成');

-- ---------------------------------------------------------------- 种子：通用子单元态转换（5 条）
INSERT INTO cim_state_transition (id, version, deleted, create_time, create_user,
                                  model_code, from_state, to_state, trigger_note)
VALUES ('seed-strule-41', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_GENERIC', 'IDLE', 'BUSY', '被占用（对准/搬运/缓存动作）'),
       ('seed-strule-42', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_GENERIC', 'BUSY', 'IDLE', '动作完成释放'),
       ('seed-strule-43', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_GENERIC', 'IDLE', 'DOWN', '故障'),
       ('seed-strule-44', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_GENERIC', 'BUSY', 'DOWN', '动作中故障'),
       ('seed-strule-45', 0, FALSE, CURRENT_TIMESTAMP, 'seed',
        'UNIT_GENERIC', 'DOWN', 'IDLE', '修复完成');
