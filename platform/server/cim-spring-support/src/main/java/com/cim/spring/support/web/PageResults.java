package com.cim.spring.support.web;

import com.cim.core.shared.PageResult;
import org.springframework.data.domain.Page;

import java.util.function.Function;

/**
 * Spring Data {@link Page} → {@link PageResult}（内核值对象）转换工具。
 *
 * <p>业务与领域层只依赖 {@link PageResult}，Spring Data 类型不越出适配层
 * （见 design.md §1.1 依赖规则）。</p>
 */
public final class PageResults {

    private PageResults() {
    }

    /** 直接转换（元素类型不变）。 */
    public static <T> PageResult<T> from(Page<T> page) {
        return PageResult.of(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }

    /** 带元素映射的转换（Entity → VO）。 */
    public static <S, T> PageResult<T> from(Page<S> page, Function<S, T> mapper) {
        return PageResult.of(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }
}
