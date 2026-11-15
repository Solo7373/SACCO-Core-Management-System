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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Applies provider callbacks to the outbox and the opt-out list.
 *
 * <p>Delivery reports are stored verbatim first. If the report wins the race
 * against the send call (the outbox row does not yet have a provider message
 * id), it remains {@code processed=false} and the dispatcher reconciles it on
 * a later tick. This makes the webhook idempotent and avoids losing reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsWebhookService {

    private static final Set<String> STOP_KEYWORDS =
            Set.of("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT");
    private static final Set<String> START_KEYWORDS =
            Set.of("START", "UNSTOP", "SUBSCRIBE");

    private final SmsDeliveryReportRepository reports;
    private final IncomingSmsRepository incomingMessages;
    private final SmsMessageRepository messages;
    private final SmsOptOutRepository optOuts;
    private final PhoneNumberService phoneNumbers;
    private final AuditService auditService;

    /**
     * Persists and applies one delivery report. Returns true when an outbox row
     * was found (including a duplicate report for an already-updated row).
     */
    @Transactional
    public boolean processDeliveryReport(SmsWebhookPayload payload) {
        String providerMessageId = payload.require("id", "messageId");
        String status = payload.require("status", "deliveryStatus");
        String phone = storePhone(payload.first("phoneNumber", "number", "to"));

        SmsDeliveryReport report = SmsDeliveryReport.builder()
                .providerMessageId(truncate(providerMessageId, 100))
                .status(truncate(status, 40))
                .phoneNumber(phone)
                .networkCode(truncate(payload.first("networkCode", "network"), 20))
                .failureReason(truncate(payload.first("failureReason", "reason"), 500))
                .retryCount(payload.integer("retryCount", "retry_count"))
                .rawPayload(payload.rawPayload())
                .processed(false)
                .receivedAt(LocalDateTime.now())
                .build();

        report = reports.save(report);
        return applyReport(report);
    }

    /**
     * Reconciles reports that arrived before their outbox row had a provider
     * id. Called by the dispatcher every few seconds. Old unmatched reports are
     * closed out so they cannot accumulate forever.
     */
    @Transactional
    public int reprocessPendingDeliveryReports() {
        LocalDateTime now = LocalDateTime.now();
        List<SmsDeliveryReport> pending = reports
                .findTop100ByProcessedFalseAndReceivedAtAfterOrderByReportIdAsc(
                        now.minusHours(24));

        int matched = 0;
        for (SmsDeliveryReport report : pending) {
            if (report.getProviderMessageId() == null) {
                continue;
            }
            if (applyReport(report)) {
                matched++;
            }
        }

        // A report with no matching outbox row after a day is no longer useful
        // for reconciliation, but it remains in the table for audit purposes.
        reports.markOldReportsProcessed(now.minusHours(24));
        return matched;
    }

    /**
     * Persists an incoming SMS and applies STOP/START keywords. Duplicate
     * callbacks (AT retries) are acknowledged but not processed twice.
     */
    @Transactional
    public boolean processIncomingSms(SmsWebhookPayload payload) {
        String providerMessageId = payload.require("id", "messageId");
        if (incomingMessages.findByProviderMessageId(providerMessageId).isPresent()) {
            return false;
        }

        String sender = storePhone(payload.first("from", "sender", "phoneNumber"));
        String text = payload.first("text", "message", "body");

        IncomingSms incoming = IncomingSms.builder()
                .providerMessageId(truncate(providerMessageId, 100))
                .sender(sender)
                .recipient(truncate(payload.first("to", "recipient"), 30))
                .text(text)
                .linkId(truncate(payload.first("linkId", "link_id"), 100))
                .receivedAt(LocalDateTime.now())
                .rawPayload(payload.rawPayload())
                .processed(false)
                .build();

        incoming = incomingMessages.save(incoming);
        if (text != null) {
            applyKeyword(sender, text);
        }
        incoming.setProcessed(true);
        incomingMessages.save(incoming);
        return true;
    }

    private boolean applyReport(SmsDeliveryReport report) {
        Optional<SmsMessage> found = messages
                .findFirstByProviderMessageIdOrderByMessageIdAsc(report.getProviderMessageId());
        if (found.isEmpty()) {
            // The send call has not stored the provider id yet. Leave the
            // report unprocessed for the dispatcher to retry.
            return false;
        }

        SmsMessage message = found.get();
        DeliveryOutcome outcome = mapStatus(report.getStatus());
        LocalDateTime now = LocalDateTime.now();

        if (message.getStatus() == SmsMessage.SmsStatus.DELIVERED) {
            // Delivery is terminal. Duplicate or late reports are retained for
            // audit but must not move delivered_at or downgrade the status.
            report.setProcessed(true);
            reports.save(report);
            return true;
        }

        message.setProviderStatus(truncate(report.getStatus(), 40));
        if (outcome == DeliveryOutcome.UNKNOWN) {
            log.warn("Unrecognised Africa's Talking delivery status '{}' for message {}",
                    report.getStatus(), message.getMessageId());
        } else {
            applyOutcome(message, outcome, report, now);
        }

        messages.save(message);
        report.setProcessed(true);
        reports.save(report);
        return true;
    }

    private void applyOutcome(SmsMessage message,
                              DeliveryOutcome outcome,
                              SmsDeliveryReport report,
                              LocalDateTime now) {
        SmsMessage.SmsStatus current = message.getStatus();

        // A late "Sent" or "Failed" report must never downgrade a confirmed
        // delivery. A later "Success" report is allowed to upgrade a failure,
        // because the provider may have retried internally.
        if (current == SmsMessage.SmsStatus.DELIVERED
                && outcome != DeliveryOutcome.DELIVERED) {
            return;
        }

        switch (outcome) {
            case DELIVERED -> {
                message.setStatus(SmsMessage.SmsStatus.DELIVERED);
                message.setDeliveredAt(now);
                message.setSentAt(message.getSentAt() == null ? now : message.getSentAt());
                message.setFailureReason(null);
            }
            case SENT -> {
                if (rank(current) < rank(SmsMessage.SmsStatus.SENT)) {
                    message.setStatus(SmsMessage.SmsStatus.SENT);
                }
                message.setSentAt(message.getSentAt() == null ? now : message.getSentAt());
            }
            case FAILED, REJECTED, EXPIRED -> {
                if (rank(current) < rank(SmsMessage.SmsStatus.FAILED)) {
                    message.setStatus(switch (outcome) {
                        case REJECTED -> SmsMessage.SmsStatus.REJECTED;
                        case EXPIRED -> SmsMessage.SmsStatus.EXPIRED;
                        default -> SmsMessage.SmsStatus.FAILED;
                    });
                }
                String reason = report.getFailureReason();
                if (reason == null || reason.isBlank()) {
                    reason = "Provider reported status " + report.getStatus();
                }
                message.setFailureReason(truncate(reason, 500));
            }
            default -> {
                // UNKNOWN is handled by the caller.
            }
        }
    }

    private DeliveryOutcome mapStatus(String rawStatus) {
        if (rawStatus == null) {
            return DeliveryOutcome.UNKNOWN;
        }
        String status = rawStatus.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (status) {
            case "DELIVERED", "SUCCESS", "SUCCESSFUL", "DELIVERED_TO_HANDSET",
                    "ACK", "ACKED", "COMPLETED" -> DeliveryOutcome.DELIVERED;
            case "SENT", "SUBMITTED", "BUFFERED", "QUEUED", "ACCEPTED",
                    "PENDING", "PROCESSING", "IN_PROGRESS", "ENROUTE", "SENDING" ->
                    DeliveryOutcome.SENT;
            case "REJECTED", "DENIED", "BLACKLISTED" -> DeliveryOutcome.REJECTED;
            case "EXPIRED" -> DeliveryOutcome.EXPIRED;
            case "FAILED", "FAILURE", "UNDELIVERABLE", "DISCARDED", "INVALID",
                    "BLOCKED", "NO_ROUTE", "NOT_DELIVERED", "ERROR" ->
                    DeliveryOutcome.FAILED;
            default -> DeliveryOutcome.UNKNOWN;
        };
    }

    private int rank(SmsMessage.SmsStatus status) {
        if (status == null) {
            return -1;
        }
        return switch (status) {
            case QUEUED -> 0;
            case SENDING -> 1;
            case SENT -> 2;
            case FAILED, REJECTED, EXPIRED -> 3;
            case DELIVERED -> 4;
        };
    }

    private void applyKeyword(String sender, String text) {
        if (sender == null) {
            return;
        }

        String normalized = text.trim().toUpperCase(Locale.ROOT);
        String firstToken = normalized.split("\\s+")[0].replaceAll("[^A-Z]", "");
        if (STOP_KEYWORDS.contains(firstToken)) {
            optOut(sender, text);
        } else if (START_KEYWORDS.contains(firstToken)) {
            optIn(sender, text);
        }
    }

    private void optOut(String phone, String text) {
        SmsOptOut optOut = optOuts.findByPhoneNumber(phone)
                .orElseGet(() -> SmsOptOut.builder().phoneNumber(phone).build());
        optOut.setActive(true);
        optOut.setReason(truncate("STOP received: " + text, 200));
        optOut.setSource("SMS");
        SmsOptOut saved = optOuts.save(optOut);
        audit("SMS_OPT_OUT", saved.getOptOutId(), phone);
    }

    private void optIn(String phone, String text) {
        optOuts.findByPhoneNumber(phone).ifPresent(optOut -> {
            optOut.setActive(false);
            optOut.setReason(truncate("START received: " + text, 200));
            optOut.setSource("SMS");
            SmsOptOut saved = optOuts.save(optOut);
            audit("SMS_OPT_IN", saved.getOptOutId(), phone);
        });
    }

    private void audit(String action, Long optOutId, String phone) {
        try {
            auditService.logEvent(0L, action, "SMS_OPT_OUT", optOutId,
                    "Phone " + phoneNumbers.mask(phone));
        } catch (RuntimeException exception) {
            // An audit write failure must not cause AT to retry the callback.
            log.error("Could not write SMS opt-out audit event", exception);
        }
    }

    private String storePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return phoneNumbers.normalize(raw);
        } catch (RuntimeException exception) {
            return truncate(raw.trim(), 20);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private enum DeliveryOutcome {
        DELIVERED,
        SENT,
        FAILED,
        REJECTED,
        EXPIRED,
        UNKNOWN
    }
}
