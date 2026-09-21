package com.cim.spring.support.service;

import com.cim.core.shared.PageResult;
import com.cim.spring.support.web.PageQuery;

/**
 * 通用 CRUD 服务契约（见 README §21.3）。
 *
 * <p>面向接口而非具体持久化技术，使 {@link com.cim.spring.support.web.BaseController}
 * 不依赖 JPA。JPA 侧实现见 {@code cim-jpa-starter} 的 {@code AbstractJpaService}。</p>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 */
public interface CrudService<T, ID> {

    /** 分页查询。 */
    PageResult<T> page(PageQuery query);

    /** 按主键加载（不存在抛业务异常）。 */
    T load(ID id);

    /** 新增（写操作自动落历史，见 README §9）。 */
    T save(T entity);

    /** 更新（写操作自动落历史）。 */
    T update(T entity);

    /** 删除（逻辑删除）。 */
    void remove(ID id);
}
