package com.cim.notify.fanout;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.config.NotifyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 扇出（默认，{@code cluster.enabled=true}）：经 pub/sub 把事件广播到同 {@code appCode} 频道。
 *
 * <p>镜像 {@code cim-cache-starter} 的 {@code RedisCacheInvalidationBroadcaster}：广播失败仅 warn，
 * 不阻断主流程（事件已在发布方本地按"先落库后推送"处理过）。</p>
 */
public class RedisSseFanout implements SseFanout {

    private static final Logger log = LoggerFactory.getLogger(RedisSseFanout.class);

    private final StringRedisTemplate template;
    private final ObjectMapper objectMapper;
    private final NotifyProperties props;

    public RedisSseFanout(StringRedisTemplate template, ObjectMapper objectMapper, NotifyProperties props) {
        this.template = template;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public void fanout(NotifyEvent event) {
        try {
            String appCode = event.getAppCode() != null ? event.getAppCode() : props.getAppCode();
            String channel = props.getChannelPrefix() + ":" + appCode;
            template.convertAndSend(channel, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[cim-notify] redis fanout failed appCode={}: {}", event.getAppCode(), e.getMessage());
        }
    }
}
