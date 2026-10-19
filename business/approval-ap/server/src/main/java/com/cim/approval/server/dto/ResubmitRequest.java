package com.cim.approval.server.dto;

import java.util.List;
import java.util.Map;

/**
 * 重新提交请求（退回到发起人后，发起人修改表单并重提，全链从头重走）。
 *
 * @param formData   修改后的业务表单数据（为空则保留原数据）
 * @param formSchema 表单字段描述（可选，结构同发起）
 * @param actor      操作人（认证态取 JWT，dev 态传参）
 */
public record ResubmitRequest(
        Map<String, Object> formData,
        List<FormSchemaField> formSchema,
        String actor
) {
}
