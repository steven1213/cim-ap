-- MDS V26：产品用途类型定稿（2026-11-25 行业调研 + 用户拍板）
--   * product_type（PROD/NPW 两值）升级为 usage_type（5 值，锚 SEMI E10/E79
--     产品片 vs 非产品片口径）：PRODUCTION=量产 / ENGINEERING=工程 /
--     EXPERIMENT=实验 / TEST=测试验证 / NPW=非产品陪跑。
--   * 新增 npw_type：NPW 二级细分（MONITOR 控挡 / DUMMY 填充 / SEASON 暖机 /
--     LITHO 光刻域辅材〔M38 Lithography，对应工厂侧 Develop/UV Mask〕/ GENERIC 兜底），
--     仅 usage_type=NPW 时必填（服务层守门），其余用途必须为空。
--   * wafer_type 保持不变（衬底形态，与 SEMI 材料等级是两个正交属性）。

ALTER TABLE cim_product RENAME COLUMN product_type TO usage_type;
UPDATE cim_product SET usage_type = 'PRODUCTION' WHERE usage_type = 'PROD';
ALTER TABLE cim_product ALTER COLUMN usage_type SET DEFAULT 'PRODUCTION';
ALTER TABLE cim_product ADD COLUMN npw_type VARCHAR(16);
-- 存量 NPW 行回填兜底细分（旧枚举无细分语义）
UPDATE cim_product SET npw_type = 'GENERIC' WHERE usage_type = 'NPW' AND npw_type IS NULL;
