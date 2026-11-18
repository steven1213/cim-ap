package com.cim.mds.server.shift;

import java.util.List;

import com.cim.mds.server.admin.DirectoryClient;
import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 班组排班域 API（V17）——定义 {@code /api/v1/mds/shifts|shift-patterns} +
 * 排班 {@code /api/v1/mds/shift-assignments} + 交接记录 {@code /api/v1/mds/shift-logs}。
 *
 * <p>权限口径（方案定稿）：班次/班制/排班共用 {@code mds:shift:*} 一组码
 * （维护人是同一批管理员）；交接记录独立 {@code mds:shiftlog:view/save}——
 * 值班人员全员可写，与排班管理员不是同一批人。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftAdminService adminService;
    private final ShiftAssignmentService assignmentService;
    private final ShiftLogService logService;
    private final DirectoryClient directory;
    private final CurrentUser currentUser;

    // ---------------------------------------------------------------- 班次

    /** 班次全量（sort_no 升序）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/shifts")
    public Result<List<ShiftAdminService.ShiftView>> listShifts() {
        return Result.ok(adminService.listShifts());
    }

    /** 启用班次（排班/铺排下拉口径）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/shifts/enabled")
    public Result<List<ShiftAdminService.ShiftView>> listEnabledShifts() {
        return Result.ok(adminService.listEnabledShifts());
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_CREATE + "')")
    @PostMapping("/shifts")
    public Result<ShiftAdminService.ShiftView> createShift(
            @RequestBody ShiftAdminService.ShiftReq req) {
        return Result.ok(adminService.createShift(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_UPDATE + "')")
    @PutMapping("/shifts/{id}")
    public Result<ShiftAdminService.ShiftView> updateShift(
            @PathVariable String id, @RequestBody ShiftAdminService.ShiftReq req) {
        return Result.ok(adminService.updateShift(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_DELETE + "')")
    @DeleteMapping("/shifts/{id}")
    public Result<Void> deleteShift(@PathVariable String id) {
        adminService.deleteShift(id);
        return Result.ok(null);
    }

    // ---------------------------------------------------------------- 班制

    /** 班制全量。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/shift-patterns")
    public Result<List<ShiftAdminService.PatternView>> listPatterns() {
        return Result.ok(adminService.listPatterns());
    }

    /** 启用班制（铺排入口下拉口径）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/shift-patterns/enabled")
    public Result<List<ShiftAdminService.PatternView>> listEnabledPatterns() {
        return Result.ok(adminService.listEnabledPatterns());
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_CREATE + "')")
    @PostMapping("/shift-patterns")
    public Result<ShiftAdminService.PatternView> createPattern(
            @RequestBody ShiftAdminService.PatternReq req) {
        return Result.ok(adminService.createPattern(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_UPDATE + "')")
    @PutMapping("/shift-patterns/{id}")
    public Result<ShiftAdminService.PatternView> updatePattern(
            @PathVariable String id, @RequestBody ShiftAdminService.PatternReq req) {
        return Result.ok(adminService.updatePattern(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_DELETE + "')")
    @DeleteMapping("/shift-patterns/{id}")
    public Result<Void> deletePattern(@PathVariable String id) {
        adminService.deletePattern(id);
        return Result.ok(null);
    }

    // ---------------------------------------------------------------- 排班

    /** 周区间排班（from = 周一）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/shift-assignments")
    public Result<List<ShiftAssignmentService.AssignmentView>> weekAssignments(
            @RequestParam String from) {
        return Result.ok(assignmentService.week(from));
    }

    /** 单格保存（shiftCode 空 = 清空该格）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_UPDATE + "')")
    @PutMapping("/shift-assignments")
    public Result<ShiftAssignmentService.AssignmentView> saveCell(
            @RequestBody ShiftAssignmentService.CellReq req) {
        return Result.ok(assignmentService.saveCell(req, currentUser.name()));
    }

    /** 班制铺排（一步落库；跳过 MANUAL 格）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_UPDATE + "')")
    @PostMapping("/shift-assignments/generate")
    public Result<Integer> generate(@RequestBody ShiftAssignmentService.GenerateReq req) {
        return Result.ok(assignmentService.generate(req, currentUser.name()));
    }

    /** 复制上周（跳过 MANUAL 格）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_UPDATE + "')")
    @PostMapping("/shift-assignments/copy")
    public Result<Integer> copyWeek(@RequestBody ShiftAssignmentService.CopyReq req) {
        return Result.ok(assignmentService.copyWeek(req));
    }

    /** 选人下拉（IAM 目录启用档案；入口码 mds:shift:view——排班页不要求人员管理权限）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/shift-users")
    public Result<List<ShiftAssignmentService.UserOption>> userOptions() {
        return Result.ok(assignmentService.userOptions(directory.list("/api/v1/directory/profiles", java.util.Map.of())));
    }

    // ---------------------------------------------------------------- 交接班记录

    /** 交接记录清单（shiftCode 过滤，work_date 倒序）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTLOG_VIEW + "')")
    @GetMapping("/shift-logs")
    public Result<List<ShiftLogService.LogView>> listLogs(
            @RequestParam(required = false) String shiftCode) {
        return Result.ok(logService.list(shiftCode));
    }

    /** 交接记录详情。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTLOG_VIEW + "')")
    @GetMapping("/shift-logs/{id}")
    public Result<ShiftLogService.LogView> getLog(@PathVariable String id) {
        return Result.ok(logService.get(id));
    }

    /** 交班提交 / 修改（仅未签收可改；workDate 空按当前时刻推算归属班次日）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTLOG_SAVE + "')")
    @PostMapping("/shift-logs")
    public Result<ShiftLogService.LogView> saveLog(@RequestBody ShiftLogService.LogReq req) {
        return Result.ok(logService.save(req, currentUser.id(), currentUser.name()));
    }

    /** 接班签收（乐观锁；签收后锁定只读）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFTLOG_SAVE + "')")
    @PostMapping("/shift-logs/{id}/ack")
    public Result<ShiftLogService.LogView> ackLog(
            @PathVariable String id,
            @RequestParam(required = false) Long version) {
        return Result.ok(logService.ack(id, version, currentUser.id(), currentUser.name()));
    }
}
