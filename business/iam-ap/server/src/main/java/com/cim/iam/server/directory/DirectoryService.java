package com.cim.iam.server.directory;

import com.cim.iam.server.directory.DirectoryDtos.OrgBriefDto;
import com.cim.iam.server.directory.DirectoryDtos.OrgNodeDto;
import com.cim.iam.server.directory.DirectoryDtos.UserProfileDto;
import com.cim.iam.server.directory.DirectoryDtos.WatermarkDto;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeRepository;
import com.cim.iam.server.org.UserOrg;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
import com.cim.iam.server.watermark.DirectoryWatermarkService;
import com.cim.iam.server.watermark.WatermarkScope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 目录只读服务（identity-directory.md §5.1）。
 *
 * <p>对业务 ap 提供档案与组织查询：<b>业务侧只存 uid 引用 + 可失效重建的缓存</b>，
 * 不落用户主数据。水位用于「轻量比对 → 变了才全量拉」。</p>
 */
@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final ProfileService profileService;
    private final OrgNodeRepository orgNodeRepository;
    private final DirectoryWatermarkService watermarkService;

    public WatermarkDto watermark() {
        return new WatermarkDto(
                watermarkService.current(WatermarkScope.USER),
                watermarkService.current(WatermarkScope.ORG));
    }

    public Optional<UserProfileDto> user(String userId) {
        return profileService.findByUserId(userId).map(this::toDto);
    }

    public List<UserProfileDto> users(List<String> userIds) {
        return profileService.findAllByIds(userIds).stream().map(this::toDto).toList();
    }

    /** 组织节点（{@code since} 非空时仅返回其后变更的节点，供业务侧增量合并）。 */
    public List<OrgNodeDto> orgs(Instant since) {
        List<OrgNode> nodes = orgNodeRepository.findAll();
        return nodes.stream()
                .filter(n -> since == null || (n.getUpdatedAt() != null && n.getUpdatedAt().isAfter(since)))
                .map(this::toDto)
                .toList();
    }

    private UserProfileDto toDto(UserProfile p) {
        List<OrgBriefDto> orgs = new ArrayList<>();
        for (UserOrg uo : profileService.orgsOfUser(p.getUserId())) {
            OrgNode n = orgNodeRepository.findById(uo.getOrgId()).orElse(null);
            if (n != null) {
                orgs.add(new OrgBriefDto(n.getId(), n.getCode(), n.getName(),
                        n.getNodeType() == null ? null : n.getNodeType().name(), uo.isPrimary()));
            }
        }
        return new UserProfileDto(
                p.getUserId(), p.getEmployeeNo(), p.getDisplayName(),
                p.getEmail(), p.getMobile(), p.getJobTitle(),
                p.getStatus().name(), p.getSource().name(), orgs);
    }

    private OrgNodeDto toDto(OrgNode n) {
        return new OrgNodeDto(n.getId(), n.getParentId(), n.getCode(), n.getName(),
                n.getNodeType() == null ? null : n.getNodeType().name(),
                n.getPath(), n.getSortNo(), n.getSource().name(), n.getStatus().name(), n.getUpdatedAt());
    }

    /** 供内部复用：按 orgId 建索引（避免 N+1 的展示场景）。 */
    public Map<String, OrgNode> orgIndex(List<String> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Map.of();
        }
        return orgNodeRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(OrgNode::getId, Function.identity(), (a, b) -> a));
    }
}
