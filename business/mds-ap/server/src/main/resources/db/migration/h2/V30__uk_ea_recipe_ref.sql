-- V30 配方解算（第二版）：uk_ea 扩列 recipe_ref
--   * 同设备同优先级但配方口径（FIXED 的 recipeRef）不同的候选池行允许并存——
--     这是设计内的「歧义池」场景：validate WARN（RECIPE_POOL_AMBIGUOUS）+ 解算 AMBIGUOUS_POOL 硬拦，
--     不在入库层拦截。
--   * GENERATE/EXACT_MATCH 行 recipe_ref 为 NULL：H2/MySQL 唯一索引对 NULL 视为互异，
--     其判重仍由应用层 assertNotDuplicate（recipe_rule/recipe_ref/recipe_match 全等才算重复）兜底。
ALTER TABLE cim_equip_assign DROP CONSTRAINT uk_ea;
ALTER TABLE cim_equip_assign ADD CONSTRAINT uk_ea
    UNIQUE (scope_type, operation_code, flow_id, step_id, product_code, family_code,
            assign_type, assign_ref, priority, recipe_ref, deleted);
