package com.cim.rms.server.integration;

/**
 * 机台配方清单核对结果（S7F19 目录 vs RMS PPID 映射）。
 *
 * <p>这是业界成熟 RMS 的合规控制点：RMS 必须能回答「机台上到底装了哪些配方」，
 * 并把「机台有、系统没纳管」的配方识别出来——未纳管配方意味着有人绕过了变更管控。</p>
 */
public enum ReconcileResult {

    /** 映射存在且机台 HASH 与 RMS 生效版一致。 */
    OK,

    /** 映射存在，但机台 HASH 与生效版不一致——机台侧被改过（漂移）。 */
    DRIFT,

    /** 机台有该 PPID，但 RMS 没有映射——未纳管配方（孤儿，合规/安全风险）。 */
    ORPHAN
}
