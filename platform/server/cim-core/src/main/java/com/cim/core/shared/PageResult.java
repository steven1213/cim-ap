package com.cim.core.shared;

import java.util.List;

/**
 * 分页结果值对象（框架无关，不含 Spring Data 依赖）。
 *
 * <p>{@code cim-spring-support} 负责在 {@code org.springframework.data.domain.Page}
 * 与本类型之间转换，业务与领域层只依赖本类型，避免 Spring Data 泄漏进内核。</p>
 *
 * @param <T> 记录类型
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }

    /** 空结果。 */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }

    /** 总页数（size<=0 时返回 0）。 */
    public int totalPages() {
        return size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
