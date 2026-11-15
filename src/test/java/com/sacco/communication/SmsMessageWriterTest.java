package com.sacco.communication;

import com.sacco.communication.dto.SmsRecipientResult;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.repository.SmsMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsMessageWriterTest {

    @Mock private SmsMessageRepository repository;

    private AfricasTalkingProperties properties;
    private SmsMessageWriter writer;

    @BeforeEach
    void setUp() {
        properties = new AfricasTalkingProperties();
        properties.getDispatch().setMaxAttempts(5);
        properties.getDispatch().setBaseBackoffMs(30_000);
        properties.getDispatch().setMaxBackoffMs(3_600_000);
        writer = new SmsMessageWriter(repository, properties);
    }

    @Test
    void createBatchPersistsAllRecipientsTogether() {
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<SmsMessage> messages = writer.createBatch(
                List.of("+256772123456", "+256782123456"),
                "Hello members",
                SmsMessage.SmsMessageType.NOTIFICATION,
                "SCMS",
                "batch-1",
                7L);

        assertThat(messages).hasSize(2);
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getBatchId()).isEqualTo("batch-1");
            assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.QUEUED);
            assertThat(message.getMaxAttempts()).isEqualTo(5);
        });
        verify(repository).saveAll(any());
    }

    @Test
    void scheduleRetryRequeuesSendingMessageWithBackoff() {
        SmsMessage message = message(SmsMessage.SmsStatus.SENDING, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(message));
        when(repository.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        writer.scheduleRetry(1L, "timeout", null, null);

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.QUEUED);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(before);
    }

    @Test
    void scheduleRetryDoesNotRequeueAlreadySentMessage() {
        SmsMessage message = message(SmsMessage.SmsStatus.SENT, 1);
        when(repository.findById(1L)).thenReturn(Optional.of(message));

        writer.scheduleRetry(1L, "late timeout", null, null);

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.SENT);
        assertThat(message.getAttempts()).isEqualTo(1);
        verify(repository, never()).save(any());
    }

    @Test
    void scheduleRetryFailsAfterMaximumAttempts() {
        SmsMessage message = message(SmsMessage.SmsStatus.SENDING, 4);
        when(repository.findById(1L)).thenReturn(Optional.of(message));
        when(repository.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.scheduleRetry(1L, "still failing", null, null);

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.FAILED);
        assertThat(message.getAttempts()).isEqualTo(5);
        assertThat(message.getFailureReason()).contains("still failing");
    }

    @Test
    void markSentNeverDowngradesDeliveredMessage() {
        SmsMessage message = message(SmsMessage.SmsStatus.DELIVERED, 1);
        when(repository.findById(1L)).thenReturn(Optional.of(message));
        when(repository.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.markSent(1L, new SmsRecipientResult(
                "+256772123456", 101, "Success", "ATXid_1", "UGX 30",
                true, false, null));

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.DELIVERED);
        assertThat(message.getProviderMessageId()).isEqualTo("ATXid_1");
    }

    @Test
    void manualRetryClearsPreviousProviderState() {
        SmsMessage message = message(SmsMessage.SmsStatus.FAILED, 5);
        message.setProviderMessageId("ATXid_old");
        message.setProviderStatus("Failed");
        message.setProviderCost("UGX 30");
        message.setSentAt(LocalDateTime.now().minusMinutes(5));
        message.setDeliveredAt(LocalDateTime.now().minusMinutes(4));
        when(repository.findById(1L)).thenReturn(Optional.of(message));
        when(repository.save(any(SmsMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.markRetried(1L);

        assertThat(message.getStatus()).isEqualTo(SmsMessage.SmsStatus.QUEUED);
        assertThat(message.getAttempts()).isZero();
        assertThat(message.getProviderMessageId()).isNull();
        assertThat(message.getProviderStatus()).isNull();
        assertThat(message.getProviderCost()).isNull();
        assertThat(message.getSentAt()).isNull();
        assertThat(message.getDeliveredAt()).isNull();
    }

    @Test
    void backoffIsCappedAtConfiguredMaximum() {
        assertThat(writer.backoffMillis(1)).isBetween(24_000L, 36_000L);
        assertThat(writer.backoffMillis(20)).isLessThanOrEqualTo(3_600_000L);
    }

    private SmsMessage message(SmsMessage.SmsStatus status, int attempts) {
        return SmsMessage.builder()
                .messageId(1L)
                .reference("ref-1")
                .recipient("+256772123456")
                .body("Hello")
                .status(status)
                .attempts(attempts)
                .maxAttempts(5)
                .build();
    }
}
