package com.cim.mds.server.namingrule;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * SEQ 序号段解析器——分桶占号（占号动作由服务层完成，本解析器只做配置校验与格式化）。
 *
 * <p>length 即流水宽度（1-9，必填）；start 起始值（缺省 1）；scope 分桶口径
 * （GLOBAL/BY_PARENT/BY_LOCATION/BY_TYPE/BY_DATE——BY_DATE 按天重置流水）。
 * 一条规则可含多个 SEQ 段，各段独立计数器。</p>
 */
@Component
public class SeqSegmentResolver implements SegmentResolver {

    @Override
    public String type() {
        return SegmentSpec.TYPE_SEQ;
    }

    @Override
    public void validate(SegmentSpec seg, NamingRuleAdminService.ObjectSpec spec, int index) {
        Integer length = seg.length();
        if (length == null || length < 1 || length > 9) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "序号段 length（流水宽度）必填且须在 1-9 之间");
        }
        if (seg.start() != null && seg.start() < 0) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "起始值不能为负");
        }
        if (seg.scope() != null && !NamingRuleAdminService.SEQ_SCOPES.contains(seg.scope())) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "未知的分桶口径: " + seg.scope());
        }
    }

    @Override
    public String render(SegmentSpec seg, Map<String, String> ctx, String seqValue) {
        return seqValue;
    }

    @Override
    public String regex(SegmentSpec seg) {
        return "\\d{" + seg.length() + "}";
    }

    @Override
    public int inferLength(SegmentSpec seg) {
        return seg.length() == null ? -1 : seg.length();
    }
}
