package com.cim.approval.server.dto;

/**
 * 运行时部署请求：前端拖拽设计器导出的 BPMN 2.0 XML 经此接口落地为流程定义。
 * <p>对应 v2 设计稿「平台做通用编排层」——流程定义不再依赖 classpath 静态文件，
 * 可由业务方在控制台动态设计、部署、版本迭代。</p>
 *
 * @param processKey  流程定义 key（同 key 重复部署自动递增版本）
 * @param processName 流程显示名
 * @param bpmnXml     Flowable 兼容的 BPMN 2.0 XML（含 flowable 命名空间）
 */
public record DeployProcessRequest(
        String processKey,
        String processName,
        String bpmnXml
) {
}
