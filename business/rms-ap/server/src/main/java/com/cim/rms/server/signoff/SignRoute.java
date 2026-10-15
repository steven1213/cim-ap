package com.cim.rms.server.signoff;

/**
 * 签核路由类型（Req.md FR-L4 / IF-W1：会签 / 或签 / 多级）。
 *
 * <p>内置签核流本版以单级审批为主（{@link #SINGLE}），{@link #COUNTERSIGN} 与 {@link #ANY}
 * 为与外部签核系统（BPM）集成预留的语义：会签=全部通过方生效，或签=任一通过即生效。
 * 路由决策的<b>执行</b>在外部签核系统，RMS 侧只记录并驱动最终决策回调。</p>
 */
public enum SignRoute {
    /** 单级审批（内置默认）。 */
    SINGLE,
    /** 会签（全部审批人通过方生效）。 */
    COUNTERSIGN,
    /** 或签（任一审批人通过即生效）。 */
    ANY
}
