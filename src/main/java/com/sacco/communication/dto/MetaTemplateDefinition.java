package com.sacco.communication.dto;

/** A template row returned by the WABA message_templates endpoint. */
public record MetaTemplateDefinition(
        String metaTemplateId,
        String name,
        String language,
        String category,
        String status,
        String components,
        int parameterCount) {
}
