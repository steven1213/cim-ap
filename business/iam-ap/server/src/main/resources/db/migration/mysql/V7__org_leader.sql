-- V7: 组织节点负责人（Hold 处置链路：部门 Hold 最终由部门负责人处理）。
-- 负责人是组织属性（归属关系 ≠ 负责关系），可空 = 该组织暂未指定负责人；
-- 语义为「任务路由目标」而非权限——处理 Hold 的实际权限仍由角色/准入控制。
ALTER TABLE org_node ADD COLUMN leader_user_id VARCHAR(128);
