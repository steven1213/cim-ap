package com.cim.approval.server.dto;

public record DefinitionXmlView(
        String definitionId,
        String key,
        String name,
        int version,
        String bpmnXml
) {
}
