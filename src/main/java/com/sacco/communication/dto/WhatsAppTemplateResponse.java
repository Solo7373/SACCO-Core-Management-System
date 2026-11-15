package com.sacco.communication.dto;

import com.sacco.communication.entity.WhatsAppTemplate;

import java.time.LocalDateTime;

public record WhatsAppTemplateResponse(
        Long templateId,
        String metaTemplateId,
        String name,
        String language,
        WhatsAppTemplate.TemplateCategory category,
        WhatsAppTemplate.TemplateStatus status,
        int parameterCount,
        LocalDateTime lastSyncedAt) {

    public static WhatsAppTemplateResponse from(WhatsAppTemplate template) {
        return new WhatsAppTemplateResponse(
                template.getTemplateId(),
                template.getMetaTemplateId(),
                template.getName(),
                template.getLanguage(),
                template.getCategory(),
                template.getStatus(),
                template.getParameterCount(),
                template.getLastSyncedAt());
    }
}
