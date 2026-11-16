package com.cim.mds.server.namingrule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * DATE 日期段解析器——按 pattern 将当前时间格式化（如 yyyyMMdd / yyMMdd）。
 *
 * <p>pattern 白名单字符：y/M/d/H/m/s 与 {@code -}/{@code .}（拒绝含引号等注入面）；
 * length 缺省 = 用样例时间 2026-11-17 08:30:05 按该 pattern 格式化的长度；
 * 渲染后仍逐段校验声明长度（跨天长度不变的 pattern 才允许定长语义）。</p>
 */
@Component
public class DateSegmentResolver implements SegmentResolver {

    /** 样例时间：各字段都是常见两位/四位宽度，推断长度用。 */
    private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 11, 17, 8, 30, 5);

    @Override
    public String type() {
        return SegmentSpec.TYPE_DATE;
    }

    @Override
    public void validate(SegmentSpec seg, NamingRuleAdminService.ObjectSpec spec, int index) {
        String pattern = seg.pattern();
        if (pattern == null || pattern.isBlank()) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "日期段 pattern 不能为空");
        }
        if (!pattern.matches("[yMdHms\\-.]{1,32}")) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "pattern 仅允许 y/M/d/H/m/s 与 -/. 字符: " + pattern);
        }
        try {
            DateTimeFormatter.ofPattern(pattern).format(SAMPLE);
        } catch (IllegalArgumentException e) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "pattern 不合法: " + e.getMessage());
        }
        LiteralSegmentResolver.requireLengthIfPresent(seg, inferLength(seg), index);
    }

    @Override
    public String render(SegmentSpec seg, Map<String, String> ctx, String seqValue) {
        return DateTimeFormatter.ofPattern(seg.pattern()).format(LocalDateTime.now());
    }

    @Override
    public String regex(SegmentSpec seg) {
        return "\\d{" + inferLength(seg) + "}";
    }

    @Override
    public int inferLength(SegmentSpec seg) {
        return seg.pattern() == null ? -1
                : DateTimeFormatter.ofPattern(seg.pattern()).format(SAMPLE).length();
    }
}
