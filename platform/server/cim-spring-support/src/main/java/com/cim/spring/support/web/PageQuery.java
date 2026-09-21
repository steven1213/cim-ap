package com.cim.spring.support.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准分页 / 排序入参（见 README §17 API 规范）。
 *
 * <p>页号从 <b>1</b> 开始；{@code sort} 形如 {@code "createTime,desc"}，可多字段。
 * 转换为 Spring Data {@link Pageable} 由框架统一处理，业务不手写方言分页。</p>
 */
@Getter
@Setter
public class PageQuery {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 500;

    /** 页号（从 1 开始）。 */
    private Integer page = DEFAULT_PAGE;

    /** 每页条数。 */
    private Integer size = DEFAULT_SIZE;

    /** 排序表达式，如 {@code "createTime,desc"}；多个用 {@code ;} 分隔。 */
    private String sort;

    /** 转换为 Spring Data {@link Pageable}（页号 1-based → 0-based，size 上限保护）。 */
    public Pageable toPageable() {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p - 1, s, parseSort(sort));
    }

    /** 解析排序串。 */
    public static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String part : sort.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            String[] kv = part.split(",");
            String property = kv[0].trim();
            if (property.isEmpty()) {
                continue;
            }
            boolean desc = kv.length > 1 && "desc".equalsIgnoreCase(kv[1].trim());
            orders.add(desc ? Sort.Order.desc(property) : Sort.Order.asc(property));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
