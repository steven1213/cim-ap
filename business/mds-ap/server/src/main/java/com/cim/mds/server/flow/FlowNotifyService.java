package com.cim.mds.server.flow;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.api.NotifyPublisher;
import com.cim.spring.support.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 流程域实时通知（P3-3）：接平台 {@code cim-notify-starter} 统一 SSE（多实例 Redis 扇出）。
 *
 * <p><b>写点（拍板口径 2026-11-25：两个起步）</b>：</p>
 * <ul>
 *   <li>{@code flow.validate}：validate 结果含 ERROR（validate_state=INVALID）→
 *       通知编辑锁占用者（若有且非操作人本人）；</li>
 *   <li>{@code productFlow.bind / productFlow.unbind}：产品绑定变更 → 通知受影响流程
 *       的编辑锁占用者。</li>
 * </ul>
 *
 * <p><b>接收人口径</b>：MDS 无「流程负责人」主数据（能力缺口，记档待补），当前唯一可用的
 * 精确接收人 = 编辑锁占用者（occupied_by）——改他正占着的流程/其绑定，他最需要知道。</p>
 *
 * <p><b>灰度与兜底</b>：{@code cim.flow.notify-enabled}（默认 false）；starter 关闭
 * （{@code cim.notify.enabled=false}，NotifyProperties/publisher 均缺席，如测试上下文）
 * 静默跳过；任何失败只告警不阻断主流程。红点/未读历史归平台通知域 backlog。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotifyService {

    private final ObjectProvider<NotifyPublisher> publisherProvider;
    private final ObjectMapper objectMapper;

    /** 灰度开关（默认关；启用后 validate ERROR / 绑定变更才推送）。 */
    @Value("${cim.flow.notify-enabled:false}")
    private boolean enabled;

    /** 宿主 ap app-code（与 NotifyProperties.appCode 同键；starter 关闭时兜底默认值）。 */
    @Value("${cim.notify.app-code:mds}")
    private String appCode;

    /** validate 结果含 ERROR（INVALID）→ 通知编辑锁占用者（非操作人本人）。 */
    public void validateFailed(CimFlow flow, long errorCount, String actor) {
        notify(flow.getOccupiedBy(), "flow.validate", actor, Map.of(
                "flowCode", flow.getFlowCode(),
                "validateState", flow.getValidateState() == null ? "" : flow.getValidateState(),
                "errors", errorCount));
    }

    /** 产品绑定变更 → 通知受影响流程的编辑锁占用者。 */
    public void bindingChanged(CimFlow flow, String action, String productCode, String actor) {
        notify(flow.getOccupiedBy(), "productFlow." + action, actor, Map.of(
                "flowCode", flow.getFlowCode(),
                "productCode", productCode));
    }

    private void notify(String userId, String type, String actor, Object payload) {
        if (!enabled || userId == null || userId.isBlank()) {
            return;
        }
        if (userId.equals(actor)) {
            return; // 自己操作自己收，噪音
        }
        NotifyPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        try {
            publisher.publish(new NotifyEvent(TenantContext.get(), NotifyEvent.TargetType.USER,
                    List.of(userId), appCode, type,
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception ex) {
            log.warn("flow notify failed: type={} user={}", type, userId, ex);
        }
    }
}
