package com.sacco.communication;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Africa's Talking callback endpoints.
 *
 * <p>The token is part of the path because AT cannot add custom headers:
 * <pre>
 *   POST /api/webhooks/africastalking/{AT_WEBHOOK_TOKEN}/delivery
 *   POST /api/webhooks/africastalking/{AT_WEBHOOK_TOKEN}/incoming
 * </pre>
 *
 * <p>The endpoints are deliberately outside the JWT-protected {@code /api/**}
 * matcher in {@link com.sacco.auth.SecurityConfig}. They are authenticated by
 * {@link SmsWebhookSecurity} instead. AT retries non-2xx responses, so a
 * duplicate callback is acknowledged with 200 after being de-duplicated.
 */
@RestController
@RequestMapping("/api/webhooks/africastalking")
@RequiredArgsConstructor
public class SmsWebhookController {

    private final SmsWebhookSecurity security;
    private final SmsWebhookPayloadParser payloadParser;
    private final SmsWebhookService webhookService;

    @PostMapping({"/{token}/delivery", "/{token}/delivery-report"})
    public ResponseEntity<Void> deliveryReport(@PathVariable String token,
                                               HttpServletRequest request) {
        security.verify(token, request);
        SmsWebhookPayload payload = payloadParser.parse(request);
        webhookService.processDeliveryReport(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping({"/{token}/incoming", "/{token}/incoming-message"})
    public ResponseEntity<Void> incomingMessage(@PathVariable String token,
                                                HttpServletRequest request) {
        security.verify(token, request);
        SmsWebhookPayload payload = payloadParser.parse(request);
        webhookService.processIncomingSms(payload);
        return ResponseEntity.ok().build();
    }
}
