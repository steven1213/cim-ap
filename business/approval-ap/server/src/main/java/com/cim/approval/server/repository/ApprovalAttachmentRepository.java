package com.cim.approval.server.repository;

import com.cim.approval.server.domain.ApprovalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, String> {

    List<ApprovalAttachment> findByBusinessKeyOrderByUploadedAtAsc(String businessKey);

    boolean existsByProcessInstanceIdAndUploader(String processInstanceId, String uploader);
}
