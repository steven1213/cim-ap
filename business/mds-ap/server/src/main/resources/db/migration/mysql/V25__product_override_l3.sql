-- V25 (P3-1，2026-11-25)：产品级参数覆盖 L3（拍板项 C 落地；MySQL 方言）
-- 口径同 h2 版本：scope=PRODUCT 扩展 + cim_product_flow 列型覆盖 + uk 扩列。
ALTER TABLE cim_equip_assign ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_equip_assign ADD COLUMN family_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_constraint_attr ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_constraint_attr ADD COLUMN family_code VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE cim_product_flow ADD COLUMN reticle_ref VARCHAR(256);
ALTER TABLE cim_product_flow ADD COLUMN contam_level VARCHAR(16);
ALTER TABLE cim_product_flow ADD COLUMN contam_forbidden VARCHAR(128);
ALTER TABLE cim_product_flow ADD COLUMN npw_limit INT;
ALTER TABLE cim_equip_assign DROP INDEX uk_ea;
ALTER TABLE cim_equip_assign ADD UNIQUE INDEX uk_ea (scope_type, operation_code, flow_id, step_id, product_code, family_code, assign_type, assign_ref, priority, deleted);
ALTER TABLE cim_constraint_attr DROP INDEX uk_cattr;
ALTER TABLE cim_constraint_attr ADD UNIQUE INDEX uk_cattr (context_type, operation_code, flow_id, step_id, product_code, family_code, attr_key, deleted);
