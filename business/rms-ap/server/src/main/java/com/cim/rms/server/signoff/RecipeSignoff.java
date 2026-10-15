package com.cim.rms.server.signoff;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 配方版本签核单（Req.md FR-L4 / §3.2 状态机、Req 41）。
 *
 * <p>一张签核单绑定一个配方版本：由 DRAFT 发起（版本转 PENDING_SIGN），审批通过则版本转 ACTIVE
 * （同配方旧 ACTIVE 自动转 OBSOLETE），拒绝则版本退回 DRAFT。除基本审批外，还承载：</p>
 * <ul>
 *   <li><b>发起原因</b>（{@code reason}）与<b>签核路由</b>（{@code signRoute}）——FR-L4 / IF-W1；</li>
 *   <li><b>参数级差异附件</b>（{@code paramDiff}，JSON）——FR-L4「改动可视」，相对上一生效版；</li>
 *   <li><b>预设限时使用</b>（{@code tempAllow*}）——Req 20 / FR-L5（超时自动回落、通过自动取消）；</li>
 *   <li><b>电子签证据链</b>（{@code eSignHash}）——IF-W1，决策不可篡改存档。</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_signoff")
public class RecipeSignoff extends BaseDefData {

    /** 配方（{@link com.cim.rms.server.recipe.Recipe} id）。 */
    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    /** 被签核的配方版本（{@link com.cim.rms.server.recipe.RecipeVersion} id）。 */
    @Column(name = "version_id", length = 64, nullable = false)
    private String versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private SignoffStatus status = SignoffStatus.PENDING;

    /** 发起人（提交签核的用户）。 */
    @Column(name = "initiator", length = 64)
    private String initiator;

    /** 审批人（通过/拒绝的用户）。 */
    @Column(name = "approver", length = 64)
    private String approver;

    /** 审批意见。 */
    @Column(name = "decision_comment", length = 512)
    private String decisionComment;

    /** 审批时间。 */
    @Column(name = "decided_at")
    private Instant decidedAt;

    /** 发起原因（本次变更为什么要签核，FR-L4）。 */
    @Column(name = "reason", length = 512)
    private String reason;

    /** 签核路由（会签/或签/多级，IF-W1；内置默认 SINGLE）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sign_route", length = 16)
    private SignRoute signRoute = SignRoute.SINGLE;

    /** 参数级差异附件（相对上一生效版的逐参数差异，JSON 字符串，FR-L4）。 */
    @Lob
    @Column(name = "param_diff")
    private String paramDiff;

    /** 预设限时使用：起。 */
    @Column(name = "temp_allow_from")
    private Instant tempAllowFrom;

    /** 预设限时使用：止（超时后自动回落为不可用，Req 20）。 */
    @Column(name = "temp_allow_to")
    private Instant tempAllowTo;

    /** 预设限时使用：使用范围（如「光刻区 · 3 台」）。 */
    @Column(name = "temp_allow_scope", length = 128)
    private String tempAllowScope;

    /** 预设限时使用：放行原因（必填留痕）。 */
    @Column(name = "temp_allow_reason", length = 512)
    private String tempAllowReason;

    /** 预设限时使用：授权人。 */
    @Column(name = "temp_allow_by", length = 64)
    private String tempAllowBy;

    /** 预设限时使用：设置时刻。 */
    @Column(name = "temp_allow_at")
    private Instant tempAllowAt;

    /** 电子签证据链哈希（SHA-256(signoffId|decision|actor|at|comment)，不可篡改存档）。 */
    @Column(name = "e_sign_hash", length = 128)
    private String eSignHash;
}
