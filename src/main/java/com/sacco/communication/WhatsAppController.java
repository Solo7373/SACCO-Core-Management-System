package com.sacco.communication;

import com.sacco.communication.dto.WhatsAppBulkSendRequest;
import com.sacco.communication.dto.WhatsAppBulkSendResponse;
import com.sacco.communication.dto.WhatsAppConsentRequest;
import com.sacco.communication.dto.WhatsAppConsentResponse;
import com.sacco.communication.dto.WhatsAppInboundResponse;
import com.sacco.communication.dto.WhatsAppMessageResponse;
import com.sacco.communication.dto.WhatsAppSendRequest;
import com.sacco.communication.dto.WhatsAppSummaryResponse;
import com.sacco.communication.dto.WhatsAppTemplateRequest;
import com.sacco.communication.dto.WhatsAppTemplateResponse;
import com.sacco.communication.dto.WhatsAppTemplateSyncResponse;
import com.sacco.communication.entity.WhatsAppMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff WhatsApp workspace. Sending is permission-gated by
 * {@link WhatsAppService}; the {@code actorId} parameter mirrors the rest of
 * the application's controllers, but when a JWT is present
 * {@link com.sacco.auth.PermissionService} uses the authenticated user and
 * ignores the supplied value.
 */
@RestController
@RequestMapping("/api/communications/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService whatsAppService;
    private final WhatsAppTemplateService templateService;

    @PostMapping("/send")
    public ResponseEntity<WhatsAppMessageResponse> send(
            @Valid @RequestBody WhatsAppSendRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.sendSingle(request, actorId));
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<WhatsAppBulkSendResponse> sendBulk(
            @Valid @RequestBody WhatsAppBulkSendRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.sendBulk(request, actorId));
    }

    @PostMapping("/messages/{messageId}/retry")
    public ResponseEntity<WhatsAppMessageResponse> retry(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.retry(messageId, actorId));
    }

    @GetMapping("/messages")
    public ResponseEntity<Page<WhatsAppMessageResponse>> messages(
            @RequestParam(required = false) WhatsAppMessage.WhatsAppStatus status,
            @RequestParam(required = false) String recipient,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(
                whatsAppService.search(status, recipient, page, size, actorId));
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<WhatsAppMessageResponse> message(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.get(messageId, actorId));
    }

    @GetMapping("/summary")
    public ResponseEntity<WhatsAppSummaryResponse> summary(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.summary(actorId));
    }

    @GetMapping("/incoming")
    public ResponseEntity<Page<WhatsAppInboundResponse>> incoming(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.incoming(page, size, actorId));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<WhatsAppTemplateResponse>> templates(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(templateService.list(actorId));
    }

    @PostMapping("/templates/sync")
    public ResponseEntity<WhatsAppTemplateSyncResponse> syncTemplates(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(templateService.sync(actorId));
    }

    @PostMapping("/templates")
    public ResponseEntity<WhatsAppTemplateResponse> registerTemplate(
            @Valid @RequestBody WhatsAppTemplateRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(templateService.upsert(request, actorId));
    }

    @GetMapping("/consents")
    public ResponseEntity<Page<WhatsAppConsentResponse>> consents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.consents(page, size, actorId));
    }

    @GetMapping("/consents/{phone}")
    public ResponseEntity<WhatsAppConsentResponse> consent(
            @PathVariable String phone,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.consent(phone, actorId));
    }

    @PostMapping("/consents")
    public ResponseEntity<WhatsAppConsentResponse> setConsent(
            @Valid @RequestBody WhatsAppConsentRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(whatsAppService.setConsent(request, actorId));
    }
}
