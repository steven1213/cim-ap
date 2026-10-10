package com.cim.rms.server.recipe;

/**
 * 配方体格式（Req.md §4.1 / IF-E1 bodyFormat）。
 *
 * <p>W1 语义：Body 原样存档（业界惯例：格式碎片化由后续「机型格式适配器」抽取
 * 参数快照解决，见 Req.md §1.2 P2）；本枚举标识 Body 的原始形态。</p>
 */
public enum BodyFormat {
    /** 设备私有二进制（Base64 存档）。 */
    BINARY,
    /** 文本类格式。 */
    TEXT,
    /** 结构化 JSON。 */
    JSON_STRUCTURED
}
