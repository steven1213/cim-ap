package com.cim.mds.server.namingrule;

import java.util.Map;

/**
 * 段解析器（策略）——每种段类型一个实现，注册于 {@link SegmentResolverRegistry}，
 * 新增段类型零侵入（加实现即接入）。
 *
 * <p>三职责：</p>
 * <ol>
 *   <li>{@link #validate}：建/改规则时校验段配置合法性（参数面、长度声明一致性）；</li>
 *   <li>{@link #render}：生成/试算时产出段值（SEQ 段的占号由服务层先解析再传入）；</li>
 *   <li>{@link #regex}：派生手填守门正则片段（各段精确量化，整码定长）。</li>
 * </ol>
 */
public interface SegmentResolver {

    /** 本解析器负责的段类型（对应 {@link SegmentSpec#type()}）。 */
    String type();

    /**
     * 建规则时的段配置校验（不合法抛 BizException 400）。
     *
     * @param seg   段配置
     * @param spec  归属对象域声明（校验 ENUM 变量白名单用）
     * @param index 段下标（报错定位）
     */
    void validate(SegmentSpec seg, NamingRuleAdminService.ObjectSpec spec, int index);

    /**
     * 渲染段值。
     *
     * @param seg    段配置
     * @param ctx    上下文变量
     * @param seqValue SEQ 段的已占号值（其余段忽略）
     */
    String render(SegmentSpec seg, Map<String, String> ctx, String seqValue);

    /** 手填守门正则片段（与渲染结果形态一致）。 */
    String regex(SegmentSpec seg);

    /** 未显式声明 length 时推断的段长度（-1 = 无法推断/不定长）。 */
    default int inferLength(SegmentSpec seg) {
        return -1;
    }
}
