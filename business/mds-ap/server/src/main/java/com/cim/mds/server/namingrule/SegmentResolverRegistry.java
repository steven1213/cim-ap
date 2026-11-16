package com.cim.mds.server.namingrule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cim.spring.support.web.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * 段解析器注册表——聚合全部 {@link SegmentResolver} 实现，按段类型分发。
 *
 * <p>负责段列表 JSON 与 {@link SegmentSpec} 的互转（单一 ObjectMapper），以及
 * 按类型取解析器。新增段类型 = 新增一个 resolver bean，注册表零改动。</p>
 */
@Component
@RequiredArgsConstructor
public class SegmentResolverRegistry {

    private final List<SegmentResolver> resolvers;
    private final ObjectMapper om;

    private final Map<String, SegmentResolver> byType = new HashMap<>();

    @PostConstruct
    void init() {
        for (SegmentResolver resolver : resolvers) {
            byType.put(resolver.type(), resolver);
        }
    }

    /** 按段类型取解析器（未知类型抛 400）。 */
    public SegmentResolver get(String type, int index) {
        SegmentResolver resolver = byType.get(type);
        if (resolver == null) {
            throw badSegment(type, index, "未知段类型（支持 LITERAL/ENUM/DATE/SEQ）");
        }
        return resolver;
    }

    /** 段列表 JSON → List&lt;SegmentSpec&gt;（空列表/语法错误抛 400）。 */
    public List<SegmentSpec> parse(String segmentsJson) {
        List<SegmentSpec> segments;
        try {
            segments = om.readValue(segmentsJson,
                    om.getTypeFactory().constructCollectionType(List.class, SegmentSpec.class));
        } catch (JsonProcessingException e) {
            throw badSegment(null, -1, "段列表 JSON 不合法: " + e.getOriginalMessage());
        }
        if (segments == null || segments.isEmpty()) {
            throw badSegment(null, -1, "段列表不能为空");
        }
        return segments;
    }

    /** List&lt;SegmentSpec&gt; → 段列表 JSON。 */
    public String toJson(List<SegmentSpec> segments) {
        try {
            return om.writeValueAsString(segments);
        } catch (JsonProcessingException e) {
            throw badSegment(null, -1, "段列表序列化失败: " + e.getOriginalMessage());
        }
    }

    static BizException badSegment(String type, int index, String why) {
        String at = index >= 0 ? "（第 " + (index + 1) + " 段）" : "";
        return new BizException(com.cim.spring.support.web.BizCode.PARAM_INVALID,
                "编码段配置不合法 " + at + (type == null ? "" : "[" + type + "] ") + why);
    }
}
