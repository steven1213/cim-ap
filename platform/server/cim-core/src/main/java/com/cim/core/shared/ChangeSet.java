package com.cim.core.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 字段级变更集值对象（框架无关）。
 *
 * <p>由 {@code cim-jpa-starter} 的 {@code ChangeDetector} 在检测到实体字段真正变更时构造，
 * 仅记录被追踪字段的 before/after，供历史表 {@code change_set_json} 落库
 * （见 README §9「变更检测」）。</p>
 */
public final class ChangeSet {

    private final Map<String, Change> changes = new LinkedHashMap<>();

    /** 记录一次字段变更（同字段重复记录时以最后一次为准）。 */
    public void record(String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        changes.put(field, new Change(field, before, after));
    }

    /** 是否为空（无任何字段变更 → 不落历史）。 */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /** 变更数量。 */
    public int size() {
        return changes.size();
    }

    /** 只读变更视图（保持插入顺序）。 */
    public Map<String, Change> getChanges() {
        return Collections.unmodifiableMap(changes);
    }

    /**
     * 单个字段的变更明细。
     *
     * @param field  字段名
     * @param before 变更前值
     * @param after  变更后值
     */
    public record Change(String field, Object before, Object after) {
    }
}
