-- MDS V37：消耗品规格升级（2026-11-28，消耗品规格+BOM 建模 P1）
-- 定位重述：cim_consumable 从「耗材台账」升级为「带过程控制约束的物料静态档案」。
--   MDS 边界 = 定义「是什么」与「按什么标准消耗」；库存扣减/采购/上料阻断归 MES/ERP。
--   * material_kind：消耗品/耐用品边界标记——FOUP/Reticle 有独立域不收进来，
--     DURABLE 行不得被消耗品 BOM 引用（守门在 ConsBomAdminService）。
--   * shelf_life_days / ldu_hours：保质期天数 + 开封后使用时限（Pot Life），
--     到期判定与阻断归运行时，规格层只存基准。
--   * storage_temp / thaw_time：存储温度要求（文本区间如 2-8°C）+ 回温时间（小时），
--     上料前条件校验的基准。
--   * tolerance_pct / density_factor：称量配比容差（±%）+ 密度/效能因子
--     （体积↔重量换算与配方修正）。
--   * substitute_group：替代组码——同组规格可互相替代，BOM 行按组引用；
--     替代选择/优先消耗策略（FIFO/FEFO）在 BOM 行与运行时。
--   * lifecycle：ACTIVE / END_OF_SALE / OBSOLETE——OBSOLETE 不得被新 BOM 引用（硬守门），
--     与 is_enabled 正交（enabled 是启停开关，lifecycle 是资产生命周期）。
--   * lot_control：批次追溯标记（关键化学品/靶材必开）；实例化批次跟踪归 MES。
--   * 存量列不动：寿命三轨/水位/归属设备定位为「PART 类部件寿命基准与用量快照」；
--     expiry_date 是批次效期快照，与 shelf_life_days（规格属性）正交。

ALTER TABLE cim_consumable ADD COLUMN material_kind   VARCHAR(16)   NOT NULL DEFAULT 'CONSUMABLE';
ALTER TABLE cim_consumable ADD COLUMN lifecycle       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE cim_consumable ADD COLUMN shelf_life_days INT           NULL;
ALTER TABLE cim_consumable ADD COLUMN ldu_hours       INT           NULL;
ALTER TABLE cim_consumable ADD COLUMN storage_temp    VARCHAR(32)   NULL;
ALTER TABLE cim_consumable ADD COLUMN thaw_time       INT           NULL;
ALTER TABLE cim_consumable ADD COLUMN tolerance_pct   NUMERIC(5,2)  NULL;
ALTER TABLE cim_consumable ADD COLUMN density_factor  NUMERIC(12,6) NULL;
ALTER TABLE cim_consumable ADD COLUMN substitute_group VARCHAR(64)  NULL;
ALTER TABLE cim_consumable ADD COLUMN lot_control     BOOLEAN       NOT NULL DEFAULT FALSE;

-- 类别词表口径（TOOL 移除——耐用工具归 equipment 域；本迁移不动字典表，
-- 词表由 MdsDictSeedService 维护）：GAS/CHEMICAL/SLURRY/TARGET/PART/PACKAGING/OTHER
-- 注意：sys_dict_item 是平台 cim-system 域的表，不在 MDS 迁移 schema 内，
-- 迁移里不能碰它——TOOL 存量项由 MdsDictSeedService.seed() 幂等清除。
