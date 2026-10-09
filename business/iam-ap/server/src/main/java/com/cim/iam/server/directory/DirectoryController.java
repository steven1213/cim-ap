package com.cim.iam.server.directory;

import com.cim.iam.server.directory.DirectoryDtos.BatchUsersRequest;
import com.cim.iam.server.directory.DirectoryDtos.OrgNodeDto;
import com.cim.iam.server.directory.DirectoryDtos.UserProfileDto;
import com.cim.iam.server.directory.DirectoryDtos.WatermarkDto;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 目录只读 API（供业务 ap 消费，identity-directory.md §5.1）。
 *
 * <p><b>认证</b>：不走 JWT，由 {@link DirectoryApiKeyFilter} 校验请求头 {@code X-Directory-Key}
 * （方案 A；后续可平滑替换为 OAuth2 client_credentials 服务令牌，接口不变）。
 * 返回体不含任何凭证与权限内部结构。</p>
 */
@RestController
@RequestMapping("/api/v1/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService service;

    /** 水位：业务侧比对决定是否重拉（与 tokenVersion 同构的轻量比对）。 */
    @GetMapping("/watermark")
    public Result<WatermarkDto> watermark() {
        return Result.ok(service.watermark());
    }

    /** 单用户档案（含组织归属）。 */
    @GetMapping("/users/{userId}")
    public Result<UserProfileDto> user(@PathVariable String userId) {
        return Result.ok(service.user(userId)
                .orElseThrow(() -> BizException.notFound("用户档案不存在: " + userId)));
    }

    /** 批量用户档案（列表页渲染用，避免 N+1）。 */
    @PostMapping("/users/batch")
    public Result<List<UserProfileDto>> users(@RequestBody BatchUsersRequest req) {
        return Result.ok(service.users(req.userIds()));
    }

    /** 组织节点（{@code since} 非空时增量）。 */
    @GetMapping("/orgs")
    public Result<List<OrgNodeDto>> orgs(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return Result.ok(service.orgs(since));
    }
}
