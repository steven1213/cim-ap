package com.cim.mq.spi;

import java.util.EnumSet;
import java.util.Set;

/**
 * MQ 厂商能力声明。
 *
 * <p>由各 provider 在 {@link MqProvider#features()} 返回，框架据此设置默认策略并在
 * 配置期做能力校验（例如要求有序的 topic 不得落到 {@code orderedByKey=false} 的 provider）。</p>
 */
public class ProviderFeatures {

    /** 能力维度。 */
    public enum Feature {
        /** 生产者事务 / 精确一次语义 */
        TRANSACTIONAL,
        /** 支持按 key 分区有序（同 key 落到同分区/队列） */
        ORDERED_BY_KEY,
        /** 支持延迟/定时投递 */
        DELAY,
        /** 原生死信队列支持 */
        DEAD_LETTER,
        /** 原生幂等发送（去重生产端重发） */
        PRODUCER_IDEMPOTENT
    }

    private final Set<Feature> features;

    private ProviderFeatures(Set<Feature> features) {
        this.features = features;
    }

    public static ProviderFeatures of(Feature... fs) {
        return new ProviderFeatures(EnumSet.of(fs[0], fs).clone());
    }

    public static ProviderFeatures from(Set<Feature> fs) {
        return new ProviderFeatures(EnumSet.copyOf(fs));
    }

    public boolean supports(Feature f) {
        return features.contains(f);
    }

    public Set<Feature> all() {
        return EnumSet.copyOf(features);
    }

    /**
     * 校验是否具备所需能力，缺一则抛异常（配置期拦截误用）。
     *
     * @param required 需要的能力
     * @param context  用于异常信息的上下文描述
     */
    public void require(Set<Feature> required, String context) {
        for (Feature f : required) {
            if (!features.contains(f)) {
                throw new IllegalStateException(
                        "MQ provider 能力不足：topic[" + context + "] 需要能力 " + f
                                + "，但当前 provider 未声明支持。请改用支持该能力的 provider 或调整需求。");
            }
        }
    }
}
