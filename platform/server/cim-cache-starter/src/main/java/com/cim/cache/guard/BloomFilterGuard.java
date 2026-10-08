package com.cim.cache.guard;

import com.cim.cache.support.BloomFilterProvider;

/**
 * 防穿透增强守卫（可选）：在回源前用 BloomFilter 判定 key 是否「可能有效」。
 *
 * <ul>
 *   <li>若未提供 {@link BloomFilterProvider}（{@code cim.cache.bloom.enabled=false} 或 Bean 缺失），守卫失效，行为与无 BloomFilter 一致。</li>
 *   <li>若 key 确定不在集合中（{@link SimpleBloomFilter#mightContain} 返回 false），判定为穿透攻击，
 *       直接返回空值占位、绝不查 DB。</li>
 *   <li>{@code addOnLoad=true} 时，回源得到真实值后会把 key 记录进 BloomFilter（仅在已灌入集合的基础上增量补充，
 *       无法弥补「首次查询尚未灌入的有效 key」）。</li>
 * </ul>
 *
 * <p>注意：BloomFilter 只能「预先灌入」才有意义；切勿对未灌入的有效 key 期望放行——
 * 这正是它防穿透的本质（宁可误伤未灌入者，也不放过垃圾 key）。</p>
 */
public class BloomFilterGuard {

    private final SimpleBloomFilter bloomFilter;
    private final boolean addOnLoad;

    public BloomFilterGuard(BloomFilterProvider provider, boolean addOnLoad) {
        this.bloomFilter = (provider == null) ? null : provider.getBloomFilter();
        this.addOnLoad = addOnLoad;
    }

    /** 是否真正启用（有可用 BloomFilter 才算启用）。 */
    public boolean isEnabled() {
        return bloomFilter != null;
    }

    /** key 是否可能有效（不在集合中返回 false，可判定为穿透）。 */
    public boolean mightBeValid(String key) {
        return bloomFilter == null || bloomFilter.mightContain(key);
    }

    /** 记录一个已被证实有效的 key（通常在 {@code addOnLoad} 时调用）。 */
    public void record(String key) {
        if (bloomFilter != null) {
            bloomFilter.put(key);
        }
    }

    public boolean isAddOnLoad() {
        return addOnLoad;
    }
}
