package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 机台配方清单快照（S7F19「请求机台配方目录」的落库结果）。
 *
 * <p>回答一个问题：<b>这台机台上一共装了哪些配方，它们和 RMS 里对得上吗？</b>
 * 成熟 RMS 靠这张表识别两类风险：未纳管配方（{@link ReconcileResult#ORPHAN}）与
 * 机台侧被改（{@link ReconcileResult#DRIFT}）。{@code (device_id, ppid_on_tool)} 唯一，
 * 每次上报刷新为最新快照。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_tool_recipe_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_tri_device_ppid",
                columnNames = {"device_id", "ppid_on_tool"}))
public class ToolRecipeInventory extends BaseDefData {

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "ppid_on_tool", length = 128, nullable = false)
    private String ppidOnTool;

    /** 机台回读的 Body HASH（S7F5/F6 语义；可选，缺省则只能判 ORPHAN/OK 而无法判 DRIFT）。 */
    @Column(name = "tool_body_hash", length = 128)
    private String toolBodyHash;

    /** RMS 侧映射到的配方（未纳管时为空）。 */
    @Column(name = "mapped_recipe_id", length = 64)
    private String mappedRecipeId;

    /** 映射配方**生效版**的 Body HASH（期望值，用于漂移判定）。 */
    @Column(name = "expected_hash", length = 128)
    private String expectedHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconcile_result", length = 16, nullable = false)
    private ReconcileResult reconcileResult = ReconcileResult.OK;

    /** 最近一次在该机台看到该 PPID 的时间。 */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
