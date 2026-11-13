package com.cim.mds.server.site;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 位置主数据编辑锁 API（五层统一：site / fab / area / bay / stage）。
 *
 * <p>锁语义见 location-design.md §8.1：<b>「人 vs 人」编辑互斥</b>。前端动线：
 * 点「编辑」→ {@code PUT /locations/{type}/{id}/lock} 抢锁（失败弹「谁正在编辑」）；
 * 保存 → 业务 PUT（服务层同事务清锁）；取消/关闭弹窗 → {@code DELETE .../lock} 显式释放；
 * {@code force=true} 为管理员强制解锁（仍需该层 update 权限）。</p>
 *
 * <p>锁端点的功能权限复用各层的 {@code update} 权限码（编辑的一部分）；
 * 认证关闭（测试/开发）时 {@link #requireEditPerm} 直接放行。</p>
 */
@RestController
@RequestMapping("/api/v1/mds/locations")
@RequiredArgsConstructor
public class LocationLockController {

    private final EditLockSupport lockSupport;

    /** type → 该层 update 权限码（type 来自 URL，必须过白名单）。 */
    private static final Map<String, String> EDIT_PERM = Map.ofEntries(
            Map.entry("site", MdsPermissions.SITE_UPDATE),
            Map.entry("fab", MdsPermissions.FAB_UPDATE),
            Map.entry("area", MdsPermissions.AREA_UPDATE),
            Map.entry("areaGroup", MdsPermissions.AREA_UPDATE),
            Map.entry("bay", MdsPermissions.BAY_UPDATE),
            Map.entry("stage", MdsPermissions.STAGE_UPDATE),
            Map.entry("technology", MdsPermissions.TECH_UPDATE),
            Map.entry("product", MdsPermissions.PRODUCT_UPDATE),
            Map.entry("equipmentType", MdsPermissions.EQP_TYPE_UPDATE),
            Map.entry("equipment", MdsPermissions.EQUIPMENT_UPDATE),
            Map.entry("capability", MdsPermissions.CAPABILITY_UPDATE));

    /** GET /locations/{type}/{id}/lock —— 锁状态（前端渲染「被谁编辑中」徽标）。 */
    @GetMapping("/{type}/{id}/lock")
    public Result<EditLockSupport.LockState> lock(@PathVariable String type, @PathVariable String id) {
        requireType(type);
        return Result.ok(lockSupport.state(type, id));
    }

    /** PUT /locations/{type}/{id}/lock —— 抢锁（本人续锁 / 残留超时锁覆盖同一入口）。 */
    @PutMapping("/{type}/{id}/lock")
    public Result<EditLockSupport.ClaimResult> claim(@PathVariable String type, @PathVariable String id) {
        requireType(type);
        requireEditPerm(type);
        String user = EditLockSupport.currentUser();
        EditLockSupport.ClaimResult result = lockSupport.claim(type, id, user);
        if (!result.claimed()) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + result.occupiedBy() + " 编辑，暂不能修改");
        }
        return Result.ok(result);
    }

    /** DELETE /locations/{type}/{id}/lock?force= —— 释放锁（默认只清自己的；force 管理员强解）。 */
    @DeleteMapping("/{type}/{id}/lock")
    public Result<Void> release(@PathVariable String type, @PathVariable String id,
                                @RequestParam(required = false, defaultValue = "false") boolean force) {
        requireType(type);
        requireEditPerm(type);
        lockSupport.release(type, id, EditLockSupport.currentUser(), force);
        return Result.ok(null);
    }

    private static void requireType(String type) {
        if (!EditLockSupport.TYPES.contains(type)) {
            throw new BizException(BizCode.PARAM_INVALID, "未知位置层类型: " + type);
        }
    }

    /** 认证开启时按层校验 update 权限；认证关闭（测试/开发）放行。 */
    private static void requireEditPerm(String type) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return;
        }
        String need = EDIT_PERM.get(type);
        boolean granted = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(need));
        if (!granted) {
            throw new BizException(BizCode.ACCESS_DENIED, "无操作权限: " + need);
        }
    }
}
