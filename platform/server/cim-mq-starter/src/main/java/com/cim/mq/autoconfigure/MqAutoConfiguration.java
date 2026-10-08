package com.cim.mq.autoconfigure;

import java.time.Duration;

import com.cim.mq.OutboxIntegrationEventPublisher;
import com.cim.mq.core.MqTemplate;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.serialize.JacksonEventSerializer;
import com.cim.mq.idempotent.IdempotencyStore;
import com.cim.mq.idempotent.InMemoryIdempotencyStore;
import com.cim.mq.idempotent.RedisIdempotencyStore;
import com.cim.mq.listener.MqListenerPostProcessor;
import com.cim.mq.outbox.JdbcOutboxStore;
import com.cim.mq.outbox.OutboxRelay;
import com.cim.mq.outbox.OutboxStore;
import com.cim.mq.resilience.CircuitBreaker;
import com.cim.mq.resilience.DeadLetterPolicy;
import com.cim.mq.resilience.RetryPolicy;
import com.cim.mq.spi.MqProvider;
import com.cim.mq.spi.MqProviderContext;
import com.cim.mq.spi.MqProviderFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

/**
 * cim-mq-starter 自动装配（见 design.md §2.6）。
 *
 * <p>总开关 {@code cim.mq.enabled=true}；按 {@code cim.mq.type} 选择 provider（经 SPI）。
 * 默认开启发件箱（不丢）+ 消费幂等（不重）+ 死信（兜底）。
 * 业务仅依赖 {@link MqTemplate} 与 {@link com.cim.mq.listener.MqListener}。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cim.mq", name = "enabled", havingValue = "true")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CimMqProperties.class)
public class MqAutoConfiguration {

    @Bean
    public EventSerializer mqEventSerializer(ObjectMapper objectMapper) {
        return new JacksonEventSerializer(objectMapper);
    }

    @Bean
    public MqProviderFactory mqProviderFactory() {
        return new MqProviderFactory();
    }

    @Bean
    public IdempotencyStore mqIdempotencyStore(ObjectProvider<StringRedisTemplate> redis,
                                               CimMqProperties props) {
        if ("redis".equals(props.getIdempotent().getStore()) && redis.getIfAvailable() != null) {
            return new RedisIdempotencyStore(redis.getIfAvailable(), props.getIdempotent().getPrefix());
        }
        return new InMemoryIdempotencyStore(props.getIdempotent().getPrefix());
    }

    @Bean
    public MqTemplate mqTemplate(CimMqProperties props, EventSerializer serializer,
                                IdempotencyStore idempotency, MqProviderFactory factory) {
        MqProvider provider = factory.getProvider(props.getType());
        MqProviderContext ctx = new MqProviderContext(props, serializer);

        RetryPolicy consumerRetry = RetryPolicy.builder()
                .maxAttempts(props.getConsumer().getMaxAttempts())
                .initialBackoff(Duration.ofMillis(500))
                .factor(2.0).maxBackoff(Duration.ofSeconds(30)).build();
        DeadLetterPolicy deadLetter = new DeadLetterPolicy(props.getConsumer().getMaxAttempts());
        Duration idemTtl = Duration.ofSeconds(props.getIdempotent().getTtlSec());

        return new MqTemplate(provider, ctx, serializer, idempotency, consumerRetry, deadLetter, idemTtl);
    }

    @Bean
    public MqListenerPostProcessor mqListenerPostProcessor(MqTemplate mqTemplate) {
        return new MqListenerPostProcessor(mqTemplate);
    }

    /** 发件箱相关 bean（开启 outbox 时装配；outbox 本质依赖关系型存储，故需 DataSource，
     *  由 {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)} 保证 DataSource 先就绪）。 */
    @Configuration
    @ConditionalOnProperty(prefix = "cim.mq.outbox", name = "enabled", havingValue = "true")
    static class OutboxConfiguration {

        @Bean
        public OutboxStore mqOutboxStore(DataSource dataSource, CimMqProperties props) {
            return new JdbcOutboxStore(dataSource, props.getOutbox().getTable(),
                    props.getOutbox().isInitSchema());
        }

        @Bean
        public OutboxIntegrationEventPublisher mqOutboxPublisher(OutboxStore store, EventSerializer serializer) {
            return new OutboxIntegrationEventPublisher(store, serializer);
        }

        @Bean
        @ConditionalOnProperty(prefix = "cim.mq.outbox", name = "relay-enabled", havingValue = "true")
        public OutboxRelay mqOutboxRelay(OutboxStore store, MqTemplate template, CimMqProperties props) {
            RetryPolicy relayRetry = RetryPolicy.builder()
                    .maxAttempts(props.getResilience().getRetryMaxAttempts())
                    .initialBackoff(Duration.ofMillis(props.getResilience().getRetryInitialBackoffMs()))
                    .factor(props.getResilience().getRetryFactor())
                    .maxBackoff(Duration.ofMillis(props.getResilience().getRetryMaxBackoffMs())).build();
            CircuitBreaker cb = new CircuitBreaker(props.getResilience().getCbFailureThreshold(),
                    Duration.ofMillis(props.getResilience().getCbCooldownMs()));
            return new OutboxRelay(store, template.producer(), serializerOf(template), relayRetry, cb,
                    props.getOutbox().getBatchSize());
        }

        private static EventSerializer serializerOf(MqTemplate t) {
            // 复用 MqTemplate 内部的序列化器：通过已发布的 bean 获取
            return t.serializer();
        }

        @Bean
        public TaskScheduler mqRelayScheduler() {
            ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
            s.setPoolSize(1);
            s.setThreadNamePrefix("cim-mq-relay-");
            s.initialize();
            return s;
        }

        @Bean
        @ConditionalOnProperty(prefix = "cim.mq.outbox", name = "relay-enabled", havingValue = "true")
        public java.util.concurrent.ScheduledFuture<?> mqOutboxRelayTask(OutboxRelay relay,
                                                                       TaskScheduler scheduler,
                                                                       CimMqProperties props) {
            return scheduler.scheduleAtFixedRate(relay, Duration.ofMillis(props.getOutbox().getIntervalMs()));
        }
    }
}
