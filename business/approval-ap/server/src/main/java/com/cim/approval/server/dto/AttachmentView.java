package com.cim.approval.server.dto;

import java.time.Instant;

public record AttachmentView(
        String id,
        String businessKey,
        String processInstanceId,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploader,
        Instant uploadedAt
) {
}
