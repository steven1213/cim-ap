-- MDS V23：流程域 P2 增量字段（2026-11-25，flow-modeling/50-roadmap.md P2）
--   * cim_flow_qtime.alarm_code：Q-time 超限联动告警码（逻辑引用告警定义，MVP 不守门存在性）。
--     超限动作语义 V14 已有 action（HOLD/SCRAP）承载，不重复加 timeout_action 列。
--   * cim_flow_step.npw_limit：NPW（Non-Product Wafer）投片上限（片数；null=不限）。
--     产品域口径——工程批/表面处理批的 NPW 数量控制在工艺路线上的落点。
--   * cim_flow.rework_count_limit：返工流程整体计数上限（rework_of_family 非空即返工流程时
--     生效；step.rework_limit 是单站口径，本列是整条返工流程允许循环次数，超限处置归 MES）。

ALTER TABLE cim_flow_qtime ADD COLUMN alarm_code VARCHAR(64);
ALTER TABLE cim_flow_step  ADD COLUMN npw_limit INT;
ALTER TABLE cim_flow       ADD COLUMN rework_count_limit INT;
