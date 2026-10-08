package com.cim.cache.cluster;

/**
 * 跨节点缓存失效事件广播抽象。
 *
 * <p>默认实现 {@link RedisCacheInvalidationBroadcaster} 基于 Redis pub/sub（L2 已存在时零额外基础设施、
 * 延迟最低、不耦合消息中间件）。若需「持久化 / 可靠投递」的失效（如关键权限/字典），可另实现经
 * §12 发件箱 / §13 MQ 的变体，绑定同一 {@link #CHANNEL} 即可替换。</p>
 */
public interface CacheInvalidationBroadcaster {

    /** Redis pub/sub 通道（各节点统一订阅）。 */
    String CHANNEL = "cim:cache:invalidation";

    /** 广播一次失效事件。 */
    void broadcast(CacheInvalidationEvent event);

    /** 是否启用（Bean 存在即视为启用）。 */
    boolean isEnabled();
}
