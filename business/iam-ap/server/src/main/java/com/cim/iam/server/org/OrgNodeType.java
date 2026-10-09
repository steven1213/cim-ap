package com.cim.iam.server.org;

/**
 * 组织节点类型（identity-directory.md §3.1）。
 *
 * <p>制造维度：{@link #AREA} 厂区 → {@link #WORKSHOP} 车间 → {@link #LINE} 产线 →
 * {@link #PROCESS} 工序 → {@link #TEAM} 班组/责任区（IAM 自建）；
 * 行政维度：{@link #DEPT} 行政部门（AD 同步层）。
 * 制造维度是数据权限与批量准入的依据——AD 里通常只有行政组织，表达不了产线/工序。</p>
 */
public enum OrgNodeType {

    /** 厂区。 */
    AREA,
    /** 车间。 */
    WORKSHOP,
    /** 产线。 */
    LINE,
    /** 工序。 */
    PROCESS,
    /** 班组 / 责任区。 */
    TEAM,
    /** 行政部门（AD 同步层）。 */
    DEPT
}
