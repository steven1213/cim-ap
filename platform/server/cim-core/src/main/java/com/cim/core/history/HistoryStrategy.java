package com.cim.core.history;

/**
 * 历史记录策略（见 README §9「历史策略注解」）。
 */
public enum HistoryStrategy {

    /** 整行快照写入同构历史表 {@code {X}Hist}（适用 {@code BaseDefData} 子类，定义/低频）。 */
    SNAPSHOT,

    /** 仅记状态变迁，落 {@code {X}StateLog}（适用 {@code BaseStateData} 子类，状态/高频）。 */
    STATE_LOG,

    /** 不记录（事件类 / 临时表）。 */
    NONE
}
