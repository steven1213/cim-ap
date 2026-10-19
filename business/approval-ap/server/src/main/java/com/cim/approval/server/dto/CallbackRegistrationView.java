package com.cim.approval.server.dto;

import java.time.Instant;

public record CallbackRegistrationView(
        String id,
        String appCode,
        String baseUrl,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        /** G2 二次确认：重复注册同 appCode 时首调返回 true，并携带 confirmToken 由调用方回填后再次提交 */
        Boolean confirmRequired,
        /** G2 二次确认令牌（仅 confirmRequired=true 时非空，回填即覆盖既有配置） */
        String confirmToken
) {
}
