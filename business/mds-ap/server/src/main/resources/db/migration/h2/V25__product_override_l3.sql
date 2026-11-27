-- V25 (P3-1，2026-11-25)：产品级参数覆盖 L3（拍板项 C 落地）
-- 口径（方案 A，2026-11-25 用户确认）：ContextChain 升级 PRODUCT > STEP > FLOW > OPERATION，
-- 但 validate 保持「流程主数据闸」不带产品轴；产品差异经按产品解析端点（GET /flows/{id}/effective?product=CODE）
-- 供 P06 预览与 MES 派工消费。
--   * 指派/KV 两张表加 scope=PRODUCT（product_code+family_code 定位，step_id 可空=整条流程生效）；
--   * 列型覆盖（V22 reticle/contam + V23 npw）落 cim_product_flow 绑定行（NULL=继承）；
--   * uk 扩列：product_code/family_code 并入 uk_ea/uk_cattr（非 PRODUCT 行为空串占位，语义不变）。
ALTER TABLE cim_equip_assign ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_equip_assign ADD COLUMN family_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_constraint_attr ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_constraint_attr ADD COLUMN family_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_product_flow ADD COLUMN reticle_ref VARCHAR(256);
ALTER TABLE cim_product_flow ADD COLUMN contam_level VARCHAR(16);
ALTER TABLE cim_product_flow ADD COLUMN contam_forbidden VARCHAR(128);
ALTER TABLE cim_product_flow ADD COLUMN npw_limit INT;
ALTER TABLE cim_equip_assign DROP CONSTRAINT uk_ea;
ALTER TABLE cim_equip_assign ADD CONSTRAINT uk_ea UNIQUE (scope_type, operation_code, flow_id, step_id, product_code, family_code, assign_type, assign_ref, priority, deleted);
ALTER TABLE cim_constraint_attr DROP CONSTRAINT uk_cattr;
ALTER TABLE cim_constraint_attr ADD CONSTRAINT uk_cattr UNIQUE (context_type, operation_code, flow_id, step_id, product_code, family_code, attr_key, deleted);
