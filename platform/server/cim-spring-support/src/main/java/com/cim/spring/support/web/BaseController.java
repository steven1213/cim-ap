package com.cim.spring.support.web;

import com.cim.core.shared.PageResult;
import com.cim.spring.support.service.CrudService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通用 CRUD 控制器基类（见 README §21.3）：零重复代码获得分页、增删改查。
 *
 * <p>子类只需声明 {@code @RequestMapping} 前缀、注入服务并实现 {@link #service()}；
 * 审计 / 幂等 / 权限由注解叠加（见 README §17 / §24）。</p>
 *
 * <p>DTO 与 Entity 严格分离：正式的 {@code Create/Update/Query/VO} 由子类按需覆盖方法
 * （见 README §2）；本基类给出可直接使用的默认契约。</p>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 */
public abstract class BaseController<T, ID> {

    /** 子类提供服务实现。 */
    protected abstract CrudService<T, ID> service();

    /** 分页查询。 */
    @GetMapping("/page")
    public Result<PageResult<T>> page(PageQuery query) {
        return Result.ok(service().page(query));
    }

    /** 按主键查询。 */
    @GetMapping("/{id}")
    public Result<T> detail(@PathVariable ID id) {
        return Result.ok(service().load(id));
    }

    /** 新增。 */
    @PostMapping
    public Result<T> create(@RequestBody T entity) {
        return Result.ok(service().save(entity));
    }

    /** 更新。 */
    @PutMapping
    public Result<T> update(@RequestBody T entity) {
        return Result.ok(service().update(entity));
    }

    /** 删除（逻辑删除）。 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable ID id) {
        service().remove(id);
        return Result.ok();
    }
}
