package com.cim.cache.support;

import com.cim.cache.cluster.CacheInvalidationBroadcaster;
import com.cim.cache.cluster.CacheInvalidationEvent;
import com.cim.cache.multi.MultiLevelCache;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;

/**
 * {@link CacheEvictCim} 切面：方法成功返回后使缓存失效（Cache-Aside 写后删）。
 *
 * <p>{@code allEntries=true} 时清空整个命名空间；否则按 key 失效单条。仅 {@code @AfterReturning} 触发，
 * 保证写失败时不误删缓存。</p>
 *
 * <p>若存在 {@link CacheInvalidationBroadcaster}（L2 Redis 存在时自动装配），失效成功后会广播失效事件，
 * 让其它节点同步清除本地缓存，实现多实例一致（见 README §11）。</p>
 */
@Aspect
public class CacheEvictCimAspect {

    private final MultiLevelCache multiLevelCache;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectProvider<CacheInvalidationBroadcaster> broadcasterProvider;

    public CacheEvictCimAspect(MultiLevelCache multiLevelCache, CacheKeyGenerator keyGenerator,
                              ObjectProvider<CacheInvalidationBroadcaster> broadcasterProvider) {
        this.multiLevelCache = multiLevelCache;
        this.keyGenerator = keyGenerator;
        this.broadcasterProvider = broadcasterProvider;
    }

    @AfterReturning("@annotation(com.cim.cache.support.CacheEvictCim)")
    public void afterReturning(JoinPoint jp) {
        MethodSignature ms = (MethodSignature) jp.getSignature();
        Method method = ms.getMethod();
        CacheEvictCim ann = method.getAnnotation(CacheEvictCim.class);
        CacheInvalidationBroadcaster broadcaster = broadcasterProvider.getIfAvailable();
        if (ann.allEntries()) {
            multiLevelCache.evictNamespace(ann.value());
            if (broadcaster != null && broadcaster.isEnabled()) {
                broadcaster.broadcast(new CacheInvalidationEvent(ann.value(), null, true));
            }
        } else {
            String key = keyGenerator.generate(ann.value(), ann.key(), jp.getArgs());
            multiLevelCache.evict(key);
            // 写路径补录：新增/更新后该 key 变为有效，须补入 BloomFilter，否则后续读取会被判穿透
            // （删除场景残留仅造成假阳性，可接受——BloomFilter 无法删除元素）。
            multiLevelCache.recordValidKey(key);
            if (broadcaster != null && broadcaster.isEnabled()) {
                broadcaster.broadcast(new CacheInvalidationEvent(ann.value(), key, false));
            }
        }
    }
}
