package com.sacco.communication.voice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Africa Africa's Talking voice callback endpoint.
 *
 * <p>Voice call state transitions arrive asynchronously from the provider:
 * <pre>
 *   POST /api/webhooks/africastalking/{AT_WEBHOOK_TOKEN}/voice
 * </pre>
 *
 * <p>Authentication mirrors the SMS delivery/incoming callbacks: a high-entropy
 * token in the path plus an optional source allowlist, with forwarded-header
 * trust disabled unless explicitly enabled. Non-2xx responses make the
 * provider retry, so the endpoint acknowledges duplicates with 200 after
 * persisting the event.
 */
@RestController
@RequestMapping("/api/webhooks/africastalking")
@RequiredArgsConstructor
public class VoiceWebhookController {

    private final VoiceWebhookSecurity security;
    private final VoiceWebhookPayloadParser payloadParser;
    private final VoiceWebhookService webhookService;

    @PostMapping({"/{token}/voice", "/{token}/calls"})
    public ResponseEntity<Void> voice(@PathVariable String token, HttpServletRequest request) {
        security.verify(token, request);
        VoiceWebhookPayload payload = payloadParser.parse(request);
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}
