package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程校验明细（rule_code 表驱动可启停；validate 一次全量替换本表）。
 *
 * <p>validate_state 三态在 {@link CimFlow}：ERROR≥1 → INVALID；0 ERROR 有 WARN →
 * VALID；明细逐条落本表，摘要（条数）写 flow.validate_msg。severity：ERROR / WARN。
 * step_id 空 = 流程级错误（如缺 END 节点）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow_validate_result", indexes = {
        @Index(name = "idx_fvr_flow", columnList = "flow_id")
})
public class CimFlowValidateResult extends BaseDefData {

    public static final String SEVERITY_ERROR = "ERROR";
    public static final String SEVERITY_WARN = "WARN";

    /** 所属流程 id。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId;

    /** 节点 id（定位到 step；空 = 流程级错误）。 */
    @Column(name = "step_id", length = 64)
    private String stepId;

    /** 规则码（§5 规则清单：FLOW_NODE_REQUIRED / ASSIGN_REQUIRED / …）。 */
    @Column(name = "rule_code", length = 64, nullable = false)
    private String ruleCode;

    /** 严重级：ERROR / WARN。 */
    @Column(name = "severity", length = 16, nullable = false)
    private String severity;

    /** 问题文案（可直接展示；i18n 在服务端拼好）。 */
    @Column(name = "message", length = 512, nullable = false)
    private String message;
}
