-- MDS V21：Step/Operation 挂 Capability + EDC Plan（2026-11-18 与用户定稿，AMAT 口径）
-- 行业对标：Applied Materials 实现——Step 上挂 Capability（能力需求）与 EDC Plan
-- （工程数据采集计划引用），Capability 与设备能力（cim_equipment_capability，V7 已建）
-- 关联，从而通过 Step 挂的能力码找到可用于加工的设备。
-- 定稿三决策（AskUserQuestion）：
--   1. Capability 分层 = O 层默认 + S 层覆写：cim_operation.capability_code 为工序
--      默认能力，cim_flow_step.capability_code 为路线内覆写（可空 = 继承 O 层）；
--      两层并存 AND：能力匹配是主筛，equip_type 保留为硬约束。
--   2. EDC Plan 本期仅引用码：edc_plan_code 可空字符串，不做主数据表/管理页
--      （采数规则属 MES/EDA 域，MDS 只留引用缝），同样 O 默认 + S 覆写。
--   3. 能力匹配粒度 = Equipment 级：复用 V7 cim_equipment_capability
--      （equipment_id ↔ capability_code），本期不建 Unit 级关联。
-- 守门：capability_code 非空才校验存在（与 techCode/equipType 同款「非空才校验」，
-- 通用工序可留空表示跨能力复用）。

-- ---------------------------------------------------------------- O 层默认
ALTER TABLE cim_operation ADD COLUMN capability_code VARCHAR(64);
ALTER TABLE cim_operation ADD COLUMN edc_plan_code VARCHAR(64);
CREATE INDEX idx_cim_operation_capability ON cim_operation (capability_code);

-- ---------------------------------------------------------------- S 层覆写
ALTER TABLE cim_flow_step ADD COLUMN capability_code VARCHAR(64);
ALTER TABLE cim_flow_step ADD COLUMN edc_plan_code VARCHAR(64);
CREATE INDEX idx_cim_flow_step_capability ON cim_flow_step (capability_code);
