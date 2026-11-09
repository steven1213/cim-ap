package com.cim.notify.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 实时推送事件（跨实例经 Redis 序列化传输，须可 JSON 往返）。
 *
 * <p>投递目标分三类：{@link TargetType#USER}（指定用户）、{@link TargetType#ROLE}
 * （指定角色下的所有在线连接）、{@link TargetType#TOPIC}（订阅某主题的在线连接）。
 * {@code payload} 为业务自定义 JSON 字符串，starter 不解释其内容。
 * {@code appCode} 用于 Redis 频道命名空间，避免跨 ap 误投。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotifyEvent {

    /** 租户（投递匹配与隔离的必需维度）。 */
    private String tenantId;

    /** 目标类型。 */
    private TargetType targetType;

    /** 目标列表：userId 集合 / role 码集合 / topic 名集合（按 targetType）。 */
    private List<String> targets;

    /** 发布方 ap 的 app-code（如 "rms-ap"）。 */
    private String appCode;

    /** 业务类型（ap 自定义，如 "RECIPE_SIGN_OFF"）。 */
    private String type;

    /** 业务载荷（JSON 字符串）。 */
    private String payload;

    /** 事件唯一 id（缺省由发布器补）。 */
    private String id;

    /** 事件时间戳（缺省由发布器补）。 */
    private long ts;

    public NotifyEvent(String tenantId, TargetType targetType, List<String> targets,
                       String appCode, String type, String payload) {
        this.tenantId = tenantId;
        this.targetType = targetType;
        this.targets = targets;
        this.appCode = appCode;
        this.type = type;
        this.payload = payload;
    }

    public enum TargetType {
        USER, ROLE, TOPIC
    }
}
