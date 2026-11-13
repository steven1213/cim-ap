package com.cim.system.dict;

import com.cim.spring.support.web.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典明细管理接口（挂在 {@code /sys/dicts/{dictId}/items} 下）。
 *
 * <p>与字典本体的 {@link SysDictController}（BaseController 通用 CRUD）不同，明细
 * 管理带字典归属校验与系统级守门，逻辑在 {@link SysDictItemService}，端点只做转发。</p>
 */
@RestController
@RequestMapping("/sys/dicts/{dictId}/items")
public class SysDictItemController {

    private final SysDictItemService itemService;

    public SysDictItemController(SysDictItemService itemService) {
        this.itemService = itemService;
    }

    /** 某字典的全量明细（含停用；管理页明细表用）。 */
    @GetMapping
    public Result<List<SysDictItem>> list(@PathVariable String dictId) {
        return Result.ok(itemService.listByDict(dictId));
    }

    /** 在指定字典下新增明细。 */
    @PostMapping
    public Result<SysDictItem> create(@PathVariable String dictId, @RequestBody SysDictItem item) {
        return Result.ok(itemService.createInDict(dictId, item));
    }

    /** 更新明细（校验归属）。 */
    @PutMapping("/{id}")
    public Result<SysDictItem> update(@PathVariable String dictId, @PathVariable String id,
                                      @RequestBody SysDictItem patch) {
        return Result.ok(itemService.updateInDict(dictId, id, patch));
    }

    /** 删除明细（系统级明细被守门 → 2004）。 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String dictId, @PathVariable String id) {
        itemService.removeInDict(dictId, id);
        return Result.ok();
    }
}
