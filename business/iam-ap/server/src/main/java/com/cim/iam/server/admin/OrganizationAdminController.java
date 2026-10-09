package com.cim.iam.server.admin;

import com.cim.iam.server.directory.DirectoryDtos.OrgNodeDto;
import com.cim.iam.server.directory.DirectoryService;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.org.OrgNodeType;
import com.cim.iam.server.org.OrgStatus;
import com.cim.iam.server.profile.ProfileService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织架构管理（管理面，需 {@code iam-ap:ADMIN}，identity-directory.md §5.2）。
 *
 * <p>制造组织（厂区→车间→产线→工序→班组）在此维护；AD 同步的行政组织为只读，
 * 任何本地修改会被 {@link OrgNodeService} 的跨源保护拒绝。
 * 组织变更（含移动）会 bump 受影响用户令牌版本 + ORG 水位。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('iam-ap:ADMIN')")
public class OrganizationAdminController {

    private final OrgNodeService orgNodeService;
    private final ProfileService profileService;
    private final DirectoryService directoryService;

    public record CreateOrgRequest(String parentId, String code, String name, OrgNodeType nodeType, int sortNo) {
    }

    public record UpdateOrgRequest(String name, Integer sortNo, OrgStatus status) {
    }

    public record MoveOrgRequest(String newParentId) {
    }

    public record UserOrgRef(String orgId, boolean primary) {
    }

    public record SetUserOrgsRequest(List<UserOrgRef> orgs) {
    }

    /** 全部组织节点（扁平，含 parentId 供前端建树）。 */
    @GetMapping("/orgs")
    public Result<List<OrgNodeDto>> list() {
        return Result.ok(directoryService.orgs(null));
    }

    /** 新建 IAM 自建组织节点。 */
    @PostMapping("/orgs")
    public Result<OrgNodeDto> create(@RequestBody CreateOrgRequest req) {
        OrgNode n = orgNodeService.createManaged(req.parentId(), req.code(), req.name(),
                req.nodeType(), req.sortNo());
        return Result.ok(new OrgNodeDto(n.getId(), n.getParentId(), n.getCode(), n.getName(),
                n.getNodeType().name(), n.getPath(), n.getSortNo(),
                n.getSource().name(), n.getStatus().name(), n.getUpdatedAt()));
    }

    /** 更新名称 / 排序 / 状态（仅 IAM 自建节点）。 */
    @PutMapping("/orgs/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody UpdateOrgRequest req) {
        orgNodeService.update(id, req.name(), req.sortNo(), req.status());
        return Result.ok();
    }

    /** 移动节点（改父，含子树路径重写）。 */
    @PutMapping("/orgs/{id}/move")
    public Result<Void> move(@PathVariable String id, @RequestBody MoveOrgRequest req) {
        orgNodeService.move(id, req.newParentId());
        return Result.ok();
    }

    /** 删除节点（须无子节点 / 无归属 / 无组织授予）。 */
    @DeleteMapping("/orgs/{id}")
    public Result<Void> delete(@PathVariable String id) {
        orgNodeService.delete(id);
        return Result.ok();
    }

    /** 组织内人员 ID（含子组织）。 */
    @GetMapping("/orgs/{id}/users")
    public Result<List<String>> users(@PathVariable String id) {
        return Result.ok(orgNodeService.userIdsInOrg(id, true));
    }

    /** 设置用户组织归属（替换式，支持多归属）。 */
    @PutMapping("/users/{userId}/orgs")
    public Result<Void> setUserOrgs(@PathVariable String userId, @RequestBody SetUserOrgsRequest req) {
        List<ProfileService.OrgRef> refs = req.orgs() == null ? List.of()
                : req.orgs().stream().map(r -> new ProfileService.OrgRef(r.orgId(), r.primary())).toList();
        profileService.setUserOrgs(userId, refs);
        return Result.ok();
    }

    /** 用户当前组织归属。 */
    @GetMapping("/users/{userId}/orgs")
    public Result<List<UserOrgRef>> userOrgs(@PathVariable String userId) {
        return Result.ok(profileService.orgsOfUser(userId).stream()
                .map(uo -> new UserOrgRef(uo.getOrgId(), uo.isPrimary()))
                .toList());
    }
}
