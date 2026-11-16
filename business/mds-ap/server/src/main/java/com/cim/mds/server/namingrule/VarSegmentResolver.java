package com.cim.mds.server.namingrule;

import java.util.Map;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Component;

/**
 * VAR 变量段解析器——上下文变量直通（如 PRODUCT 编码的 {tech} 前缀）。
 *
 * <p>与 ENUM 的区别：ENUM 校验值在固定值域（字典/自定义列表）内；VAR 不设值域，
 * 只要求变量在对象白名单（{@link NamingRuleAdminService.ObjectSpec#vars()}）内，
 * 取 ctx 实值渲染——父编码是动态创建的（如自动占号的制程码），值域无法预先枚举。</p>
 */
@Component
public class VarSegmentResolver implements SegmentResolver {

    @Override
    public String type() {
        return SegmentSpec.TYPE_VAR;
    }

    @Override
    public void validate(SegmentSpec seg, NamingRuleAdminService.ObjectSpec spec, int index) {
        if (seg.var() == null || seg.var().isBlank()) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "变量段 var 不能为空");
        }
        if (!spec.vars().contains(seg.var())) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "变量 {" + seg.var() + "} 不在 " + spec.code() + " 白名单内" + spec.vars());
        }
        if (seg.length() != null && (seg.length() < 1 || seg.length() > 32)) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "length 须在 1-32 内");
        }
    }

    @Override
    public String render(SegmentSpec seg, Map<String, String> ctx, String seqValue) {
        String value = ctx.get(seg.var());
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "变量段缺少上下文变量 {" + seg.var() + "}");
        }
        return value.trim();
    }

    @Override
    public String regex(SegmentSpec seg) {
        // 变量段取值动态，手填正则只约束字符集与（声明了的）定长
        return seg.length() == null ? "[A-Za-z0-9_-]+" : "[A-Za-z0-9_-]{%d}".formatted(seg.length());
    }

    @Override
    public int inferLength(SegmentSpec seg) {
        return seg.length() == null ? -1 : seg.length();
    }
}
