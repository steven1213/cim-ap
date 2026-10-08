package com.cim.system.dict;

import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BaseController;
import com.cim.spring.support.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 字典管理接口（字典本体 CRUD + 按编码取明细）。 */
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
