package com.cim.jpa.history;

import com.cim.core.history.History;
import com.cim.core.shared.ChangeSet;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 字段级变更检测（见 README §9「变更检测闸门」）。
 *
 * <p>对比「变更前实例」（从库中加载的旧值）与「变更后实例」的字段值，仅当被追踪字段真正
 * 变化时才产生非空 {@link ChangeSet}，避免「未变更也落历史」的历史污染。追踪字段由
 * {@link History#include()} 白名单与 {@link History#exclude()} 黑名单控制。</p>
 */
@Slf4j
public final class ChangeDetector {

    /** 框架内部字段不参与变更检测。 */
    private static final Set<String> IGNORED = Set.of("id", "version");

    private ChangeDetector() {
    }

    /**
     * 计算两个实例之间的字段差异。
     *
     * @param history 实体上的 {@link History} 注解（不可为 {@code null}）
     * @param before  变更前实例（旧值）
     * @param after   变更后实例（新值）
     * @return 变更集（可能为空）
     */
    public static ChangeSet detect(History history, Object before, Object after) {
        ChangeSet changeSet = new ChangeSet();
        if (before == null || after == null) {
            return changeSet;
        }
        Set<String> includes = new HashSet<>(Arrays.asList(history.include()));
        Set<String> excludes = new HashSet<>(Arrays.asList(history.exclude()));

        Class<?> current = before.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (shouldSkip(field, includes, excludes)) {
                    continue;
                }
                Object oldValue = read(field, before);
                Object newValue = read(field, after);
                if (!Objects.equals(oldValue, newValue)) {
                    changeSet.record(field.getName(), oldValue, newValue);
                }
            }
            current = current.getSuperclass();
        }
        return changeSet;
    }

    private static boolean shouldSkip(Field field, Set<String> includes, Set<String> excludes) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
            return true;
        }
        String name = field.getName();
        if (IGNORED.contains(name) || excludes.contains(name)) {
            return true;
        }
        return !includes.isEmpty() && !includes.contains(name);
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            log.debug("[cim-jpa] read field {} failed: {}", field.getName(), e.getMessage());
            return null;
        }
    }
}
