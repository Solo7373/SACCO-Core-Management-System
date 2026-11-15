package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.communication.dto.IncomingSmsResponse;
import com.sacco.communication.dto.SmsBulkSendRequest;
import com.sacco.communication.dto.SmsMessageResponse;
import com.sacco.communication.dto.SmsSendRequest;
import com.sacco.communication.dto.SmsSummaryResponse;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.exception.AfricasTalkingException;
import com.sacco.communication.exception.SmsOptOutException;
import com.sacco.communication.exception.SmsProviderUnavailableException;
import com.sacco.communication.exception.SmsValidationException;
import com.sacco.communication.repository.IncomingSmsRepository;
import com.sacco.communication.repository.SmsMessageRepository;
import com.sacco.communication.repository.SmsOptOutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Staff-facing SMS API. All sends are persisted first (outbox pattern) and
 * delivered asynchronously unless {@code immediate=true} is explicitly set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsMessageWriter writer;
    private final SmsMessageRepository repository;
    private final IncomingSmsRepository incomingSmsRepository;
    private final SmsOptOutRepository optOuts;
    private final PhoneNumberService phoneNumbers;
    private final AfricasTalkingProperties properties;
    private final SmsDeliveryService deliveryService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    public SmsMessageResponse sendSingle(SmsSendRequest request, Long actorId) {
        permissionService.require(actorId, Permission.SMS_SEND);
        rejectOtpType(request.getType());

        String recipient = phoneNumbers.normalize(request.getRecipient());
        ensureAllowed(recipient);
        String body = validateBody(request.getMessage());
        SmsMessage.SmsMessageType type = typeOrDefault(request.getType());

        SmsMessage message = writer.create(
                recipient,
                body,
                type,
                resolveSender(request.getSenderId()),
                null,
                request.getMemberId(),
                actorId);

        auditService.logEvent(actorId, "SMS_QUEUED", "SMS_MESSAGE", message.getMessageId(),
                type.name() + " to " + phoneNumbers.mask(recipient));

        if (request.isImmediate()) {
            requireProviderConfigured();
            deliveryService.deliver(message.getMessageId());
            message = repository.findById(message.getMessageId()).orElse(message);
        }
        return SmsMessageResponse.from(message);
    }

    public List<SmsMessageResponse> sendBulk(SmsBulkSendRequest request, Long actorId) {
        permissionService.require(actorId, Permission.SMS_SEND);
        rejectOtpType(request.getType());

        List<String> normalized = request.getRecipients().stream()
                .map(phoneNumbers::normalize)
                .distinct()
                .toList();
        if (normalized.size() > properties.getDispatch().getBulkLimit()) {
            throw new SmsValidationException(
                    "At most " + properties.getDispatch().getBulkLimit()
                            + " recipients are allowed per bulk request.");
        }

        List<String> allowed = new ArrayList<>();
        int skipped = 0;
        for (String recipient : normalized) {
            if (properties.isBlockOptedOut()
                    && optOuts.existsByPhoneNumberAndActiveTrue(recipient)) {
                skipped++;
            } else {
                allowed.add(recipient);
            }
        }
        if (allowed.isEmpty()) {
            throw new SmsOptOutException(
                    "All recipients have opted out of SMS. No messages were queued.");
        }

        String body = validateBody(request.getMessage());
        SmsMessage.SmsMessageType type = typeOrDefault(request.getType());
        String sender = resolveSender(request.getSenderId());
        String batchId = UUID.randomUUID().toString();

        List<SmsMessage> messages = writer.createBatch(
                allowed, body, type, sender, batchId, actorId);

        auditService.logEvent(actorId, "SMS_BULK_QUEUED", "SMS_MESSAGE", 0L,
                "Batch " + batchId + ": " + messages.size() + " messages"
                        + (skipped > 0 ? ", " + skipped + " opted out" : ""));

        if (request.isImmediate()) {
            requireProviderConfigured();
            List<Long> ids = messages.stream().map(SmsMessage::getMessageId).toList();
            deliveryService.deliverBulk(ids);
            messages = repository.findAllById(ids);
        }
        return messages.stream().map(SmsMessageResponse::from).toList();
    }

    /**
     * Internal immediate send used by {@link OtpService}. It deliberately does
     * not perform a staff permission check: the OTP flow controls who may call
     * it. It still honours opt-outs and provider configuration.
     */
    public SmsMessageResponse sendImmediate(String recipient,
                                            String body,
                                            SmsMessage.SmsMessageType type,
                                            String senderId,
                                            Long memberId,
                                            Long createdBy) {
        String normalized = phoneNumbers.normalize(recipient);
        ensureAllowed(normalized);
        String messageBody = validateBody(body);
        requireProviderConfigured();

        SmsMessage message = writer.create(
                normalized,
                messageBody,
                typeOrDefault(type),
                resolveSender(senderId),
                null,
                memberId,
                createdBy);

        deliveryService.deliver(message.getMessageId());
        SmsMessage updated = repository.findById(message.getMessageId()).orElse(message);
        if (updated.getStatus() == SmsMessage.SmsStatus.FAILED) {
            throw new AfricasTalkingException(
                    "SMS could not be sent: " + updated.getFailureReason(), false);
        }
        return SmsMessageResponse.from(updated);
    }

    public SmsMessageResponse retry(Long messageId, Long actorId) {
        permissionService.require(actorId, Permission.SMS_SEND);
        SmsMessage message = repository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SMS message not found: " + messageId));
        if (message.getStatus() != SmsMessage.SmsStatus.FAILED
                && message.getStatus() != SmsMessage.SmsStatus.REJECTED
                && message.getStatus() != SmsMessage.SmsStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Only failed, rejected or expired messages can be retried.");
        }
        SmsMessage retried = writer.markRetried(messageId);
        auditService.logEvent(actorId, "SMS_RETRIED", "SMS_MESSAGE", messageId,
                "Manual retry of " + phoneNumbers.mask(retried.getRecipient()));
        return SmsMessageResponse.from(retried);
    }

    public Page<SmsMessageResponse> search(SmsMessage.SmsStatus status,
                                           String recipient,
                                           int page,
                                           int size,
                                           Long actorId) {
        permissionService.require(actorId, Permission.SMS_VIEW);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100));

        String filter = recipient == null ? null : recipient.trim();
        Page<SmsMessage> result;
        if (status != null && filter != null && !filter.isBlank()) {
            result = repository.findByStatusAndRecipientContainingOrderByMessageIdDesc(
                    status, filter, pageable);
        } else if (status != null) {
            result = repository.findByStatusOrderByMessageIdDesc(status, pageable);
        } else if (filter != null && !filter.isBlank()) {
            result = repository.findByRecipientContainingOrderByMessageIdDesc(filter, pageable);
        } else {
            result = repository.findAllByOrderByMessageIdDesc(pageable);
        }
        return result.map(SmsMessageResponse::from);
    }

    public SmsMessageResponse get(Long messageId, Long actorId) {
        permissionService.require(actorId, Permission.SMS_VIEW);
        return repository.findById(messageId)
                .map(SmsMessageResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SMS message not found: " + messageId));
    }

    public SmsSummaryResponse summary(Long actorId) {
        permissionService.require(actorId, Permission.SMS_VIEW);
        long queued = repository.countByStatus(SmsMessage.SmsStatus.QUEUED);
        long sending = repository.countByStatus(SmsMessage.SmsStatus.SENDING);
        long sent = repository.countByStatus(SmsMessage.SmsStatus.SENT);
        long delivered = repository.countByStatus(SmsMessage.SmsStatus.DELIVERED);
        long failed = repository.countByStatus(SmsMessage.SmsStatus.FAILED);
        long rejected = repository.countByStatus(SmsMessage.SmsStatus.REJECTED);
        long expired = repository.countByStatus(SmsMessage.SmsStatus.EXPIRED);
        return new SmsSummaryResponse(
                repository.count(), queued, sending, sent, delivered,
                failed, rejected, expired);
    }

    public Page<IncomingSmsResponse> incoming(int page, int size, Long actorId) {
        permissionService.require(actorId, Permission.SMS_VIEW);
        return incomingSmsRepository.findAllByOrderByIncomingIdDesc(
                        PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))
                .map(IncomingSmsResponse::from);
    }

    private void ensureAllowed(String recipient) {
        if (properties.isBlockOptedOut()
                && optOuts.existsByPhoneNumberAndActiveTrue(recipient)) {
            throw new SmsOptOutException(
                    "This phone number has opted out of SMS. Reply START to opt back in.");
        }
    }

    private void requireProviderConfigured() {
        if (!properties.isConfigured()) {
            throw new SmsProviderUnavailableException(
                    "SMS provider is not configured. Set AT_ENABLED, AT_USERNAME and AT_API_KEY.");
        }
    }

    private String validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new SmsValidationException("Message body is required.");
        }
        String trimmed = body.trim();
        if (trimmed.length() > properties.getMaxMessageLength()) {
            throw new SmsValidationException(
                    "Message must be " + properties.getMaxMessageLength()
                            + " characters or fewer.");
        }
        return trimmed;
    }

    private String resolveSender(String requested) {
        String sender = requested == null || requested.isBlank()
                ? properties.getSenderId()
                : requested.trim();
        return sender == null || sender.isBlank() ? null : sender;
    }

    private SmsMessage.SmsMessageType typeOrDefault(SmsMessage.SmsMessageType type) {
        return type == null ? SmsMessage.SmsMessageType.NOTIFICATION : type;
    }

    private void rejectOtpType(SmsMessage.SmsMessageType type) {
        if (type == SmsMessage.SmsMessageType.OTP) {
            throw new SmsValidationException(
                    "OTP messages must be generated by the OTP service.");
        }
    }
}
