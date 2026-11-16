package com.cim.mds.server.namingrule;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * LITERAL 常量段解析器——原样输出。
 *
 * <p>length 缺省 = 常量文本长度；显式声明时必须与文本等长（否则建规则即 400）。</p>
 */
@Component
public class LiteralSegmentResolver implements SegmentResolver {

    @Override
    public String type() {
        return SegmentSpec.TYPE_LITERAL;
    }

    @Override
    public void validate(SegmentSpec seg, NamingRuleAdminService.ObjectSpec spec, int index) {
        if (seg.text() == null || seg.text().isBlank()) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "常量段 text 不能为空");
        }
        requireLengthIfPresent(seg, seg.text().length(), index);
    }

    @Override
    public String render(SegmentSpec seg, Map<String, String> ctx, String seqValue) {
        return seg.text();
    }

    @Override
    public String regex(SegmentSpec seg) {
        return java.util.regex.Pattern.quote(seg.text());
    }

    @Override
    public int inferLength(SegmentSpec seg) {
        return seg.text() == null ? -1 : seg.text().length();
    }

    static void requireLengthIfPresent(SegmentSpec seg, int actual, int index) {
        if (seg.length() != null && seg.length() != actual) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "length 声明为 " + seg.length() + "，与实际段长度 " + actual + " 不一致");
        }
    }
}
