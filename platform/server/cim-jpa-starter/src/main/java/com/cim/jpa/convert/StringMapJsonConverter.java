package com.cim.jpa.convert;

import jakarta.persistence.Converter;

import java.util.Map;

/**
 * {@code Map<String,Object>} ↔ JSON 文本转换器（存 TEXT/CLOB，跨库通用）。
 *
 * <p>字段用法：{@code @Convert(converter = StringMapJsonConverter.class)}。</p>
 */
@Converter
public class StringMapJsonConverter extends AbstractJsonConverter<Map<String, Object>> {

    @SuppressWarnings("unchecked")
    public StringMapJsonConverter() {
        super((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
