package com.cim.approval.server.notification;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通知模板：事件 → 标题/正文（zh-CN）。变量以 {@code {key}} 占位，从事件 vars 填充。
 *
 * <p>本期内置中文模板；多语言可后续按 locale 切换资源文件。</p>
 */
@Service
public class NotificationTemplateService {

    public record Rendered(String title, String body) {}

    private static final Map<String, String> TITLE = Map.of(
            "NEW_TASK", "您有一条新待办：{name}",
            "TASK_DONE", "审批已处理：{name}",
            "TASK_RETURNED", "流程已退回，请修改后重新提交：{taskName}",
            "TASK_OVERDUE", "待办已超时：{name}",
            "URGE", "催办：{title}",
            "TASK_CANCELLED", "任务已取消",
            "ADDED_TO_TASK", "您被加签到一个任务",
            "REASSIGNED", "任务已转派给您"
    );

    private static final Map<String, String> BODY = Map.of(
            "NEW_TASK", "任务：{name}\n单号：{taskId}",
            "TASK_RETURNED", "退回意见：{reason}\n当前轮次：{round}",
            "TASK_OVERDUE", "已停留 {overdueMinutes} 分钟，请尽快处理",
            "URGE", "发起人 {from} 催促您处理：{title}"
    );

    public Rendered render(NotificationEvent event, String locale) {
        String key = event.templateKeyOrDefault();
        String titleTpl = TITLE.getOrDefault(key, key);
        String bodyTpl = BODY.getOrDefault(key, "");
        return new Rendered(fill(titleTpl, event.vars()), fill(bodyTpl, event.vars()));
    }

    private String fill(String tpl, Map<String, Object> vars) {
        if (tpl == null || vars == null) return "";
        String out = tpl;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return out;
    }
}
