package com.cim.iam.server.directory;

import java.time.Instant;
import java.util.List;

/** 目录只读 API 的返回体（identity-directory.md §5.1）。 */
public final class DirectoryDtos {

    private DirectoryDtos() {
    }

    /** 水位：业务侧比对决定是否重拉。 */
    public record WatermarkDto(long user, long org) {
    }

    /** 组织摘要（含用户归属）。 */
    public record OrgBriefDto(String orgId, String code, String name, String nodeType, boolean primary) {
    }

    /** 用户档案（供业务系统展示「张三（工号 1001 · 蚀刻工序）」）。 */
    public record UserProfileDto(
            String userId,
            String employeeNo,
            String displayName,
            String email,
            String mobile,
            String jobTitle,
            String status,
            String source,
            List<OrgBriefDto> orgs) {
    }

    /** 组织节点（{@code since} 增量拉取用 {@code updatedAt} 合并）。 */
    public record OrgNodeDto(
            String id,
            String parentId,
            String code,
            String name,
            String nodeType,
            String path,
            int sortNo,
            String source,
            String status,
            Instant updatedAt) {
    }

    /** 批量档案查询请求体。 */
    public record BatchUsersRequest(List<String> userIds) {
    }
}
