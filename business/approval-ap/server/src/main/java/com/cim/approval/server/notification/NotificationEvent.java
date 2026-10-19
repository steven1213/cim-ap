package com.cim.approval.server.notification;

import java.util.List;
import java.util.Map;

/**
 * 通知事件：业务侧只发出语义化事件，由分发器路由到各渠道。
 *
 * <p>事件分类（对齐初稿 §4.11 / 框架 G7）：待办 NEW_TASK、结果/退回 TASK_RETURNED、超时 TASK_OVERDUE、
 * 催办 URGE 为企微可投递类型；其余（取消/改派/加签）为站内-only。</p>
 */
public record NotificationEvent(
        Type type,
        List<String> recipients,
        String templateKey,
        Map<String, Object> vars
) {
    public enum Type {
        NEW_TASK, TASK_DONE, TASK_RETURNED, TASK_CANCELLED,
        TASK_OVERDUE, URGE, ADDED_TO_TASK, REASSIGNED
    }

    public static NotificationEvent of(Type type, List<String> recipients, Map<String, Object> vars) {
        return new NotificationEvent(type, recipients, null, vars);
    }

    /** 模板键缺省回退到事件名。 */
    public String templateKeyOrDefault() {
        return templateKey != null && !templateKey.isBlank() ? templateKey : type.name();
    }
}
