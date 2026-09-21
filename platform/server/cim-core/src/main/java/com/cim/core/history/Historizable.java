package com.cim.core.history;

/**
 * 历史表标记接口。
 *
 * <p>所有实体历史表（{@code {X}Hist} / {@code {X}StateLog}，均继承
 * {@link com.cim.core.model.BaseHistoryData}）应同时实现本接口，
 * 便于框架以类型安全的方式识别与统一处理历史表。</p>
 */
public interface Historizable {
}
