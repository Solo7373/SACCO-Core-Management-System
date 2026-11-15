package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.communication.entity.IncomingSms;
import com.sacco.communication.entity.SmsDeliveryReport;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.entity.SmsOptOut;
import com.sacco.communication.repository.IncomingSmsRepository;
import com.sacco.communication.repository.SmsDeliveryReportRepository;
import com.sacco.communication.repository.SmsMessageRepository;
import com.sacco.communication.repository.SmsOptOutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsWebhookServiceTest {

    private static final String PHONE = "+256772123456";

    @Mock private SmsDeliveryReportRepository reports;
    @Mock private IncomingSmsRepository incomingMessages;
    @Mock private SmsMessageRepository messages;
    @Mock private SmsOptOutRepository optOuts;
    @Mock private PhoneNumberService phoneNumbers;
    @Mock private AuditService auditService;

    @InjectMocks private SmsWebhookService service;

    @Test
    void marksMessageDeliveredFromSuccessReport() {
        SmsMessage message = message(SmsMessage.SmsStatus.SENT);
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.of(message));
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean matched = service.processDeliveryReport(deliveryPayload("Success", null));

        assertThat(matched).isTrue();
        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.DELIVERED);
        assertThat(message.getDeliveredAt()).isNotNull();

        ArgumentCaptor<SmsDeliveryReport> reportCaptor =
                ArgumentCaptor.forClass(SmsDeliveryReport.class);
        verify(reports, atLeastOnce()).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().isProcessed()).isTrue();
    }

    @Test
    void leavesReportUnprocessedWhenOutboxHasNoProviderIdYet() {
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.empty());
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean matched = service.processDeliveryReport(deliveryPayload("Success", null));

        assertThat(matched).isFalse();
        ArgumentCaptor<SmsDeliveryReport> reportCaptor =
                ArgumentCaptor.forClass(SmsDeliveryReport.class);
        verify(reports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().isProcessed()).isFalse();
        verify(messages, never()).save(any());
    }

    @Test
    void lateSentReportCannotDowngradeDeliveredMessage() {
        SmsMessage message = message(SmsMessage.SmsStatus.DELIVERED);
        message.setProviderStatus("Success");
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.of(message));
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean matched = service.processDeliveryReport(deliveryPayload("Sent", null));

        assertThat(matched).isTrue();
        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.DELIVERED);
        assertThat(message.getProviderStatus()).isEqualTo("Success");
        verify(messages, never()).save(any());
    }

    @Test
    void duplicateSuccessReportDoesNotMoveDeliveredAt() {
        LocalDateTime deliveredAt = LocalDateTime.now().minusMinutes(10);
        SmsMessage message = message(SmsMessage.SmsStatus.DELIVERED);
        message.setDeliveredAt(deliveredAt);
        message.setProviderStatus("Success");
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.of(message));
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean matched = service.processDeliveryReport(deliveryPayload("Success", null));

        assertThat(matched).isTrue();
        assertThat(message.getDeliveredAt()).isEqualTo(deliveredAt);
        verify(messages, never()).save(any());
    }

    @Test
    void failedReportMarksMessageFailed() {
        SmsMessage message = message(SmsMessage.SmsStatus.SENT);
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.of(message));
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processDeliveryReport(deliveryPayload("Failed", "No route to handset"));

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.FAILED);
        assertThat(message.getFailureReason()).contains("No route to handset");
    }

    @Test
    void unknownReportStatusLeavesOutboxStatusUnchanged() {
        SmsMessage message = message(SmsMessage.SmsStatus.SENT);
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.of(message));
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processDeliveryReport(deliveryPayload("SomethingNew", null));

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.SENT);
        verify(messages).save(message);
    }

    @Test
    void reprocessesReportThatArrivedBeforeProviderId() {
        SmsDeliveryReport report = SmsDeliveryReport.builder()
                .reportId(10L)
                .providerMessageId("ATXid_1")
                .status("Success")
                .processed(false)
                .receivedAt(LocalDateTime.now())
                .build();
        SmsMessage message = message(SmsMessage.SmsStatus.SENT);

        when(reports.findTop100ByProcessedFalseAndReceivedAtAfterOrderByReportIdAsc(any()))
                .thenReturn(List.of(report));
        when(messages.findFirstByProviderMessageIdOrderByMessageIdAsc("ATXid_1"))
                .thenReturn(Optional.of(message));
        when(reports.save(any(SmsDeliveryReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int matched = service.reprocessPendingDeliveryReports();

        assertThat(matched).isEqualTo(1);
        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.DELIVERED);
        assertThat(report.isProcessed()).isTrue();
    }

    @Test
    void stopKeywordCreatesActiveOptOut() {
        when(incomingMessages.findByProviderMessageId("ATXid_2"))
                .thenReturn(Optional.empty());
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(optOuts.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(incomingMessages.save(any(IncomingSms.class)))
                .thenAnswer(invocation -> {
                    IncomingSms sms = invocation.getArgument(0);
                    if (sms.getIncomingId() == null) {
                        sms.setIncomingId(10L);
                    }
                    return sms;
                });
        when(optOuts.save(any(SmsOptOut.class)))
                .thenAnswer(invocation -> {
                    SmsOptOut optOut = invocation.getArgument(0);
                    if (optOut.getOptOutId() == null) {
                        optOut.setOptOutId(20L);
                    }
                    return optOut;
                });

        boolean processed = service.processIncomingSms(incomingPayload("STOP"));

        assertThat(processed).isTrue();
        ArgumentCaptor<SmsOptOut> optOutCaptor = ArgumentCaptor.forClass(SmsOptOut.class);
        verify(optOuts).save(optOutCaptor.capture());
        assertThat(optOutCaptor.getValue().isActive()).isTrue();
        assertThat(optOutCaptor.getValue().getPhoneNumber()).isEqualTo(PHONE);
        assertThat(optOutCaptor.getValue().getReason()).contains("STOP");
        verify(auditService).logEvent(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void startKeywordDeactivatesExistingOptOut() {
        SmsOptOut existing = SmsOptOut.builder()
                .optOutId(20L)
                .phoneNumber(PHONE)
                .active(true)
                .build();
        when(incomingMessages.findByProviderMessageId("ATXid_2"))
                .thenReturn(Optional.empty());
        when(phoneNumbers.normalize(PHONE)).thenReturn(PHONE);
        when(optOuts.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
        when(incomingMessages.save(any(IncomingSms.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(optOuts.save(any(SmsOptOut.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processIncomingSms(incomingPayload("START"));

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getReason()).contains("START");
    }

    @Test
    void duplicateIncomingCallbackIsAcknowledgedWithoutReprocessing() {
        when(incomingMessages.findByProviderMessageId("ATXid_2"))
                .thenReturn(Optional.of(IncomingSms.builder()
                        .incomingId(10L)
                        .providerMessageId("ATXid_2")
                        .build()));

        boolean processed = service.processIncomingSms(incomingPayload("STOP"));

        assertThat(processed).isFalse();
        verify(incomingMessages, never()).save(any());
        verify(optOuts, never()).save(any());
    }

    private SmsMessage message(SmsMessage.SmsStatus status) {
        return SmsMessage.builder()
                .messageId(1L)
                .reference("ref-1")
                .providerMessageId("ATXid_1")
                .recipient(PHONE)
                .body("Hello")
                .status(status)
                .attempts(1)
                .maxAttempts(5)
                .build();
    }

    private SmsWebhookPayload deliveryPayload(String status, String failureReason) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("id", "ATXid_1");
        fields.put("status", status);
        fields.put("phoneNumber", PHONE);
        if (failureReason != null) {
            fields.put("failureReason", failureReason);
        }
        return new SmsWebhookPayload(fields, "{}");
    }

    private SmsWebhookPayload incomingPayload(String text) {
        return new SmsWebhookPayload(Map.of(
                "id", "ATXid_2",
                "from", PHONE,
                "to", "12345",
                "text", text), "{}");
    }
}
