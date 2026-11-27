-- =====================================================================
-- V35 多尺寸厂支持：晶圆尺寸维度贯穿主数据链 + 第二批字段缺口（2026-11-27 审计）
-- 口径：系统需同时支持 6/8/12 英寸厂（Auto3 6 寸 + 未来 8/12 寸扩展）。
--   * 尺寸统一用英寸数字字符串（"6"/"8"/"12"，字典 mds.wafer_size），
--     与设备侧 wafer_sizes 逗号串同词表。
--   * product.wafer_type 字段语义实为衬底材料，统一改名 substrate_type（不做兼容）。
-- =====================================================================

-- 1) 产品：衬底材料改名 + 适用尺寸 + 裸片尺寸（good-die 统计前置）
ALTER TABLE cim_product RENAME COLUMN wafer_type TO substrate_type;
ALTER TABLE cim_product ADD COLUMN wafer_size VARCHAR(16);
ALTER TABLE cim_product ADD COLUMN die_size_x DECIMAL(8,3);
ALTER TABLE cim_product ADD COLUMN die_size_y DECIMAL(8,3);

-- 2) 载具：适用尺寸 + 额定槽数（E87 口径：FOUP=25 / 花篮=13/25）
ALTER TABLE cim_carrier ADD COLUMN wafer_size VARCHAR(16);
ALTER TABLE cim_carrier ADD COLUMN capacity INT;

-- 3) 光罩：适用晶圆尺寸 / 光罩版尺寸（5/6/9 英寸版，字典 mds.reticle_plate_size）/ 制程归属
ALTER TABLE cim_reticle ADD COLUMN wafer_size VARCHAR(16);
ALTER TABLE cim_reticle ADD COLUMN plate_size VARCHAR(16);
ALTER TABLE cim_reticle ADD COLUMN tech_code VARCHAR(64);

-- 4) 设备：归属部门（字典 mds.owning_dept 接线，此前只挂在 reason_code）
ALTER TABLE cim_equipment ADD COLUMN owning_dept VARCHAR(32);

-- 5) 设备↔能力：混线资格按尺寸区分（同机 6 寸合格 8 寸不合格的场景）；
--    UK 扩列允许「同能力 × 多尺寸」多条资格（NULL 尺寸 = 全尺寸通用）
ALTER TABLE cim_equipment_capability ADD COLUMN wafer_size VARCHAR(16);
ALTER TABLE cim_equipment_capability DROP INDEX uk_cim_eqp_cap;
ALTER TABLE cim_equipment_capability ADD UNIQUE INDEX uk_cim_eqp_cap (equipment_id, capability_code, wafer_size, deleted);

-- 6) 机台组成员：派工优先级（候选池权重，空 = 无偏好）
ALTER TABLE cim_tool_group_member ADD COLUMN priority INT;

-- 7) 耗材：供应商 / 料号（采购对齐）/ 有效期（化学品/胶水效期，与寿命三轨正交）
ALTER TABLE cim_consumable ADD COLUMN vendor VARCHAR(128);
ALTER TABLE cim_consumable ADD COLUMN part_no VARCHAR(64);
ALTER TABLE cim_consumable ADD COLUMN expiry_date DATE;
