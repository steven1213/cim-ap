package com.cim.cache.config;

import com.cim.cache.cluster.CacheInvalidationBroadcaster;
import com.cim.cache.cluster.RedisCacheInvalidationBroadcaster;
import com.cim.cache.cluster.RedisInvalidationSubscriber;
import com.cim.cache.guard.BloomFilterGuard;
import com.cim.cache.guard.LocalLockProvider;
import com.cim.cache.guard.LockProvider;
import com.cim.cache.guard.MutexRebuild;
import com.cim.cache.guard.NullValueGuard;
import com.cim.cache.guard.RedisLockProvider;
import com.cim.cache.guard.TtlJitter;
import com.cim.cache.multi.LocalCaffeineCache;
import com.cim.cache.multi.MultiLevelCache;
import com.cim.cache.multi.RedisCache;
import com.cim.cache.support.BloomFilterProvider;
import com.cim.cache.support.CacheEvictCimAspect;
import com.cim.cache.support.CacheKeyGenerator;
import com.cim.cache.support.CacheableCimAspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 缓存自动装配（见 design.md §2.5 / README §11）。
 *
 * <p>装配：L1 本地 Caffeine（常驻）+ 可选 L2 Redis（探测可达则启用，否则降级本地）。
 * 互斥重建锁按可用性自动选择：有 Redis 用分布式锁（{@link RedisLockProvider}），否则进程内锁
 * （{@link LocalLockProvider}）。</p>
 *
 * <p>降级策略：未配置 Redis / Redis 不可达 / {@code cim.cache.redis.enabled=false} 时，
 * 整个缓存退化为本地 Caffeine（仍具备防穿透/击穿/雪崩能力，只是不跨实例共享）。</p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "cim.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CimCacheProperties.class)
public class CacheAutoConfiguration {

    private final CimCacheProperties props;

    public CacheAutoConfiguration(CimCacheProperties props) {
        this.props = props;
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheKeyGenerator cacheKeyGenerator() {
        return new CacheKeyGenerator(props.getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalCaffeineCache localCaffeineCache() {
        return new LocalCaffeineCache(props.getCaffeine().getMaxSize(), props.getCaffeine().getTtl());
    }

    /** L2 RedisTemplate：键用 String，值用 GenericJackson（带类型信息，跨语言可读）。 */
    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "cim.cache.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisTemplate<String, Object> cimCacheRedisTemplate(RedisConnectionFactory rf) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(rf);
        RedisSerializer<String> keySer = RedisSerializer.string();
        template.setKeySerializer(keySer);
        template.setHashKeySerializer(keySer);
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /** L2 RedisCache：探测可达才启用，否则返回 null → 自动降级本地。 */
    @Bean
    @ConditionalOnMissingBean
    public RedisCache redisCache(ObjectProvider<RedisTemplate<String, Object>> templateProvider,
                                ObjectProvider<RedisConnectionFactory> rfProvider) {
        if (!props.getRedis().isEnabled()) {
            return null;
        }
        RedisTemplate<String, Object> template = templateProvider.getIfAvailable();
        RedisConnectionFactory rf = rfProvider.getIfAvailable();
        if (template == null || rf == null) {
            return null;
        }
        try (var conn = rf.getConnection()) {
            conn.ping();
        } catch (Exception e) {
            log.warn("[cim-cache] Redis 不可达，降级为本地缓存: {}", e.getMessage());
            return null;
        }
        return new RedisCache(template, buildRedisPrefix());
    }

    /** 互斥重建锁：有 Redis 用分布式锁，否则进程内锁。 */
    @Bean
    @ConditionalOnMissingBean
    public LockProvider lockProvider(ObjectProvider<RedisCache> redisCacheProvider,
                                    ObjectProvider<RedisTemplate<String, Object>> templateProvider) {
        if (redisCacheProvider.getIfAvailable() != null) {
            RedisTemplate<String, Object> template = templateProvider.getIfAvailable();
            return new RedisLockProvider(template, buildRedisPrefix());
        }
        return new LocalLockProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiLevelCache multiLevelCache(LocalCaffeineCache l1,
                                          ObjectProvider<RedisCache> l2Provider,
                                          LockProvider lockProvider,
                                          ObjectProvider<ObjectMapper> objectMapperProvider,
                                          ObjectProvider<BloomFilterGuard> bloomGuardProvider) {
        RedisCache l2 = l2Provider.getIfAvailable();
        NullValueGuard nullGuard = new NullValueGuard(
                props.getNullValue().isEnabled(), props.getNullValue().getTtl());
        TtlJitter jitter = new TtlJitter(
                props.getTtlJitter().getPercent(), props.getTtlJitter().getMaxJitter());
        MutexRebuild mutex = new MutexRebuild(lockProvider, 30);
        ObjectMapper om = objectMapperProvider.getIfAvailable();
        if (om == null) {
            om = new ObjectMapper();
        }
        om.registerModule(new JavaTimeModule());
        BloomFilterGuard bloomGuard = bloomGuardProvider.getIfAvailable();
        return new MultiLevelCache(l1, l2, nullGuard, jitter, mutex,
                props.getCaffeine().getTtl(), props.getRedis().getDefaultTtl(), om, bloomGuard);
    }

    /** 跨节点失效用的 String 序列化模板（pub/sub 通道，JSON 明文便于跨语言）。 */
    @Bean
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    public StringRedisTemplate cimCacheStringRedisTemplate(RedisConnectionFactory rf) {
        return new StringRedisTemplate(rf);
    }

    /** 跨节点失效事件广播（L2 Redis 存在且开关开启时生效）。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "cim.cache", name = {"redis.enabled", "cluster.invalidation-enabled"},
                          havingValue = "true", matchIfMissing = true)
    public CacheInvalidationBroadcaster cacheInvalidationBroadcaster(ObjectProvider<StringRedisTemplate> templateProvider,
                                                                  ObjectProvider<ObjectMapper> objectMapperProvider) {
        StringRedisTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            return null;
        }
        ObjectMapper om = objectMapperProvider.getIfAvailable();
        if (om == null) {
            om = new ObjectMapper();
        }
        return new RedisCacheInvalidationBroadcaster(template, om);
    }

    /** 订阅失效通道，使本节点本地缓存失效（多实例最终一致）。 */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "cim.cache", name = {"redis.enabled", "cluster.invalidation-enabled"},
                          havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer cacheInvalidationContainer(RedisConnectionFactory rf,
                                                                  MultiLevelCache multiLevelCache,
                                                                  ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper om = objectMapperProvider.getIfAvailable();
        if (om == null) {
            om = new ObjectMapper();
        }
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(rf);
        container.addMessageListener(new RedisInvalidationSubscriber(multiLevelCache, om),
                new ChannelTopic(CacheInvalidationBroadcaster.CHANNEL));
        return container;
    }

    /** BloomFilter 防穿透增强守卫（需业务提供 BloomFilterProvider 且开启开关）。 */
    @Bean
    @ConditionalOnMissingBean
    public BloomFilterGuard bloomFilterGuard(ObjectProvider<BloomFilterProvider> provider) {
        BloomFilterProvider p = props.getBloom().isEnabled() ? provider.getIfAvailable() : null;
        return new BloomFilterGuard(p, props.getBloom().isAddOnLoad());
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheableCimAspect cacheableCimAspect(MultiLevelCache multiLevelCache,
                                                 CacheKeyGenerator keyGenerator) {
        return new CacheableCimAspect(multiLevelCache, keyGenerator);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheEvictCimAspect cacheEvictCimAspect(MultiLevelCache multiLevelCache,
                                                  CacheKeyGenerator keyGenerator,
                                                  ObjectProvider<CacheInvalidationBroadcaster> broadcasterProvider) {
        return new CacheEvictCimAspect(multiLevelCache, keyGenerator, broadcasterProvider);
    }

    private String buildRedisPrefix() {
        String global = props.getKeyPrefix();
        String redis = props.getRedis().getKeyPrefix();
        return (global == null || global.isEmpty() ? "" : global + ":")
                + (redis == null ? "" : redis);
    }
}
