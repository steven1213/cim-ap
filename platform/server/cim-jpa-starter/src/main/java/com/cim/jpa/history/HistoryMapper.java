package com.cim.jpa.history;

import com.cim.core.history.HistoryStrategy;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 历史实体映射（反射）：按约定解析历史表实体类，并把主实体的业务字段**同构**拷贝过去
 * （见 README §9「每实体一专属历史表」）。
 *
 * <p>约定：历史实体与主实体**同包同名 + 后缀**——{@code SNAPSHOT} → {@code {X}Hist}，
 * {@code STATE_LOG} → {@code {X}StateLog}。这样业务实体继承 {@code BaseDefData} 并加
 * {@code @History} 即可自动获得历史能力，零样板代码。</p>
 */
@Slf4j
public final class HistoryMapper {

    private static final Map<Class<?>, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Field[]>> FIELD_CACHE = new ConcurrentHashMap<>();

    private HistoryMapper() {
    }

    /**
     * 解析历史实体类。
     *
     * @param entityClass 主实体类
     * @param strategy    历史策略
     * @return 历史实体类
     * @throws IllegalStateException 未找到约定的历史实体类
     */
    public static Class<?> resolveHistoryClass(Class<?> entityClass, HistoryStrategy strategy) {
        return CLASS_CACHE.computeIfAbsent(entityClass, key -> {
            String suffix = (strategy == HistoryStrategy.STATE_LOG) ? "StateLog" : "Hist";
            String name = key.getName() + suffix;
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("History class not found by convention: " + name
                        + " (expected for entity " + key.getName() + ", strategy " + strategy + ")");
            }
        });
    }

    /** 实例化历史实体（要求公共无参构造）。 */
    public static Object newHistoryInstance(Class<?> historyClass) {
        try {
            Constructor<?> constructor = historyClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate history class: " + historyClass.getName(), e);
        }
    }

    /**
     * 把主实体的业务字段同构拷贝到历史实体（同名即拷贝，缺失则跳过；{@code id} 不拷贝，
     * 由写入器映射为 {@code bizId}）。
     *
     * @return 实际拷贝的字段数
     */
    public static int copyBusinessFields(Object source, Object target) {
        List<Field[]> pairs = FIELD_CACHE.computeIfAbsent(
                source.getClass().getName() + "->" + target.getClass().getName(),
                key -> resolveFieldPairs(source.getClass(), target.getClass()));

        int copied = 0;
        for (Field[] pair : pairs) {
            Field from = pair[0];
            Field to = pair[1];
            try {
                from.setAccessible(true);
                to.setAccessible(true);
                to.set(target, from.get(source));
                copied++;
            } catch (Exception e) {
                log.debug("[cim-jpa] copy field {} failed: {}", from.getName(), e.getMessage());
            }
        }
        return copied;
    }

    private static List<Field[]> resolveFieldPairs(Class<?> sourceClass, Class<?> targetClass) {
        List<Field[]> pairs = new ArrayList<>();
        Class<?> current = sourceClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (isSkippable(field) || "id".equals(field.getName())) {
                    continue;
                }
                Field counterpart = findField(targetClass, field.getName());
                if (counterpart != null) {
                    pairs.add(new Field[]{field, counterpart});
                }
            }
            current = current.getSuperclass();
        }
        return Collections.unmodifiableList(pairs);
    }

    private static boolean isSkippable(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic();
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
