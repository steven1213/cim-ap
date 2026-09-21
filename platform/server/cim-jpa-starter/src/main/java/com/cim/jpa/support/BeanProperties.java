package com.cim.jpa.support;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 轻量反射属性访问工具（框架内部使用）。
 *
 * <p>用于在生命周期回调中对**基类族**统一注入主键（{@code id} / {@code historyId}），
 * 避免为每种基类重复分支。字段沿类层次向上查找，找不到时静默跳过。</p>
 */
public final class BeanProperties {

    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Field ABSENT = sentinel();

    private BeanProperties() {
    }

    /** 读取属性值；属性不存在或访问失败返回 {@code null}。 */
    public static Object get(Object target, String property) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), property);
        if (field == null || field == ABSENT) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** 若属性为 {@code null}，则用 {@code supplier} 赋值；属性不存在或已有值时不做处理。 */
    public static void setIfNull(Object target, String property, Supplier<Object> supplier) {
        Field field = findField(target.getClass(), property);
        if (field == null || field == ABSENT) {
            return;
        }
        try {
            field.setAccessible(true);
            if (field.get(target) == null) {
                field.set(target, supplier.get());
            }
        } catch (IllegalAccessException ignored) {
            // 访问失败不阻断主流程
        }
    }

    private static Field findField(Class<?> type, String name) {
        String key = type.getName() + '#' + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached == ABSENT ? null : cached;
        }
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        FIELD_CACHE.put(key, ABSENT);
        return null;
    }

    private static Field sentinel() {
        try {
            return BeanProperties.class.getDeclaredField("FIELD_CACHE");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
