package com.cim.mds.server.shift;

import java.util.List;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 换班申请 API（V19）——{@code /api/v1/mds/shift-swaps}。
 *
 * <p>权限口径（方案定稿）：换班是员工自助动作，与交接记录同理——
 * {@code mds:shiftswap:view/save} 独立成组、值班人员全员可写，与排班管理员
 * {@code mds:shift:*} 分开。确认/拒绝/取消动作同 save 码（发起人、对方、班长
 * 都是「参与者」，本期不做参与者身份强校验，与交接记录签收同口径）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ShiftSwapController {

    private final ShiftSwapService swapService;
    private final CurrentUser currentUser;

    /** 申请清单（status 过滤可选；create_time 倒序）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTSWAP_VIEW + "')")
    @GetMapping("/shift-swaps")
    public Result<List<ShiftSwapService.SwapView>> list(
            @RequestParam(required = false) String status) {
        return Result.ok(swapService.list(status));
    }

    /** 发起换班/代班申请（落 PENDING）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTSWAP_SAVE + "')")
    @PostMapping("/shift-swaps")
    public Result<ShiftSwapService.SwapView> apply(
            @RequestBody ShiftSwapService.ApplyReq req) {
        return Result.ok(swapService.apply(req, currentUser.id(), currentUser.name()));
    }

    /** 对方确认（本期确认即生效，排班同事务落库）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTSWAP_SAVE + "')")
    @PostMapping("/shift-swaps/{id}/confirm")
    public Result<ShiftSwapService.SwapView> confirm(
            @PathVariable String id,
            @RequestParam(required = false) String note) {
        return Result.ok(swapService.confirm(id, note, currentUser.id(), currentUser.name()));
    }

    /** 对方拒绝（PENDING → REJECTED）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTSWAP_SAVE + "')")
    @PostMapping("/shift-swaps/{id}/reject")
    public Result<ShiftSwapService.SwapView> reject(
            @PathVariable String id,
            @RequestParam(required = false) String reason) {
        return Result.ok(swapService.reject(id, reason));
    }

    /** 发起人取消（PENDING → CANCELLED）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTSWAP_SAVE + "')")
    @PostMapping("/shift-swaps/{id}/cancel")
    public Result<ShiftSwapService.SwapView> cancel(@PathVariable String id) {
        return Result.ok(swapService.cancel(id));
    }
}
