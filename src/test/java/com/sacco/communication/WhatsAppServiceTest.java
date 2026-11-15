package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.auth.PermissionService;
import com.sacco.communication.dto.WhatsAppBulkSendRequest;
import com.sacco.communication.dto.WhatsAppBulkSendResponse;
import com.sacco.communication.dto.WhatsAppConsentRequest;
import com.sacco.communication.dto.WhatsAppConsentResponse;
import com.sacco.communication.dto.WhatsAppMessageResponse;
import com.sacco.communication.dto.WhatsAppSendRequest;
import com.sacco.communication.entity.WhatsAppConsent;
import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.entity.WhatsAppTemplate;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.exception.WhatsAppOptOutException;
import com.sacco.communication.exception.WhatsAppValidationException;
import com.sacco.communication.exception.WhatsAppWindowClosedException;
import com.sacco.communication.repository.WhatsAppConsentRepository;
import com.sacco.communication.repository.WhatsAppInboundMessageRepository;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    private static final String PHONE = "+256772123456";
    private static final String PHONE_B = "+256772123457";
    private static final String PHONE_C = "+256772123458";

    @Mock private WhatsAppMessageWriter writer;
    @Mock private WhatsAppMessageRepository repository;
    @Mock private WhatsAppInboundMessageRepository inboundMessages;
    @Mock private WhatsAppConsentRepository consents;
    @Mock private WhatsAppTemplateService templates;
    @Mock private PhoneNumberService phoneNumbers;
    @Mock private WhatsAppDeliveryService deliveryService;
    @Mock private AuditService auditService;
    @Mock private PermissionService permissionService;

    private WhatsAppProperties properties;
    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setPhoneNumberId("12345");
        properties.setAccessToken("token");
        properties.setBlockOptedOut(true);
        properties.setRequireOptInForTemplates(true);
        properties.setCustomerServiceWindowHours(24);
        properties.setMaxTextLength(4096);
        service = new WhatsAppService(
                writer, repository, inboundMessages, consents, templates,
                phoneNumbers, properties, deliveryService, auditService,
                permissionService);
    }

    @Test
    void textInsideWindowIsQueued() {
        WhatsAppSendRequest request = textRequest("Hello");
        WhatsAppMessage queued = message(WhatsAppMessage.WhatsAppMessageType.TEXT);
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN,
                        LocalDateTime.now().minusHours(1))));
        when(writer.createText(PHONE, "Hello", null, null, null, 1L))
                .thenReturn(queued);

        WhatsAppMessageResponse response = service.sendSingle(request, 1L);

        assertThat(response.status()).isEqualTo(WhatsAppMessage.WhatsAppStatus.QUEUED);
        verify(writer).createText(PHONE, "Hello", null, null, null, 1L);
        verifyNoInteractions(deliveryService);
    }

    @Test
    void textOutsideWindowIsRejected() {
        WhatsAppSendRequest request = textRequest("Hello");
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN,
                        LocalDateTime.now().minusHours(25))));

        assertThatThrownBy(() -> service.sendSingle(request, 1L))
                .isInstanceOf(WhatsAppWindowClosedException.class)
                .hasMessageContaining("24-hour");
        verify(writer, never()).createText(any(), any(), any(), any(), any(), any());
    }

    @Test
    void optedOutRecipientIsRejected() {
        WhatsAppSendRequest request = textRequest("Hello");
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_OUT, null)));

        assertThatThrownBy(() -> service.sendSingle(request, 1L))
                .isInstanceOf(WhatsAppOptOutException.class)
                .hasMessageContaining("opted out");
    }

    @Test
    void templateWithoutRecordedOptInIsRejected() {
        WhatsAppSendRequest request = templateRequest(List.of("Alice", "1000"));
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendSingle(request, 1L))
                .isInstanceOf(WhatsAppOptOutException.class)
                .hasMessageContaining("opted in");
        verify(templates, never()).requireApproved(any(), any());
    }

    @Test
    void approvedTemplateIsQueuedWithParameters() {
        WhatsAppSendRequest request = templateRequest(List.of("Alice", "1000"));
        WhatsAppTemplate template = approvedTemplate(2);
        WhatsAppMessage queued = WhatsAppMessage.builder()
                .messageId(10L)
                .reference("ref")
                .recipient(PHONE)
                .messageType(WhatsAppMessage.WhatsAppMessageType.TEMPLATE)
                .templateName("loan_due")
                .templateLanguage("en")
                .status(WhatsAppMessage.WhatsAppStatus.QUEUED)
                .attempts(0)
                .maxAttempts(5)
                .build();

        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN, null)));
        when(templates.requireApproved("loan_due", "en")).thenReturn(template);
        when(writer.createTemplate(
                PHONE, "loan_due", "en", List.of("Alice", "1000"), null, null, 1L))
                .thenReturn(queued);

        WhatsAppMessageResponse response = service.sendSingle(request, 1L);

        assertThat(response.type())
                .isEqualTo(WhatsAppMessage.WhatsAppMessageType.TEMPLATE);
        assertThat(response.templateName()).isEqualTo("loan_due");
        verify(writer).createTemplate(
                PHONE, "loan_due", "en", List.of("Alice", "1000"), null, null, 1L);
    }

    @Test
    void templateParameterCountMismatchIsRejected() {
        WhatsAppSendRequest request = templateRequest(List.of("Alice"));
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN, null)));
        when(templates.requireApproved("loan_due", "en"))
                .thenReturn(approvedTemplate(2));

        assertThatThrownBy(() -> service.sendSingle(request, 1L))
                .isInstanceOf(WhatsAppValidationException.class)
                .hasMessageContaining("expects 2");
    }

    @Test
    void bulkSkipsOptedOutAndClosedWindowRecipients() {
        WhatsAppBulkSendRequest request = new WhatsAppBulkSendRequest();
        request.setRecipients(List.of(PHONE, PHONE_B, PHONE_C));
        request.setMessage("Hello");
        request.setType(WhatsAppMessage.WhatsAppMessageType.TEXT);

        when(phoneNumbers.normalize(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_OUT, null)));
        when(consents.findByPhoneNumber(PHONE_B)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN,
                        LocalDateTime.now().minusHours(25))));
        when(consents.findByPhoneNumber(PHONE_C)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN,
                        LocalDateTime.now().minusHours(1))));
        when(writer.createText(eq(PHONE_C), eq("Hello"), isNull(),
                anyString(), isNull(), eq(1L)))
                .thenReturn(message(WhatsAppMessage.WhatsAppMessageType.TEXT));

        WhatsAppBulkSendResponse response = service.sendBulk(request, 1L);

        assertThat(response.queued()).isEqualTo(1);
        assertThat(response.skippedOptOut()).isEqualTo(1);
        assertThat(response.skippedWindowClosed()).isEqualTo(1);
        assertThat(response.messages()).hasSize(1);
        verify(writer, never()).createText(eq(PHONE), any(), any(), any(), any(), any());
        verify(writer, never()).createText(eq(PHONE_B), any(), any(), any(), any(), any());
    }

    @Test
    void immediateSendSurfacesProviderFailure() {
        WhatsAppSendRequest request = textRequest("Hello");
        request.setImmediate(true);
        WhatsAppMessage queued = message(WhatsAppMessage.WhatsAppMessageType.TEXT);
        WhatsAppMessage failed = message(WhatsAppMessage.WhatsAppMessageType.TEXT);
        failed.setStatus(WhatsAppMessage.WhatsAppStatus.FAILED);
        failed.setErrorMessage("Meta rejected the recipient");

        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN,
                        LocalDateTime.now().minusHours(1))));
        when(writer.createText(PHONE, "Hello", null, null, null, 1L))
                .thenReturn(queued);
        when(repository.findById(10L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.sendSingle(request, 1L))
                .isInstanceOf(WhatsAppException.class)
                .hasMessageContaining("could not be sent");
        verify(deliveryService).deliver(10L);
    }

    @Test
    void consentLookupReportsOpenWindow() {
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(
                consent(WhatsAppConsent.ConsentStatus.OPT_IN,
                        LocalDateTime.now().minusHours(1))));

        WhatsAppConsentResponse response = service.consent(PHONE, 1L);

        assertThat(response.customerServiceWindowOpen()).isTrue();
        assertThat(response.customerServiceWindowClosesAt()).isNotNull();
    }

    @Test
    void staffCanRecordOptOut() {
        WhatsAppConsentRequest request = new WhatsAppConsentRequest();
        request.setPhone(PHONE);
        request.setStatus(WhatsAppConsent.ConsentStatus.OPT_OUT);
        request.setSource("MEMBER_FORM");

        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(consents.save(any(WhatsAppConsent.class)))
                .thenAnswer(invocation -> {
                    WhatsAppConsent consent = invocation.getArgument(0);
                    consent.setConsentId(20L);
                    return consent;
                });

        WhatsAppConsentResponse response = service.setConsent(request, 1L);

        assertThat(response.status()).isEqualTo(WhatsAppConsent.ConsentStatus.OPT_OUT);
        assertThat(response.optedOutAt()).isNotNull();
        assertThat(response.source()).isEqualTo("MEMBER_FORM");
        verify(auditService).logEvent(any(), anyString(), anyString(), any(), anyString());
    }

    private WhatsAppSendRequest textRequest(String body) {
        WhatsAppSendRequest request = new WhatsAppSendRequest();
        request.setRecipient(PHONE);
        request.setType(WhatsAppMessage.WhatsAppMessageType.TEXT);
        request.setMessage(body);
        return request;
    }

    private WhatsAppSendRequest templateRequest(List<String> parameters) {
        WhatsAppSendRequest request = new WhatsAppSendRequest();
        request.setRecipient(PHONE);
        request.setType(WhatsAppMessage.WhatsAppMessageType.TEMPLATE);
        request.setTemplateName("loan_due");
        request.setTemplateLanguage("en");
        request.setTemplateParameters(parameters);
        return request;
    }

    private WhatsAppConsent consent(WhatsAppConsent.ConsentStatus status,
                                    LocalDateTime lastInboundAt) {
        return WhatsAppConsent.builder()
                .consentId(20L)
                .phoneNumber(PHONE)
                .status(status)
                .lastInboundAt(lastInboundAt)
                .build();
    }

    private WhatsAppTemplate approvedTemplate(int parameterCount) {
        return WhatsAppTemplate.builder()
                .templateId(30L)
                .name("loan_due")
                .language("en")
                .category(WhatsAppTemplate.TemplateCategory.UTILITY)
                .status(WhatsAppTemplate.TemplateStatus.APPROVED)
                .parameterCount(parameterCount)
                .build();
    }

    private WhatsAppMessage message(WhatsAppMessage.WhatsAppMessageType type) {
        return WhatsAppMessage.builder()
                .messageId(10L)
                .reference("ref")
                .recipient(PHONE)
                .messageType(type)
                .body("Hello")
                .status(WhatsAppMessage.WhatsAppStatus.QUEUED)
                .attempts(0)
                .maxAttempts(5)
                .build();
    }
}
