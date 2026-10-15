package com.cim.approval.server.controller;

import com.cim.approval.server.dto.AttachmentView;
import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.approval.server.service.ApprovalProcessService;
import com.cim.approval.server.service.AttachmentService;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 附件接口。<b>授权约定</b>：下载/删除须与该附件所属审批实例相干
 * （发起人/审计参与人/历史受理人/当前候选人，复用详情可见性）。
 * 认证态身份取 JWT（下载走 {@code ?token=} 注入头，见 ApprovalSecurityConfig）；
 * dev 态回退 userId 参数。
 */
@RestController
@RequestMapping("/api/v1/approval/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ApprovalProcessService processService;
    private final CurrentUserResolver currentUser;

    /** 上传附件（发起前或审批中均可，按 businessKey 归档）。 */
    @PostMapping("/upload")
    public Result<AttachmentView> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam String businessKey,
                                         @RequestParam(required = false) String processInstanceId,
                                         @RequestParam(required = false) String tenantId,
                                         @RequestParam(required = false) String uploader) {
        return Result.ok(attachmentService.upload(file, businessKey, processInstanceId, tenantId,
                currentUser.resolve(uploader)));
    }

    @GetMapping
    public Result<List<AttachmentView>> list(@RequestParam String businessKey,
                                             @RequestParam(required = false) String userId) {
        return Result.ok(attachmentService.listByBusinessKey(businessKey));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id,
                                             @RequestParam(required = false) String userId) {
        String user = currentUser.resolveNullable(userId);
        assertAccessible(id, user);
        AttachmentService.DownloadPayload payload = attachmentService.download(id);
        String encoded = URLEncoder.encode(payload.meta().getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .body(payload.resource());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id, @RequestParam(required = false) String actor) {
        String user = currentUser.resolve(actor);
        assertAccessible(id, user);
        attachmentService.delete(id, user);
        return Result.ok();
    }

    /** 相干性校验：用户须与附件所属实例相干；dev 态无身份时跳过（与整站 dev 放行语义一致）。 */
    private void assertAccessible(String attachmentId, String user) {
        if (user == null || user.isBlank()) {
            return;
        }
        var meta = attachmentService.meta(attachmentId);
        if (meta.getProcessInstanceId() != null && !meta.getProcessInstanceId().isBlank()) {
            processService.assertInstanceVisible(meta.getProcessInstanceId(), user);
        }
    }
}
