package com.cim.rms.server.compare;

/**
 * 比对方式（Req.md FR-C1、Req 37/38）。
 *
 * <ul>
 *   <li>{@link #FULL_BODY}：完整内容比对（Body 格式 / SHA-256 / 字节长度，字节级一致判定）；</li>
 *   <li>{@link #PARAMETER}：参数级比对（按参数快照逐参数差异；快照缺失时退化为变更摘要比对）；</li>
 *   <li>{@link #STRUCTURE}：结构级比对（版本号 / 状态 / 血缘结构差异）。</li>
 * </ul>
 */
public enum CompareMode {
    FULL_BODY,
    PARAMETER,
    STRUCTURE
}
