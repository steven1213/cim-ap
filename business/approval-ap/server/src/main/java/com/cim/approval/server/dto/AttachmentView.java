package com.cim.approval.server.dto;

import java.time.LocalDateTime;

public record AttachmentView(
        String id,
        String businessKey,
        String processInstanceId,
        String fileName,
        String contentType,
        long sizeBytes,
        String createUser,
        LocalDateTime createTime
) {
}
