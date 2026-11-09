package com.cim.notify.config;

import com.cim.notify.api.DefaultNotifyPublisher;
import com.cim.notify.api.NotifyPublisher;
import com.cim.notify.fanout.LocalSseFanout;
import com.cim.notify.fanout.RedisSseFanout;
import com.cim.notify.fanout.RedisSseFanoutSubscriber;
import com.cim.notify.fanout.SseFanout;
import com.cim.notify.metrics.NotifyMetrics;
import com.cim.notify.security.BearerTokenQueryParamFilter;
import com.cim.notify.transport.SseController;
import com.cim.notify.transport.SseRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 实时推送自动装配（见 docs/platform/server/notify-starter-design.md）。
 *
 * <p>装配：SseRegistry（连接管理 + 心跳）+ SseFanout（Redis/本地扇出）+ NotifyPublisher
 * + SseController（/sys/notify/stream）+ 鉴权过滤器。</p>
 *
 * <p>指标使用内部门面 {@link NotifyMetrics}，只依赖 micrometer 接口；宿主有
 * {@code MeterRegistry}（如引入 {@code cim-obs-starter}）时自动采集，无则 no-op。</p>
 *
 * <p>约定：@ConditionalOnProperty(cim.notify.enabled) 总开关；其余 Bean 用 @ConditionalOnMissingBean
 * 让位（对齐 auth/cache/mq 范式）。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cim.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyAutoConfiguration {

    /** 指标门面：宿主提供 MeterRegistry 则采集，否则 no-op（不强制引入 actuator）。 */
    @Bean
    @ConditionalOnMissingBean
    public NotifyMetrics cimNotifyMetrics(ObjectProvider<MeterRegistry> registry) {
        return new NotifyMetrics(registry.getIfAvailable(), "cim.");
    }

    @Bean
    @ConditionalOnMissingBean
    public SseRegistry sseRegistry(NotifyProperties props, ObjectMapper objectMapper, NotifyMetrics metrics) {
        return new SseRegistry(props, objectMapper, metrics);
    }

    /** Redis 扇出（cluster.enabled=true 且存在 StringRedisTemplate 时启用）。 */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "cim.notify.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SseFanout redisSseFanout(StringRedisTemplate template, ObjectMapper objectMapper, NotifyProperties props) {
        return new RedisSseFanout(template, objectMapper, props);
    }

    /** 订阅同 appCode 频道，把跨实例事件投递到本 Pod 的连接。 */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "cim.notify.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer notifyFanoutContainer(StringRedisTemplate template, SseRegistry registry,
                                                              ObjectMapper objectMapper, NotifyProperties props) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(template.getConnectionFactory());
        String channel = props.getChannelPrefix() + ":" + props.getAppCode();
        container.addMessageListener(new RedisSseFanoutSubscriber(registry, objectMapper), new ChannelTopic(channel));
        return container;
    }

    /** 本地扇出兜底：仅在 Redis 路径未装配时生效（cluster 关闭或 Redis 不可达）。 */
    @Bean
    @ConditionalOnMissingBean(SseFanout.class)
    public SseFanout localSseFanout(SseRegistry registry) {
        return new LocalSseFanout(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyPublisher notifyPublisher(SseFanout fanout, NotifyMetrics metrics) {
        return new DefaultNotifyPublisher(fanout, metrics);
    }

    /** SSE 端点以 Bean 注册（库 jar 不依赖宿主组件扫描）。 */
    @Bean
    @ConditionalOnMissingBean
    public SseController sseController(SseRegistry registry, NotifyProperties props) {
        return new SseController(registry, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public BearerTokenQueryParamFilter bearerTokenQueryParamFilter() {
        return new BearerTokenQueryParamFilter();
    }
}
