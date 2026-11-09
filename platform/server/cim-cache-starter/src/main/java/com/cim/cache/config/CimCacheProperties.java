package com.cim.cache.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 缓存配置（{@code cim.cache.*}，见 design.md §2.5 / README §11）。
 *
 * <p>总开关 {@link #enabled} 默认开启；Redis 未配置或不可达时自动降级为本地 Caffeine 缓存
 * （{@link #useMultiLevel} 仍按配置，但 L2 实际不可用）。</p>
 *
 * <p><b>配置边界（与 Spring Boot 对齐）</b>：Redis 连接参数一律用 {@code spring.data.redis.*}
 * （本 starter 直接消费 Boot 装配的 {@code RedisConnectionFactory}，不另设连接配置）；
 * 本类只承载「多级缓存自身的语义」（键前缀、各级 TTL、防穿透/雪崩开关）。
 * 时间类配置统一用 {@link Duration}（{@code 5m} / {@code 300s}），裸数字按秒解释。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.cache")
public class CimCacheProperties {

    /** 总开关，默认开启。 */
    private boolean enabled = true;

    /** 全局键前缀（避免与其它 Redis key 冲突）。 */
    private String keyPrefix = "cim";

    /** 是否启用多级（L1 本地 + L2 Redis）；false 时仅本地。 */
    private boolean useMultiLevel = true;

    /** L2 Redis 配置。 */
    private Redis redis = new Redis();

    /** L1 Caffeine 配置。 */
    private Caffeine caffeine = new Caffeine();

    /** 空值守卫（防穿透）。 */
    private NullValue nullValue = new NullValue();

    /** TTL 随机抖动（防雪崩）。 */
    private TtlJitter ttlJitter = new TtlJitter();

    /** BloomFilter 防穿透增强（可选，需业务提供 BloomFilterProvider Bean）。 */
    private Bloom bloom = new Bloom();

    /** 跨节点缓存失效（多实例一致）。 */
    private Cluster cluster = new Cluster();

    @Getter
    @Setter
    public static class Redis {
        /** 是否尝试启用 L2 Redis；不可达时降级本地。 */
        private boolean enabled = true;
        /** L2 键前缀（追加在全局前缀之后）。 */
        private String keyPrefix = "cache:";
        /** L2 默认 TTL（默认 5m）。 */
        @DurationUnit(ChronoUnit.SECONDS)
        private Duration defaultTtl = Duration.ofSeconds(300);
    }

    @Getter
    @Setter
    public static class Caffeine {
        /** L1 最大条目数。 */
        private int maxSize = 10000;
        /** L1 TTL（默认 5m）；{@link Duration#ZERO} 表示不过期。 */
        @DurationUnit(ChronoUnit.SECONDS)
        private Duration ttl = Duration.ofSeconds(300);
    }

    @Getter
    @Setter
    public static class NullValue {
        /** 是否启用空值占位（防穿透）。 */
        private boolean enabled = true;
        /** 空值占位 TTL（默认 1m，宜短）。 */
        @DurationUnit(ChronoUnit.SECONDS)
        private Duration ttl = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class TtlJitter {
        /** 抖动比例（0.1 = ±10%）。 */
        private double percent = 0.1;
        /** 抖动上限（默认 1m），避免长 TTL 抖动过大。 */
        @DurationUnit(ChronoUnit.SECONDS)
        private Duration maxJitter = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class Bloom {
        /** 是否启用 BloomFilter 防穿透增强（还需提供 BloomFilterProvider Bean，否则自动关闭）。 */
        private boolean enabled = false;
        /** 回源得到真实值后是否把 key 增量记录进 BloomFilter（无法弥补「首次查询尚未灌入的有效 key」）。 */
        private boolean addOnLoad = false;
    }

    @Getter
    @Setter
    public static class Cluster {
        /** 是否启用跨节点失效广播（L2 Redis 存在时生效，使各实例本地缓存最终一致）。 */
        private boolean invalidationEnabled = true;
    }
}
