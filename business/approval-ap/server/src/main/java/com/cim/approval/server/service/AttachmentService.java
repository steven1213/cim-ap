package com.cim.approval.server.service;

import com.cim.approval.server.domain.ApprovalAttachment;
import com.cim.approval.server.dto.AttachmentView;
import com.cim.approval.server.repository.ApprovalAttachmentRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 审批附件：文件存磁盘（按日期分目录），元数据入库，上传/删除进审计链。
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "sh", "ps1", "vbs", "js", "jar", "msi", "scr");

    private final ApprovalAttachmentRepository attachmentRepository;
    private final AuditService auditService;

    @Value("${approval.attachment.dir:./data/attachments}")
    private String baseDir;

    @Value("${approval.attachment.max-size-mb:20}")
    private long maxSizeMb;

    @Transactional
    public AttachmentView upload(MultipartFile file, String businessKey, String processInstanceId,
                                 String tenantId, String uploader) {
        if (file == null || file.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "附件内容不能为空");
        }
        if (businessKey == null || businessKey.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "businessKey 不能为空");
        }
        if (file.getSize() > maxSizeMb * 1024L * 1024L) {
            throw new BizException(BizCode.PARAM_INVALID, "附件超过大小限制 " + maxSizeMb + "MB");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String fileName = Path.of(original).getFileName().toString();
        if (fileName.isBlank()) {
            fileName = "unnamed";
        }
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            ext = fileName.substring(dot + 1).toLowerCase();
        }
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new BizException(BizCode.PARAM_INVALID, "不允许上传的文件类型: ." + ext);
        }

        String relative = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = Paths.get(baseDir, relative);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw new BizException(BizCode.SYSTEM_ERROR, "附件保存失败: " + e.getMessage());
        }

        ApprovalAttachment a = new ApprovalAttachment();
        a.setId(UUID.randomUUID().toString());
        a.setBusinessKey(businessKey);
        a.setProcessInstanceId(processInstanceId);
        a.setTenantId(tenantId);
        a.setFileName(fileName);
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setStoragePath(relative);
        a.setUploader(uploader);
        a.setUploadedAt(Instant.now());
        attachmentRepository.save(a);

        auditService.append(businessKey, processInstanceId, null, "ATTACHMENT_ADD",
                uploader, null, fileName, "attachment=" + a.getId() + ",size=" + a.getSizeBytes());
        return toView(a);
    }

    @Transactional
    public void delete(String id, String actor) {
        ApprovalAttachment a = attachmentRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "附件不存在: " + id));
        attachmentRepository.delete(a);
        try {
            Files.deleteIfExists(Paths.get(baseDir, a.getStoragePath()));
        } catch (IOException ignored) {
            // 元数据已删，孤儿文件可后台清理
        }
        auditService.append(a.getBusinessKey(), a.getProcessInstanceId(), null, "ATTACHMENT_DEL",
                actor, a.getFileName(), null, "attachment=" + id);
    }

    public List<AttachmentView> listByBusinessKey(String businessKey) {
        List<AttachmentView> views = new ArrayList<>();
        for (ApprovalAttachment a : attachmentRepository.findByBusinessKeyOrderByUploadedAtAsc(businessKey)) {
            views.add(toView(a));
        }
        return views;
    }

    /** 下载：返回文件资源与元数据；调用方负责 HTTP 头。 */
    public DownloadPayload download(String id) {
        ApprovalAttachment a = attachmentRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "附件不存在: " + id));
        Path path = Paths.get(baseDir, a.getStoragePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "附件文件已丢失: " + a.getFileName());
        }
        return new DownloadPayload(a, new FileSystemResource(path));
    }

    /** 附件元数据（供控制器做相干性校验，避免与 ApprovalProcessService 形成循环依赖）。 */
    public ApprovalAttachment meta(String id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "附件不存在: " + id));
    }

    public record DownloadPayload(ApprovalAttachment meta, Resource resource) {
    }

    private AttachmentView toView(ApprovalAttachment a) {
        return new AttachmentView(a.getId(), a.getBusinessKey(), a.getProcessInstanceId(),
                a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getUploader(), a.getUploadedAt());
    }
}
