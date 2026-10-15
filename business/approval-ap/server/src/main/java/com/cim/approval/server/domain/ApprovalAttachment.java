package com.cim.approval.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 审批附件：文件本体存本地磁盘（生产可换对象存储），元数据入库。
 * <p>通过 businessKey 与流程实例关联；上传/删除均写入审计链。</p>
 */
@Entity
@Table(name = "approval_attachment")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalAttachment {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "business_key", nullable = false, length = 128)
    private String businessKey;

    /** 发起前上传时可能尚无实例，允许为空。 */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** 磁盘存储相对路径（相对 attachment.dir）。 */
    @Column(name = "storage_path", nullable = false, length = 255)
    private String storagePath;

    @Column(name = "uploader", nullable = false, length = 64)
    private String uploader;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
