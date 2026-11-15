package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.communication.dto.MetaTemplateDefinition;
import com.sacco.communication.dto.WhatsAppTemplateRequest;
import com.sacco.communication.dto.WhatsAppTemplateResponse;
import com.sacco.communication.dto.WhatsAppTemplateSyncResponse;
import com.sacco.communication.entity.WhatsAppTemplate;
import com.sacco.communication.exception.WhatsAppProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppValidationException;
import com.sacco.communication.repository.WhatsAppTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Local mirror of Meta's approved message templates.
 *
 * <p>Meta only allows a business-initiated conversation when the template is
 * approved, so the outbox checks this registry before queueing. The registry is
 * refreshed from the WABA {@code message_templates} endpoint; manual upserts
 * exist for mock development and for pre-registering a template immediately
 * after approval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateService {

    private final WhatsAppTemplateRepository repository;
    private final MetaWhatsAppClient client;
    private final WhatsAppProperties properties;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public List<WhatsAppTemplateResponse> list(Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        return repository.findAllByOrderByNameAscLanguageAsc().stream()
                .map(WhatsAppTemplateResponse::from)
                .toList();
    }

    @Transactional
    public WhatsAppTemplateSyncResponse sync(Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_MANAGE);
        if (!properties.isTemplateSyncConfigured()) {
            throw new WhatsAppProviderUnavailableException(
                    "WhatsApp template sync is not configured. Set WA_ENABLED, "
                            + "WA_PHONE_NUMBER_ID, WA_ACCESS_TOKEN and WA_WABA_ID.");
        }

        List<MetaTemplateDefinition> definitions = client.fetchTemplates();
        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        int updated = 0;
        int approved = 0;

        for (MetaTemplateDefinition definition : definitions) {
            if (blank(definition.name()) || blank(definition.language())) {
                continue;
            }
            String name = definition.name().trim();
            String language = definition.language().trim();
            WhatsAppTemplate template = repository.findByNameAndLanguage(name, language)
                    .orElseGet(() -> WhatsAppTemplate.builder()
                            .name(name)
                            .language(language)
                            .build());
            boolean isNew = template.getTemplateId() == null;

            template.setMetaTemplateId(trim(definition.metaTemplateId(), 64));
            template.setCategory(parseCategory(definition.category()));
            template.setStatus(parseStatus(definition.status()));
            template.setComponents(definition.components());
            template.setParameterCount(Math.max(0, definition.parameterCount()));
            template.setLastSyncedAt(now);
            repository.save(template);

            if (isNew) {
                created++;
            } else {
                updated++;
            }
            if (template.isApproved()) {
                approved++;
            }
        }

        auditService.logEvent(actorId, "WHATSAPP_TEMPLATES_SYNCED",
                "WHATSAPP_TEMPLATE", 0L,
                "Fetched " + definitions.size() + ", created " + created
                        + ", updated " + updated + ", approved " + approved);

        return new WhatsAppTemplateSyncResponse(
                definitions.size(), created, updated, approved, now);
    }

    @Transactional
    public WhatsAppTemplateResponse upsert(WhatsAppTemplateRequest request, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_MANAGE);
        if (request.getName() == null || request.getName().isBlank()) {
            throw new WhatsAppValidationException("Template name is required.");
        }
        String name = request.getName().trim();
        String language = blank(request.getLanguage())
                ? properties.getDefaultLanguage()
                : request.getLanguage().trim();

        WhatsAppTemplate template = repository.findByNameAndLanguage(name, language)
                .orElseGet(() -> WhatsAppTemplate.builder()
                        .name(name)
                        .language(language)
                        .build());

        template.setMetaTemplateId(trim(request.getMetaTemplateId(), 64));
        template.setCategory(request.getCategory() == null
                ? WhatsAppTemplate.TemplateCategory.UNKNOWN
                : request.getCategory());
        template.setStatus(request.getStatus() == null
                ? WhatsAppTemplate.TemplateStatus.PENDING
                : request.getStatus());
        template.setComponents(request.getComponents());
        template.setParameterCount(Math.max(0, request.getParameterCount()));
        template.setLastSyncedAt(LocalDateTime.now());
        WhatsAppTemplate saved = repository.save(template);

        auditService.logEvent(actorId, "WHATSAPP_TEMPLATE_REGISTERED",
                "WHATSAPP_TEMPLATE", saved.getTemplateId(),
                name + " (" + language + ") " + saved.getStatus());

        return WhatsAppTemplateResponse.from(saved);
    }

    /**
     * Returns the approved local mirror or fails with a caller-facing message.
     * The outbox never sends a template that Meta has not approved.
     */
    public WhatsAppTemplate requireApproved(String name, String language) {
        if (name == null || name.isBlank()) {
            throw new WhatsAppValidationException("Template name is required.");
        }
        String resolvedLanguage = blank(language)
                ? properties.getDefaultLanguage()
                : language.trim();
        WhatsAppTemplate template = repository
                .findByNameAndLanguage(name.trim(), resolvedLanguage)
                .orElseThrow(() -> new WhatsAppValidationException(
                        "Template '" + name.trim() + "' (" + resolvedLanguage
                                + ") is not registered locally. Run a template sync first."));
        if (!template.isApproved()) {
            throw new WhatsAppValidationException(
                    "Template '" + template.getName() + "' (" + template.getLanguage()
                            + ") is " + template.getStatus()
                            + " and cannot be sent until Meta approves it.");
        }
        return template;
    }

    private WhatsAppTemplate.TemplateCategory parseCategory(String value) {
        if (blank(value)) {
            return WhatsAppTemplate.TemplateCategory.UNKNOWN;
        }
        try {
            return WhatsAppTemplate.TemplateCategory.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return WhatsAppTemplate.TemplateCategory.UNKNOWN;
        }
    }

    private WhatsAppTemplate.TemplateStatus parseStatus(String value) {
        if (blank(value)) {
            return WhatsAppTemplate.TemplateStatus.UNKNOWN;
        }
        try {
            return WhatsAppTemplate.TemplateStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return WhatsAppTemplate.TemplateStatus.UNKNOWN;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
