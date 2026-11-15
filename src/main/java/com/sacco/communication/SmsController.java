package com.sacco.communication;

import com.sacco.communication.dto.IncomingSmsResponse;
import com.sacco.communication.dto.SmsBulkSendRequest;
import com.sacco.communication.dto.SmsMessageResponse;
import com.sacco.communication.dto.SmsSendRequest;
import com.sacco.communication.dto.SmsSummaryResponse;
import com.sacco.communication.entity.SmsMessage;
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
 * Staff SMS workspace. Sending is permission-gated by {@link SmsService};
 * the {@code actorId} parameter mirrors the rest of the application's
 * controllers, but when a JWT is present {@link com.sacco.auth.PermissionService}
 * uses the authenticated user and ignores the supplied value.
 */
@RestController
@RequestMapping("/api/communications/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;

    @PostMapping("/send")
    public ResponseEntity<SmsMessageResponse> send(
            @Valid @RequestBody SmsSendRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.sendSingle(request, actorId));
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<List<SmsMessageResponse>> sendBulk(
            @Valid @RequestBody SmsBulkSendRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.sendBulk(request, actorId));
    }

    @PostMapping("/messages/{messageId}/retry")
    public ResponseEntity<SmsMessageResponse> retry(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.retry(messageId, actorId));
    }

    @GetMapping("/messages")
    public ResponseEntity<Page<SmsMessageResponse>> messages(
            @RequestParam(required = false) SmsMessage.SmsStatus status,
            @RequestParam(required = false) String recipient,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.search(status, recipient, page, size, actorId));
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<SmsMessageResponse> message(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.get(messageId, actorId));
    }

    @GetMapping("/summary")
    public ResponseEntity<SmsSummaryResponse> summary(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.summary(actorId));
    }

    @GetMapping("/incoming")
    public ResponseEntity<Page<IncomingSmsResponse>> incoming(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(smsService.incoming(page, size, actorId));
    }
}
