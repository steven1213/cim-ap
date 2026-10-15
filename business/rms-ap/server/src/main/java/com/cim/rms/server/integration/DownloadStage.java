package com.cim.rms.server.integration;

import java.util.Set;

/**
 * IF-E2 配方下发阶段（Req §5.3 {@code RecipeDownloadProgress.stage}）。
 *
 * <p>语义对齐 SECS：{@code QUEUED}（已受理）→ {@code GRANTED}（S7F1/F2 Load Inquire/Grant，
 * 容量许可）→ {@code SENT_TO_TOOL}（S7F3 已下发）→ {@code TOOL_ACKED}（S7F4 ACKC7 机台确认）
 * → {@code VERIFIED}（回读 HASH 比对通过，S7F5/F6）/ {@code FAILED}（任意环节失败）。</p>
 *
 * <p>下发是<b>异步长流程</b>（机台可能忙碌，Req P6），因此阶段必须落库、可回执、可查询，
 * 而不是一次请求内同步返回终态。</p>
 */
public enum DownloadStage {

    /** 已受理，等待 EAP 取走。 */
    QUEUED,
    /** EAP 已获得容量许可（Load Grant），准备下发。 */
    GRANTED,
    /** 已投递到机台（S7F3 发送完成）。 */
    SENT_TO_TOOL,
    /** 机台已确认接收（S7F4 ACK）。 */
    TOOL_ACKED,
    /** 回读校验通过，闭环完成（终态）。 */
    VERIFIED,
    /** 失败（终态，但允许重新入队重试）。 */
    FAILED;

    /** 终态。 */
    public boolean terminal() {
        return this == VERIFIED || this == FAILED;
    }

    /** 是否允许从本状态迁移到 {@code next}（含「失败重试入队」）。 */
    public boolean canMoveTo(DownloadStage next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case QUEUED -> next == GRANTED || next == FAILED;
            case GRANTED -> next == SENT_TO_TOOL || next == FAILED;
            case SENT_TO_TOOL -> next == TOOL_ACKED || next == FAILED;
            case TOOL_ACKED -> next == VERIFIED || next == FAILED;
            // 回读校验不通过可重下发；VERIFIED 为终态不可再迁
            case FAILED -> next == QUEUED;
            case VERIFIED -> false;
        };
    }

    /** 本状态允许迁移到的目标集合（用于报文提示）。 */
    public Set<DownloadStage> nextAllowed() {
        return java.util.Arrays.stream(values()).filter(this::canMoveTo)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
