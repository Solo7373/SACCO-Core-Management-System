package com.sacco.communication;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta WhatsApp Cloud API callback endpoints.
 *
 * <p>Meta calls a single URL for both the verification handshake and event
 * delivery:
 * <pre>
 *   GET  /api/webhooks/whatsapp/{WA_WEBHOOK_TOKEN}
 *   POST /api/webhooks/whatsapp/{WA_WEBHOOK_TOKEN}
 * </pre>
 *
 * <p>The endpoints are deliberately outside the JWT-protected {@code /api/**}
 * matcher in {@link com.sacco.auth.SecurityConfig}. They are authenticated by
 * {@link WhatsAppWebhookSecurity}: a constant-time path token plus Meta's
 * {@code X-Hub-Signature-256} HMAC of the raw body. When any secret is missing
 * the endpoint fails closed. Meta retries non-2xx responses, so duplicates are
 * acknowledged with 200 after being de-duplicated.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final WhatsAppWebhookSecurity security;
    private final WhatsAppWebhookPayloadParser payloadParser;
    private final WhatsAppWebhookService webhookService;

    /** Meta verification handshake. The challenge must be echoed as plain text. */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @PathVariable String token,
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        security.verifyPathToken(token);
        String response = security.verifyChallenge(mode, verifyToken, challenge);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(response);
    }

    @PostMapping("/{token}")
    public ResponseEntity<Void> receive(
            @PathVariable String token,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        security.verifyPathToken(token);
        security.verifySignature(rawBody, signature);
        WhatsAppWebhookPayload payload = payloadParser.parse(rawBody);
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}
