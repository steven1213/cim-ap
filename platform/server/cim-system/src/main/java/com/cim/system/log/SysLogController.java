package com.cim.system.log;

import com.cim.core.shared.PageResult;
import com.cim.spring.support.web.PageQuery;
import com.cim.spring.support.web.Result;
import com.cim.system.support.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日志查询接口。
 *
 * <p>只读——日志由 {@code OperationLogService}/{@code LoginLogService} 在业务链路中写入，
 * 不提供 HTTP 写入端点，避免日志被外部伪造。</p>
 */
@RestController
@RequestMapping("/sys/logs")
public class SysLogController {

    private final OperationLogService operationLogService;
    private final LoginLogService loginLogService;

    public SysLogController(OperationLogService operationLogService, LoginLogService loginLogService) {
        this.operationLogService = operationLogService;
        this.loginLogService = loginLogService;
    }

    /** 分页查询操作日志。 */
    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public Result<PageResult<OperationLog>> operations(PageQuery query) {
        return Result.ok(operationLogService.page(query));
    }

    /** 分页查询登录日志。 */
    @GetMapping("/logins")
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public Result<PageResult<LoginLog>> logins(PageQuery query) {
        return Result.ok(loginLogService.page(query));
    }

    /** 某业务键的操作轨迹。 */
    @GetMapping("/operations/by-biz-key")
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public Result<List<OperationLog>> byBizKey(@RequestParam(name = "bizKey") String bizKey) {
        return Result.ok(operationLogService.byBizKey(bizKey));
    }
}
