package com.cim.system.dict;

import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BaseController;
import com.cim.spring.support.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典管理接口（字典本体 CRUD + 全量列表 + 按编码取明细）。
 *
 * <p>权限模型：{@code sys:dict:list}（读）/ {@code sys:dict:save}（写）/
 * {@code sys:dict:remove}（删）；系统级字典（{@code is_system=true}）删除被守门。</p>
 */
@RestController
@RequestMapping("/sys/dicts")
public class SysDictController extends BaseController<SysDict, String> {

    private final SysDictService dictService;

    public SysDictController(SysDictService dictService) {
        this.dictService = dictService;
    }

    @Override
    protected CrudService<SysDict, String> service() {
        return dictService;
    }

    /** 全量字典列表（管理页主表用；字典量小不分页）。 */
    @GetMapping
    public Result<List<SysDict>> list() {
        return Result.ok(dictService.list());
    }

    /**
     * 按字典编码取启用明细（前端下拉直接用）。
     *
     * <p>不加鉴权：字典是<b>参照数据</b>，业务表单/列表都需要渲染，属公共读取；
     * 字典的维护（写）才受 {@code sys:dict:save} 保护。</p>
     */
    @GetMapping("/code/{code}/items")
    public Result<List<SysDictItem>> items(@PathVariable String code) {
        return Result.ok(dictService.items(code));
    }
}
