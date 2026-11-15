package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.audit.AuditService;
import com.sacco.communication.entity.WhatsAppConsent;
import com.sacco.communication.entity.WhatsAppInboundMessage;
import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.entity.WhatsAppWebhookEvent;
import com.sacco.communication.repository.WhatsAppConsentRepository;
import com.sacco.communication.repository.WhatsAppInboundMessageRepository;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import com.sacco.communication.repository.WhatsAppWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    private static final String PHONE = "+256772123456";

    @Mock private WhatsAppWebhookEventRepository events;
    @Mock private WhatsAppInboundMessageRepository inboundMessages;
    @Mock private WhatsAppMessageRepository messages;
    @Mock private WhatsAppConsentRepository consents;
    @Mock private PhoneNumberService phoneNumbers;
    @Mock private AuditService auditService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks private WhatsAppWebhookService service;

    @Test
    void deliveredStatusMovesSentMessageToDelivered() {
        WhatsAppMessage message = message(WhatsAppMessage.WhatsAppStatus.SENT);
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("wamid.1"))
                .thenReturn(Optional.of(message));
        stubEventSave();
        when(messages.save(any(WhatsAppMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.process(statusPayload("delivered"));

        assertThat(message.getStatus()).isEqualTo(WhatsAppMessage.WhatsAppStatus.DELIVERED);
        assertThat(message.getProviderStatus()).isEqualTo("delivered");
        assertThat(message.getDeliveredAt()).isNotNull();

        ArgumentCaptor<WhatsAppWebhookEvent> eventCaptor =
                ArgumentCaptor.forClass(WhatsAppWebhookEvent.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isProcessed()).isTrue();
    }

    @Test
    void lateSentStatusCannotDowngradeDelivered() {
        WhatsAppMessage message = message(WhatsAppMessage.WhatsAppStatus.DELIVERED);
        message.setProviderStatus("delivered");
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("wamid.1"))
                .thenReturn(Optional.of(message));
        stubEventSave();
        when(messages.save(any(WhatsAppMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.process(statusPayload("sent"));

        assertThat(message.getStatus()).isEqualTo(WhatsAppMessage.WhatsAppStatus.DELIVERED);
        assertThat(message.getProviderStatus()).isEqualTo("delivered");
    }

    @Test
    void lateFailureCannotOverwriteDelivered() {
        WhatsAppMessage message = message(WhatsAppMessage.WhatsAppStatus.DELIVERED);
        message.setProviderStatus("delivered");
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("wamid.1"))
                .thenReturn(Optional.of(message));
        stubEventSave();
        when(messages.save(any(WhatsAppMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.process(statusPayload("failed"));

        assertThat(message.getStatus()).isEqualTo(WhatsAppMessage.WhatsAppStatus.DELIVERED);
        assertThat(message.getErrorCode()).isNull();
    }

    @Test
    void failedStatusRecordsMetaErrorDetails() {
        WhatsAppMessage message = message(WhatsAppMessage.WhatsAppStatus.SENT);
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("wamid.1"))
                .thenReturn(Optional.of(message));
        stubEventSave();
        when(messages.save(any(WhatsAppMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WhatsAppWebhookPayload.StatusUpdate status =
                new WhatsAppWebhookPayload.StatusUpdate(
                        "wamid.1", "failed", LocalDateTime.now(), "256772123456",
                        131026, 2494010, "Message undeliverable",
                        "Recipient is not on WhatsApp", "trace-9");
        service.process(new WhatsAppWebhookPayload(List.of(status), List.of(), "{}"));

        assertThat(message.getStatus()).isEqualTo(WhatsAppMessage.WhatsAppStatus.FAILED);
        assertThat(message.getProviderStatus()).isEqualTo("failed");
        assertThat(message.getErrorCode()).isEqualTo(131026);
        assertThat(message.getErrorSubcode()).isEqualTo(2494010);
        assertThat(message.getErrorMessage()).contains("not on WhatsApp");
        assertThat(message.getFbtraceId()).isEqualTo("trace-9");
    }

    @Test
    void readStatusWinsAndDoesNotMoveExistingReadTime() {
        LocalDateTime readAt = LocalDateTime.now().minusMinutes(5);
        WhatsAppMessage message = message(WhatsAppMessage.WhatsAppStatus.READ);
        message.setReadAt(readAt);
        message.setProviderStatus("read");
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("wamid.1"))
                .thenReturn(Optional.of(message));
        stubEventSave();
        when(messages.save(any(WhatsAppMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.process(statusPayload("delivered"));

        assertThat(message.getStatus()).isEqualTo(WhatsAppMessage.WhatsAppStatus.READ);
        assertThat(message.getReadAt()).isEqualTo(readAt);
        assertThat(message.getProviderStatus()).isEqualTo("read");
    }

    @Test
    void statusThatArrivesBeforeProviderIdStaysUnprocessed() {
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("wamid.1"))
                .thenReturn(Optional.empty());
        stubEventSave();

        service.process(statusPayload("delivered"));

        ArgumentCaptor<WhatsAppWebhookEvent> eventCaptor =
                ArgumentCaptor.forClass(WhatsAppWebhookEvent.class);
        verify(events).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isProcessed()).isFalse();
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("STATUS");
        verify(messages, never()).save(any());
    }

    @Test
    void duplicateStatusEventIsAcknowledgedWithoutReapplying() {
        when(events.findByEventKey(anyString()))
                .thenReturn(Optional.of(WhatsAppWebhookEvent.builder()
                        .eventId(1L)
                        .eventKey("status:wamid.1:delivered")
                        .eventType("STATUS")
                        .build()));

        service.process(statusPayload("delivered"));

        verify(events, never()).save(any());
        verify(messages, never())
                .findFirstByProviderMessageIdOrderByMessageIdAsc(anyString());
    }

    @Test
    void stopKeywordCreatesOptOutAndAuditEvent() {
        stubEventSave();
        when(inboundMessages.findByProviderMessageId("wamid.2"))
                .thenReturn(Optional.empty());
        when(phoneNumbers.normalize("256772123456")).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(consents.save(any(WhatsAppConsent.class)))
                .thenAnswer(invocation -> {
                    WhatsAppConsent consent = invocation.getArgument(0);
                    if (consent.getConsentId() == null) {
                        consent.setConsentId(20L);
                    }
                    return consent;
                });
        when(inboundMessages.save(any(WhatsAppInboundMessage.class)))
                .thenAnswer(invocation -> {
                    WhatsAppInboundMessage inbound = invocation.getArgument(0);
                    if (inbound.getInboundId() == null) {
                        inbound.setInboundId(10L);
                    }
                    return inbound;
                });

        service.process(inboundPayload("STOP"));

        ArgumentCaptor<WhatsAppConsent> consentCaptor =
                ArgumentCaptor.forClass(WhatsAppConsent.class);
        verify(consents).save(consentCaptor.capture());
        assertThat(consentCaptor.getValue().getStatus())
                .isEqualTo(WhatsAppConsent.ConsentStatus.OPT_OUT);
        assertThat(consentCaptor.getValue().getOptedOutAt()).isNotNull();
        assertThat(consentCaptor.getValue().getLastInboundAt()).isNotNull();
        verify(auditService).logEvent(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void startKeywordOptsExistingContactBackIn() {
        WhatsAppConsent existing = WhatsAppConsent.builder()
                .consentId(20L)
                .phoneNumber(PHONE)
                .status(WhatsAppConsent.ConsentStatus.OPT_OUT)
                .build();
        stubEventSave();
        when(inboundMessages.findByProviderMessageId("wamid.2"))
                .thenReturn(Optional.empty());
        when(phoneNumbers.normalize("256772123456")).thenReturn(PHONE);
        when(consents.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
        when(consents.save(any(WhatsAppConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundMessages.save(any(WhatsAppInboundMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.process(inboundPayload("start"));

        assertThat(existing.getStatus()).isEqualTo(WhatsAppConsent.ConsentStatus.OPT_IN);
        assertThat(existing.getOptedInAt()).isNotNull();
    }

    @Test
    void duplicateInboundCallbackIsNotStoredTwice() {
        when(events.findByEventKey(anyString()))
                .thenReturn(Optional.of(WhatsAppWebhookEvent.builder()
                        .eventId(1L)
                        .eventKey("message:wamid.2")
                        .eventType("MESSAGE")
                        .build()));

        service.process(inboundPayload("STOP"));

        verify(inboundMessages, never()).save(any());
        verify(consents, never()).save(any());
    }

    private void stubEventSave() {
        when(events.save(any(WhatsAppWebhookEvent.class)))
                .thenAnswer(invocation -> {
                    WhatsAppWebhookEvent event = invocation.getArgument(0);
                    if (event.getEventId() == null) {
                        event.setEventId(1L);
                    }
                    return event;
                });
    }

    private WhatsAppMessage message(WhatsAppMessage.WhatsAppStatus status) {
        return WhatsAppMessage.builder()
                .messageId(1L)
                .reference("ref-1")
                .providerMessageId("wamid.1")
                .recipient(PHONE)
                .body("Hello")
                .status(status)
                .attempts(1)
                .maxAttempts(5)
                .build();
    }

    private WhatsAppWebhookPayload statusPayload(String status) {
        return new WhatsAppWebhookPayload(
                List.of(new WhatsAppWebhookPayload.StatusUpdate(
                        "wamid.1", status, LocalDateTime.now(), "256772123456",
                        null, null, null, null, null)),
                List.of(),
                "{}");
    }

    private WhatsAppWebhookPayload inboundPayload(String body) {
        return new WhatsAppWebhookPayload(
                List.of(),
                List.of(new WhatsAppWebhookPayload.InboundMessage(
                        "wamid.2", "256772123456", "Alice", "text", body,
                        null, null, null, LocalDateTime.now())),
                "{}");
    }
}
