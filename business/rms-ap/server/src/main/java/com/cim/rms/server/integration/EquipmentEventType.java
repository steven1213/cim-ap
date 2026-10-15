package com.cim.rms.server.integration;

/**
 * GEM 设备事件类型（SEMI E30 Collection Event 语义，Req §5.3 设备事件）。
 *
 * <p>业界惯例：配方完整性不靠「人记得去查」，而靠<b>设备主动上报</b>——这是 GEM 对配方管理的
 * 硬性要求（"设备在配方发生变化时必须通知主机"）。本枚举把设备侧上报告成 RMS 可处置的事件。</p>
 */
public enum EquipmentEventType {

    /**
     * 机台侧配方变更（GEM {@code PPChange} Collection Event）。
     *
     * <p>伴随数据变量：{@code PPChangeName}（变更的 PPID）与 {@code PPChangeStatus}
     * （{@code CREATED}/{@code EDITED}/{@code DELETED}）。收到后主机应回读重同步，
     * 否则仓库与机台会长期不一致（Req §1.2 P1 的根因）。</p>
     */
    PP_CHANGE,

    /**
     * 机台校验下发配方失败（GEM {@code PPVerificationFailed} Collection Event）。
     *
     * <p>伴随数据变量 {@code PPError}（校验失败信息）。行业惯例：<b>校验失败的配方不得使用</b>，
     * 因此本事件会联动把对应下发任务判 {@code FAILED} 并报警。</p>
     */
    PP_VERIFICATION_FAILED
}
