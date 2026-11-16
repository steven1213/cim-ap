package com.cim.mds.server.namingrule;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cim.system.dict.SysDictService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * ENUM 枚举段解析器——取上下文变量值并校验其在值域内。
 *
 * <p>值域二选一：{@code dictKey}（平台数据字典 /dicts 的键，运行时实时取字典项）
 * 或 {@code values}（自定义固定值列表）。渲染值不在值域内 = 400（生成与手填同口径——
 * 手填的正则也只放行值域内的值）。</p>
 */
@Component
@RequiredArgsConstructor
public class EnumSegmentResolver implements SegmentResolver {

    private final SysDictService dictService;

    @Override
    public String type() {
        return SegmentSpec.TYPE_ENUM;
    }

    @Override
    public void validate(SegmentSpec seg, NamingRuleAdminService.ObjectSpec spec, int index) {
        if (seg.var() == null || seg.var().isBlank()) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "枚举段 var（上下文变量）不能为空");
        }
        if (!spec.vars().contains(seg.var())) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "变量 {" + seg.var() + "} 不在 " + spec.code() + " 白名单内" + spec.vars());
        }
        boolean hasDict = seg.dictKey() != null && !seg.dictKey().isBlank();
        boolean hasValues = seg.values() != null && !seg.values().isEmpty();
        if (hasDict == hasValues) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "dictKey 与 values 必须二选一");
        }
        if (hasValues && seg.values().stream().anyMatch(v -> v == null || v.isBlank())) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index, "values 含空值");
        }
        if (hasValues && seg.values().stream().map(String::length).distinct().count() > 1
                && seg.length() == null) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "values 长度不一致且未声明 length，无法定长");
        }
        if (hasValues) {
            requireLengthIfPresent(seg, seg.values().get(0).length(), index);
        }
        if (hasDict && seg.length() == null) {
            throw SegmentResolverRegistry.badSegment(seg.type(), index,
                    "字典值域段必须显式声明 length（字典项长度不归规则管）");
        }
    }

    @Override
    public String render(SegmentSpec seg, Map<String, String> ctx, String seqValue) {
        String value = ctx.get(seg.var());
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "枚举段缺少上下文变量 {" + seg.var() + "}");
        }
        value = value.trim();
        List<String> domain = resolveDomain(seg);
        if (!domain.contains(value)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "值 \"" + value + "\" 不在枚举段 {" + seg.var() + "} 值域内");
        }
        return value;
    }

    @Override
    public String regex(SegmentSpec seg) {
        List<String> domain = resolveDomain(seg);
        return domain.stream()
                .map(java.util.regex.Pattern::quote)
                .collect(Collectors.joining("|", "(?:", ")"));
    }

    @Override
    public int inferLength(SegmentSpec seg) {
        if (seg.values() != null && !seg.values().isEmpty()) {
            return seg.values().get(0).length();
        }
        return -1;
    }

    /** 值域：dictKey 实时取字典项（不存在/为空 = 配置错误抛 400），否则自定义 values。 */
    private List<String> resolveDomain(SegmentSpec seg) {
        if (seg.dictKey() != null && !seg.dictKey().isBlank()) {
            List<String> items = dictService.items(seg.dictKey()).stream()
                    .map(i -> i.getItemKey())
                    .toList();
            if (items.isEmpty()) {
                throw new BizException(BizCode.PARAM_INVALID,
                        "枚举段字典 " + seg.dictKey() + " 不存在或无字典项");
            }
            return items;
        }
        return seg.values() == null ? List.of() : seg.values();
    }

    private void requireLengthIfPresent(SegmentSpec seg, int actual, int index) {
        LiteralSegmentResolver.requireLengthIfPresent(seg, actual, index);
    }
}
