-- V27: WIP 原因事件域移出 MDS（归 MES 运行域）。
-- cim_wip_reason_event 是 V16 引入的「WIP 事务快照」示例表（事件创建/履历），
-- 属 MES 运行时事务而非主数据；原因码主数据（cim_reason_code*）保留。
DROP TABLE IF EXISTS cim_wip_reason_event;
