package com.cim.approval.server.controller;

import com.cim.approval.server.dto.AttachmentView;
import com.cim.approval.server.service.AttachmentService;
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

@RestController
@RequestMapping("/api/v1/approval/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** 上传附件（发起前或审批中均可，按 businessKey 归档）。 */
    @PostMapping("/upload")
    public AttachmentView upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam String businessKey,
                                 @RequestParam(required = false) String processInstanceId,
                                 @RequestParam(required = false) String tenantId,
                                 @RequestParam String uploader) {
        return attachmentService.upload(file, businessKey, processInstanceId, tenantId, uploader);
    }

    @GetMapping
    public List<AttachmentView> list(@RequestParam String businessKey) {
        return attachmentService.listByBusinessKey(businessKey);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
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
    public void delete(@PathVariable String id, @RequestParam String actor) {
        attachmentService.delete(id, actor);
    }
}
