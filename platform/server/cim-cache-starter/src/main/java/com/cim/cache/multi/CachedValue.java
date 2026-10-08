package com.cim.cache.multi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存值信封：把「真实值」与「空值占位」区分开，用于防穿透。
 *
 * <p>Redis 层使用 {@code GenericJackson2JsonRedisSerializer} 序列化，{@code value} 字段会写入类型信息（{@code @class}），
 * 还原时恢复真实类型。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedValue {

    /** 实际值（空值占位时为 null）。 */
    private Object value;

    /** 是否空值占位（防穿透）。 */
    private boolean nullValue;

    public static CachedValue of(Object value) {
        return new CachedValue(value, false);
    }

    public static CachedValue nullSentinel() {
        return new CachedValue(null, true);
    }

    /**
     * 是否为真实值（非占位）。
     *
     * <p>派生属性，不参与序列化——否则 Redis 反序列化时会因 JSON 中的 {@code "real"} 字段
     * 找不到对应 setter 而抛 {@code UnrecognizedPropertyException}（值为派生量，无需持久化）。</p>
     */
    @JsonIgnore
    public boolean isReal() {
        return !nullValue;
    }
}
