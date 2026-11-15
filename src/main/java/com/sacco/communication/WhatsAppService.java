package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.communication.dto.WhatsAppBulkSendRequest;
import com.sacco.communication.dto.WhatsAppBulkSendResponse;
import com.sacco.communication.dto.WhatsAppConsentRequest;
import com.sacco.communication.dto.WhatsAppConsentResponse;
import com.sacco.communication.dto.WhatsAppInboundResponse;
import com.sacco.communication.dto.WhatsAppMessageResponse;
import com.sacco.communication.dto.WhatsAppSendRequest;
import com.sacco.communication.dto.WhatsAppSummaryResponse;
import com.sacco.communication.entity.WhatsAppConsent;
import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.entity.WhatsAppTemplate;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.exception.WhatsAppOptOutException;
import com.sacco.communication.exception.WhatsAppProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppValidationException;
import com.sacco.communication.exception.WhatsAppWindowClosedException;
import com.sacco.communication.repository.WhatsAppConsentRepository;
import com.sacco.communication.repository.WhatsAppInboundMessageRepository;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Staff-facing WhatsApp API plus the internal entry points used by automated
 * notifications.
 *
 * <p>Every message is persisted first (outbox pattern) and delivered by the
 * scheduled dispatcher unless {@code immediate=true} is set. Free-form
 * messages are only queued inside Meta's 24-hour customer service window;
 * business-initiated messages use an approved template and a recorded opt-in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private static final Set<WhatsAppMessage.WhatsAppMessageType> MEDIA_TYPES = Set.of(
            WhatsAppMessage.WhatsAppMessageType.IMAGE,
            WhatsAppMessage.WhatsAppMessageType.DOCUMENT,
            WhatsAppMessage.WhatsAppMessageType.AUDIO,
            WhatsAppMessage.WhatsAppMessageType.VIDEO,
            WhatsAppMessage.WhatsAppMessageType.STICKER);

    private final WhatsAppMessageWriter writer;
    private final WhatsAppMessageRepository repository;
    private final WhatsAppInboundMessageRepository inboundMessages;
    private final WhatsAppConsentRepository consents;
    private final WhatsAppTemplateService templates;
    private final PhoneNumberService phoneNumbers;
    private final WhatsAppProperties properties;
    private final WhatsAppDeliveryService deliveryService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    public WhatsAppMessageResponse sendSingle(WhatsAppSendRequest request, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_SEND);
        String recipient = phoneNumbers.normalize(request.getRecipient());
        WhatsAppMessage.WhatsAppMessageType type = typeOrDefault(request.getType());

        WhatsAppMessage message = queueSingle(
                recipient,
                type,
                request.getMessage(),
                request.getTemplateName(),
                request.getTemplateLanguage(),
                request.getTemplateParameters(),
                request.getMediaUrl(),
                request.getMediaId(),
                request.getCaption(),
                request.getContextMessageId(),
                null,
                request.getMemberId(),
                actorId);

        auditService.logEvent(actorId, "WHATSAPP_QUEUED", "WHATSAPP_MESSAGE",
                message.getMessageId(),
                type.name() + " to " + phoneNumbers.mask(recipient));

        if (request.isImmediate()) {
            requireProviderConfigured();
            deliveryService.deliver(message.getMessageId());
            message = reload(message);
            if (message.getStatus() == WhatsAppMessage.WhatsAppStatus.FAILED) {
                throw new WhatsAppException(
                        "WhatsApp message could not be sent: " + message.getErrorMessage(),
                        false);
            }
        }
        return WhatsAppMessageResponse.from(message);
    }

    public WhatsAppBulkSendResponse sendBulk(WhatsAppBulkSendRequest request, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_SEND);
        WhatsAppMessage.WhatsAppMessageType type = typeOrDefault(request.getType());

        List<String> recipients = request.getRecipients().stream()
                .map(phoneNumbers::normalize)
                .distinct()
                .toList();
        int bulkLimit = Math.max(1, properties.getDispatch().getBulkLimit());
        if (recipients.size() > bulkLimit) {
            throw new WhatsAppValidationException(
                    "At most " + bulkLimit + " recipients are allowed per bulk request.");
        }

        WhatsAppTemplate template = null;
        List<String> parameters = List.of();
        if (type == WhatsAppMessage.WhatsAppMessageType.TEMPLATE) {
            template = templates.requireApproved(
                    request.getTemplateName(), request.getTemplateLanguage());
            parameters = validateParameters(request.getTemplateParameters(), template);
        } else if (type == WhatsAppMessage.WhatsAppMessageType.TEXT) {
            validateBody(request.getMessage());
        } else if (MEDIA_TYPES.contains(type)) {
            validateMedia(type, request.getMediaUrl(), request.getMediaId(), request.getCaption());
        } else {
            throw new WhatsAppValidationException(
                    "Unsupported WhatsApp message type: " + type);
        }

        String batchId = UUID.randomUUID().toString();
        List<WhatsAppMessage> queued = new ArrayList<>();
        int skippedOptOut = 0;
        int skippedNoConsent = 0;
        int skippedWindowClosed = 0;

        for (String recipient : recipients) {
            Optional<WhatsAppConsent> consent = consents.findByPhoneNumber(recipient);

            if (isOptedOut(consent)) {
                skippedOptOut++;
                continue;
            }

            if (type == WhatsAppMessage.WhatsAppMessageType.TEMPLATE) {
                if (!hasTemplateConsent(consent)) {
                    skippedNoConsent++;
                    continue;
                }
                queued.add(writer.createTemplate(
                        recipient,
                        template.getName(),
                        template.getLanguage(),
                        parameters,
                        batchId,
                        null,
                        actorId));
                continue;
            }

            if (!isWindowOpen(consent)) {
                skippedWindowClosed++;
                continue;
            }

            if (type == WhatsAppMessage.WhatsAppMessageType.TEXT) {
                queued.add(writer.createText(
                        recipient,
                        request.getMessage().trim(),
                        null,
                        batchId,
                        null,
                        actorId));
            } else {
                queued.add(writer.createMedia(
                        recipient,
                        type,
                        trim(request.getMediaUrl()),
                        trim(request.getMediaId()),
                        trim(request.getCaption()),
                        null,
                        batchId,
                        null,
                        actorId));
            }
        }

        if (queued.isEmpty()) {
            throw emptyBatchException(type, skippedOptOut, skippedNoConsent,
                    skippedWindowClosed);
        }

        auditService.logEvent(actorId, "WHATSAPP_BULK_QUEUED", "WHATSAPP_MESSAGE", 0L,
                "Batch " + batchId + ": " + queued.size() + " queued"
                        + (skippedOptOut > 0 ? ", " + skippedOptOut + " opted out" : "")
                        + (skippedNoConsent > 0 ? ", " + skippedNoConsent + " no consent" : "")
                        + (skippedWindowClosed > 0
                        ? ", " + skippedWindowClosed + " window closed" : ""));

        if (request.isImmediate()) {
            requireProviderConfigured();
            for (WhatsAppMessage message : queued) {
                deliveryService.deliver(message.getMessageId());
            }
            List<Long> ids = queued.stream().map(WhatsAppMessage::getMessageId).toList();
            queued = repository.findAllById(ids);
        }

        return new WhatsAppBulkSendResponse(
                batchId,
                queued.size(),
                skippedOptOut,
                skippedNoConsent,
                skippedWindowClosed,
                queued.stream().map(WhatsAppMessageResponse::from).toList());
    }

    /**
     * Internal queue-only template send for automated notifications. The caller
     * is responsible for the business rule; WhatsApp policy (opt-in, approved
     * template) is still enforced here.
     */
    public WhatsAppMessageResponse sendTemplateInternal(String recipient,
                                                        String templateName,
                                                        String language,
                                                        List<String> parameters,
                                                        Long memberId,
                                                        Long createdBy) {
        String phone = phoneNumbers.normalize(recipient);
        ensureNotOptedOut(phone);
        ensureTemplateConsent(phone);

        WhatsAppTemplate template = templates.requireApproved(templateName, language);
        List<String> values = validateParameters(parameters, template);

        WhatsAppMessage message = writer.createTemplate(
                phone,
                template.getName(),
                template.getLanguage(),
                values,
                null,
                memberId,
                createdBy);
        auditService.logEvent(createdBy, "WHATSAPP_QUEUED", "WHATSAPP_MESSAGE",
                message.getMessageId(),
                "TEMPLATE " + template.getName() + " to " + phoneNumbers.mask(phone));
        return WhatsAppMessageResponse.from(message);
    }

    /**
     * Internal queue-only free-form send. Only valid inside the 24-hour window,
     * which is exactly when a conversational reply is allowed.
     */
    public WhatsAppMessageResponse sendTextInternal(String recipient,
                                                    String body,
                                                    String contextMessageId,
                                                    Long memberId,
                                                    Long createdBy) {
        String phone = phoneNumbers.normalize(recipient);
        ensureNotOptedOut(phone);
        ensureWindowOpen(phone);
        String messageBody = validateBody(body);

        WhatsAppMessage message = writer.createText(
                phone,
                messageBody,
                trim(contextMessageId),
                null,
                memberId,
                createdBy);
        auditService.logEvent(createdBy, "WHATSAPP_QUEUED", "WHATSAPP_MESSAGE",
                message.getMessageId(),
                "TEXT to " + phoneNumbers.mask(phone));
        return WhatsAppMessageResponse.from(message);
    }

    public WhatsAppMessageResponse retry(Long messageId, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_SEND);
        WhatsAppMessage message = repository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WhatsApp message not found: " + messageId));
        if (message.getStatus() != WhatsAppMessage.WhatsAppStatus.FAILED) {
            throw new IllegalStateException("Only failed WhatsApp messages can be retried.");
        }
        WhatsAppMessage retried = writer.markRetried(messageId);
        auditService.logEvent(actorId, "WHATSAPP_RETRIED", "WHATSAPP_MESSAGE", messageId,
                "Manual retry of " + phoneNumbers.mask(retried.getRecipient()));
        return WhatsAppMessageResponse.from(retried);
    }

    public Page<WhatsAppMessageResponse> search(WhatsAppMessage.WhatsAppStatus status,
                                                String recipient,
                                                int page,
                                                int size,
                                                Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100));

        String filter = recipient == null ? null : recipient.trim();
        Page<WhatsAppMessage> result;
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
        return result.map(WhatsAppMessageResponse::from);
    }

    public WhatsAppMessageResponse get(Long messageId, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        return repository.findById(messageId)
                .map(WhatsAppMessageResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WhatsApp message not found: " + messageId));
    }

    public WhatsAppSummaryResponse summary(Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        return new WhatsAppSummaryResponse(
                repository.count(),
                repository.countByStatus(WhatsAppMessage.WhatsAppStatus.QUEUED),
                repository.countByStatus(WhatsAppMessage.WhatsAppStatus.SENDING),
                repository.countByStatus(WhatsAppMessage.WhatsAppStatus.SENT),
                repository.countByStatus(WhatsAppMessage.WhatsAppStatus.DELIVERED),
                repository.countByStatus(WhatsAppMessage.WhatsAppStatus.READ),
                repository.countByStatus(WhatsAppMessage.WhatsAppStatus.FAILED),
                consents.countByStatus(WhatsAppConsent.ConsentStatus.OPT_IN),
                consents.countByStatus(WhatsAppConsent.ConsentStatus.OPT_OUT),
                inboundMessages.count());
    }

    public Page<WhatsAppInboundResponse> incoming(int page, int size, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        return inboundMessages.findAllByOrderByInboundIdDesc(pageRequest(page, size))
                .map(WhatsAppInboundResponse::from);
    }

    public Page<WhatsAppConsentResponse> consents(int page, int size, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        return consents.findAllByOrderByConsentIdDesc(pageRequest(page, size))
                .map(this::consentResponse);
    }

    public WhatsAppConsentResponse consent(String phone, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_VIEW);
        String normalized = phoneNumbers.normalize(phone);
        return consents.findByPhoneNumber(normalized)
                .map(this::consentResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No WhatsApp consent record for " + phoneNumbers.mask(normalized)));
    }

    @Transactional
    public WhatsAppConsentResponse setConsent(WhatsAppConsentRequest request, Long actorId) {
        permissionService.require(actorId, Permission.WHATSAPP_MANAGE);
        String phone = phoneNumbers.normalize(request.getPhone());
        LocalDateTime now = LocalDateTime.now();

        WhatsAppConsent consent = consents.findByPhoneNumber(phone)
                .orElseGet(() -> WhatsAppConsent.builder()
                        .phoneNumber(phone)
                        .status(WhatsAppConsent.ConsentStatus.OPT_IN)
                        .build());

        consent.setStatus(request.getStatus());
        consent.setSource(trim(request.getSource()) == null
                ? "STAFF" : trim(request.getSource()));
        if (request.getMemberId() != null) {
            consent.setMemberId(request.getMemberId());
        }
        if (request.getStatus() == WhatsAppConsent.ConsentStatus.OPT_OUT) {
            consent.setOptedOutAt(now);
        } else {
            consent.setOptedInAt(now);
            consent.setOptedOutAt(null);
        }

        WhatsAppConsent saved = consents.save(consent);
        auditService.logEvent(actorId, "WHATSAPP_CONSENT_UPDATED",
                "WHATSAPP_CONSENT", saved.getConsentId(),
                request.getStatus() + " for " + phoneNumbers.mask(phone));
        return consentResponse(saved);
    }

    private WhatsAppMessage queueSingle(String recipient,
                                        WhatsAppMessage.WhatsAppMessageType type,
                                        String body,
                                        String templateName,
                                        String templateLanguage,
                                        List<String> templateParameters,
                                        String mediaUrl,
                                        String mediaId,
                                        String caption,
                                        String contextMessageId,
                                        String batchId,
                                        Long memberId,
                                        Long createdBy) {
        ensureNotOptedOut(recipient);

        if (type == WhatsAppMessage.WhatsAppMessageType.TEMPLATE) {
            ensureTemplateConsent(recipient);
            WhatsAppTemplate template = templates.requireApproved(
                    templateName, templateLanguage);
            List<String> parameters = validateParameters(templateParameters, template);
            return writer.createTemplate(
                    recipient,
                    template.getName(),
                    template.getLanguage(),
                    parameters,
                    batchId,
                    memberId,
                    createdBy);
        }

        ensureWindowOpen(recipient);

        if (type == WhatsAppMessage.WhatsAppMessageType.TEXT) {
            return writer.createText(
                    recipient,
                    validateBody(body),
                    trim(contextMessageId),
                    batchId,
                    memberId,
                    createdBy);
        }

        if (MEDIA_TYPES.contains(type)) {
            validateMedia(type, mediaUrl, mediaId, caption);
            return writer.createMedia(
                    recipient,
                    type,
                    trim(mediaUrl),
                    trim(mediaId),
                    trim(caption),
                    trim(contextMessageId),
                    batchId,
                    memberId,
                    createdBy);
        }

        throw new WhatsAppValidationException(
                "Unsupported WhatsApp message type: " + type);
    }

    private void ensureNotOptedOut(String recipient) {
        if (!properties.isBlockOptedOut()) {
            return;
        }
        if (isOptedOut(consents.findByPhoneNumber(recipient))) {
            throw new WhatsAppOptOutException(
                    "This phone number has opted out of WhatsApp messages. "
                            + "Reply START on WhatsApp to opt back in.");
        }
    }

    private void ensureTemplateConsent(String recipient) {
        if (!properties.isRequireOptInForTemplates()) {
            return;
        }
        Optional<WhatsAppConsent> consent = consents.findByPhoneNumber(recipient);
        if (consent.isEmpty() || !consent.get().isOptedIn()) {
            throw new WhatsAppOptOutException(
                    "This phone number has not opted in to WhatsApp messages. "
                            + "Record consent before sending a template.");
        }
    }

    private void ensureWindowOpen(String recipient) {
        Optional<WhatsAppConsent> consent = consents.findByPhoneNumber(recipient);
        if (!isWindowOpen(consent)) {
            throw new WhatsAppWindowClosedException(
                    "The 24-hour WhatsApp customer service window is closed. "
                            + "Use an approved template for a business-initiated message.");
        }
    }

    private boolean isOptedOut(Optional<WhatsAppConsent> consent) {
        return consent.map(value -> value.getStatus() == WhatsAppConsent.ConsentStatus.OPT_OUT)
                .orElse(false);
    }

    private boolean hasTemplateConsent(Optional<WhatsAppConsent> consent) {
        return !properties.isRequireOptInForTemplates()
                || consent.map(WhatsAppConsent::isOptedIn).orElse(false);
    }

    private boolean isWindowOpen(Optional<WhatsAppConsent> consent) {
        if (consent.isEmpty() || consent.get().getLastInboundAt() == null) {
            return false;
        }
        LocalDateTime closesAt = consent.get().getLastInboundAt()
                .plusHours(Math.max(1, properties.getCustomerServiceWindowHours()));
        return closesAt.isAfter(LocalDateTime.now());
    }

    private WhatsAppConsentResponse consentResponse(WhatsAppConsent consent) {
        boolean open = consent.getLastInboundAt() != null
                && consent.getLastInboundAt()
                .plusHours(Math.max(1, properties.getCustomerServiceWindowHours()))
                .isAfter(LocalDateTime.now());
        LocalDateTime closesAt = consent.getLastInboundAt() == null
                ? null
                : consent.getLastInboundAt()
                .plusHours(Math.max(1, properties.getCustomerServiceWindowHours()));
        return WhatsAppConsentResponse.from(consent, open, closesAt);
    }

    private RuntimeException emptyBatchException(WhatsAppMessage.WhatsAppMessageType type,
                                                 int skippedOptOut,
                                                 int skippedNoConsent,
                                                 int skippedWindowClosed) {
        if (skippedOptOut > 0 && skippedNoConsent == 0 && skippedWindowClosed == 0) {
            return new WhatsAppOptOutException(
                    "All recipients have opted out of WhatsApp. No messages were queued.");
        }
        if (type == WhatsAppMessage.WhatsAppMessageType.TEMPLATE && skippedNoConsent > 0) {
            return new WhatsAppOptOutException(
                    "No recipient has opted in to WhatsApp templates. "
                            + "No messages were queued.");
        }
        if (skippedWindowClosed > 0 && skippedOptOut == 0 && skippedNoConsent == 0) {
            return new WhatsAppWindowClosedException(
                    "All recipients are outside the 24-hour WhatsApp customer service "
                            + "window. Use an approved template instead.");
        }
        return new WhatsAppValidationException(
                "No messages were queued. " + skippedOptOut + " opted out, "
                        + skippedNoConsent + " without consent, "
                        + skippedWindowClosed + " outside the 24-hour window.");
    }

    private String validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new WhatsAppValidationException("Message body is required.");
        }
        String trimmed = body.trim();
        if (trimmed.length() > properties.getMaxTextLength()) {
            throw new WhatsAppValidationException(
                    "Message must be " + properties.getMaxTextLength()
                            + " characters or fewer.");
        }
        return trimmed;
    }

    private List<String> validateParameters(List<String> parameters,
                                            WhatsAppTemplate template) {
        List<String> values = parameters == null ? List.of() : parameters;
        if (values.size() != template.getParameterCount()) {
            throw new WhatsAppValidationException(
                    "Template '" + template.getName() + "' expects "
                            + template.getParameterCount() + " body parameter(s) but "
                            + values.size() + " were supplied.");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new WhatsAppValidationException(
                        "Template parameters cannot be blank.");
            }
            if (value.length() > 1024) {
                throw new WhatsAppValidationException(
                        "A template parameter must be 1024 characters or fewer.");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private void validateMedia(WhatsAppMessage.WhatsAppMessageType type,
                               String mediaUrl,
                               String mediaId,
                               String caption) {
        if (!MEDIA_TYPES.contains(type)) {
            throw new WhatsAppValidationException(
                    "Unsupported WhatsApp media type: " + type);
        }
        boolean hasUrl = mediaUrl != null && !mediaUrl.isBlank();
        boolean hasId = mediaId != null && !mediaId.isBlank();
        if (!hasUrl && !hasId) {
            throw new WhatsAppValidationException(
                    "A media URL or media ID is required.");
        }
        if (hasUrl && mediaUrl.trim().length() > 2048) {
            throw new WhatsAppValidationException("mediaUrl is too long.");
        }
        if (caption != null && caption.trim().length() > 1024) {
            throw new WhatsAppValidationException("caption is too long.");
        }
    }

    private void requireProviderConfigured() {
        if (!properties.isConfigured()) {
            throw new WhatsAppProviderUnavailableException(
                    "WhatsApp is not configured. Set WA_ENABLED, "
                            + "WA_PHONE_NUMBER_ID and WA_ACCESS_TOKEN.");
        }
    }

    private WhatsAppMessage reload(WhatsAppMessage message) {
        return repository.findById(message.getMessageId()).orElse(message);
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
    }

    private WhatsAppMessage.WhatsAppMessageType typeOrDefault(
            WhatsAppMessage.WhatsAppMessageType type) {
        return type == null ? WhatsAppMessage.WhatsAppMessageType.TEXT : type;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
