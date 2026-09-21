package com.cim.spring.support.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具（框架内部使用）。
 *
 * <p>独立于 Web 层的 {@code ObjectMapper}，用于历史变更集、事件载荷等场景的序列化。
 * 注册 {@link JavaTimeModule} 以支持 {@code LocalDateTime}。</p>
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private JsonUtils() {
    }

    /** 对象 → JSON 字符串；失败返回 {@code null}（不抛出，避免影响主流程）。 */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[JsonUtils] serialize failed: {}", e.getMessage());
            return null;
        }
    }

    /** JSON 字符串 → 对象；失败返回 {@code null}。 */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("[JsonUtils] deserialize failed: {}", e.getMessage());
            return null;
        }
    }

    /** JSON 字符串 → 泛型对象；失败返回 {@code null}。 */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("[JsonUtils] deserialize failed: {}", e.getMessage());
            return null;
        }
    }

    /** 暴露底层 mapper（只读用途）。 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
