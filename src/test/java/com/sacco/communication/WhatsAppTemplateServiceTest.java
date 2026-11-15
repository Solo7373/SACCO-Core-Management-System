package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.auth.PermissionService;
import com.sacco.communication.dto.MetaTemplateDefinition;
import com.sacco.communication.dto.WhatsAppTemplateRequest;
import com.sacco.communication.dto.WhatsAppTemplateResponse;
import com.sacco.communication.dto.WhatsAppTemplateSyncResponse;
import com.sacco.communication.entity.WhatsAppTemplate;
import com.sacco.communication.exception.WhatsAppProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppValidationException;
import com.sacco.communication.repository.WhatsAppTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppTemplateServiceTest {

    @Mock private WhatsAppTemplateRepository repository;
    @Mock private MetaWhatsAppClient client;
    @Mock private PermissionService permissionService;
    @Mock private AuditService auditService;

    private WhatsAppProperties properties;
    private WhatsAppTemplateService service;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setPhoneNumberId("12345");
        properties.setAccessToken("token");
        properties.setWabaId("waba-1");
        properties.setDefaultLanguage("en");
        service = new WhatsAppTemplateService(
                repository, client, properties, permissionService, auditService);
    }

    @Test
    void syncCreatesAndUpdatesLocalMirror() {
        MetaTemplateDefinition loan = new MetaTemplateDefinition(
                "t1", "loan_due", "en", "UTILITY", "APPROVED",
                "[{\"type\":\"BODY\",\"text\":\"Hello {{1}}, pay {{2}}\"}]", 2);
        MetaTemplateDefinition otp = new MetaTemplateDefinition(
                "t2", "otp", "en", "AUTHENTICATION", "PENDING",
                "[{\"type\":\"BODY\",\"text\":\"Your code is {{1}}\"}]", 1);
        WhatsAppTemplate existing = WhatsAppTemplate.builder()
                .templateId(5L)
                .name("otp")
                .language("en")
                .build();

        when(client.fetchTemplates()).thenReturn(List.of(loan, otp));
        when(repository.findByNameAndLanguage("loan_due", "en"))
                .thenReturn(Optional.empty());
        when(repository.findByNameAndLanguage("otp", "en"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(WhatsAppTemplate.class)))
                .thenAnswer(invocation -> {
                    WhatsAppTemplate template = invocation.getArgument(0);
                    if (template.getTemplateId() == null) {
                        template.setTemplateId(7L);
                    }
                    return template;
                });

        WhatsAppTemplateSyncResponse response = service.sync(1L);

        assertThat(response.fetched()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.approved()).isEqualTo(1);
        assertThat(existing.getStatus())
                .isEqualTo(WhatsAppTemplate.TemplateStatus.PENDING);
        assertThat(existing.getParameterCount()).isEqualTo(1);
        assertThat(existing.getLastSyncedAt()).isNotNull();
        verify(auditService).logEvent(any(), any(), any(), any(), any());
    }

    @Test
    void syncRequiresWabaConfiguration() {
        properties.setWabaId("");

        assertThatThrownBy(() -> service.sync(1L))
                .isInstanceOf(WhatsAppProviderUnavailableException.class)
                .hasMessageContaining("WA_WABA_ID");
        verify(client, never()).fetchTemplates();
    }

    @Test
    void requireApprovedRejectsUnregisteredTemplate() {
        when(repository.findByNameAndLanguage("loan_due", "en"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireApproved("loan_due", "en"))
                .isInstanceOf(WhatsAppValidationException.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void requireApprovedRejectsPendingTemplate() {
        when(repository.findByNameAndLanguage("loan_due", "en"))
                .thenReturn(Optional.of(WhatsAppTemplate.builder()
                        .templateId(5L)
                        .name("loan_due")
                        .language("en")
                        .status(WhatsAppTemplate.TemplateStatus.PENDING)
                        .build()));

        assertThatThrownBy(() -> service.requireApproved("loan_due", "en"))
                .isInstanceOf(WhatsAppValidationException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void requireApprovedReturnsApprovedTemplate() {
        WhatsAppTemplate approved = WhatsAppTemplate.builder()
                .templateId(5L)
                .name("loan_due")
                .language("en")
                .status(WhatsAppTemplate.TemplateStatus.APPROVED)
                .build();
        when(repository.findByNameAndLanguage("loan_due", "en"))
                .thenReturn(Optional.of(approved));

        assertThat(service.requireApproved("loan_due", null)).isSameAs(approved);
    }

    @Test
    void upsertRegistersLocalTemplateWithDefaultLanguage() {
        WhatsAppTemplateRequest request = new WhatsAppTemplateRequest();
        request.setName("loan_due");
        request.setStatus(WhatsAppTemplate.TemplateStatus.APPROVED);
        request.setParameterCount(2);

        when(repository.findByNameAndLanguage("loan_due", "en"))
                .thenReturn(Optional.empty());
        when(repository.save(any(WhatsAppTemplate.class)))
                .thenAnswer(invocation -> {
                    WhatsAppTemplate template = invocation.getArgument(0);
                    template.setTemplateId(9L);
                    return template;
                });

        WhatsAppTemplateResponse response = service.upsert(request, 1L);

        assertThat(response.language()).isEqualTo("en");
        assertThat(response.status())
                .isEqualTo(WhatsAppTemplate.TemplateStatus.APPROVED);
        assertThat(response.parameterCount()).isEqualTo(2);
        verify(auditService).logEvent(any(), any(), any(), any(), any());
    }
}
