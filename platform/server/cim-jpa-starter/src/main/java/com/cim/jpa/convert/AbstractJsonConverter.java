package com.cim.jpa.convert;

import com.cim.spring.support.util.JsonUtils;
import jakarta.persistence.AttributeConverter;

/**
 * JSON 属性转换器抽象基类：把 POJO 序列化为 {@code TEXT}(MySQL/PG) / {@code CLOB}(Oracle)
 * 字符串，读取时反序列化，使业务实体与具体方言解耦（见 README §3）。
 *
 * <p>用法：为具体类型声明一个 {@code @Converter} 子类，并在字段上用
 * {@code @Convert(converter = XxxJsonConverter.class)} 指定。</p>
 *
 * @param <T> 属性类型
 */
public abstract class AbstractJsonConverter<T> implements AttributeConverter<T, String> {

    private final Class<T> type;

    protected AbstractJsonConverter(Class<T> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return attribute == null ? null : JsonUtils.toJson(attribute);
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        return JsonUtils.fromJson(dbData, type);
    }
}
