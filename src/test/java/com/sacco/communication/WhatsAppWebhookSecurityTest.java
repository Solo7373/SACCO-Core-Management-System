package com.sacco.communication;

import com.sacco.communication.exception.WebhookAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsAppWebhookSecurityTest {

    private static final String BODY =
            "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

    private WhatsAppProperties properties;
    private WhatsAppWebhookSecurity security;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setAppSecret("app-secret");
        properties.setVerifyToken("verify-token");
        properties.setWebhookToken("path-token");
        security = new WhatsAppWebhookSecurity(properties);
    }

    @Test
    void acceptsCorrectPathToken() {
        assertThatCode(() -> security.verifyPathToken("path-token"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongPathToken() {
        assertThatThrownBy(() -> security.verifyPathToken("wrong"))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void acceptsValidHubSignature() throws Exception {
        String signature = "sha256=" + hmac("app-secret", BODY);

        assertThatCode(() -> security.verifySignature(BODY, signature))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String signature = "sha256=" + hmac("app-secret", BODY);

        assertThatThrownBy(() -> security.verifySignature(BODY + " ", signature))
                .isInstanceOf(WebhookAuthenticationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsMissingSignature() {
        assertThatThrownBy(() -> security.verifySignature(BODY, null))
                .isInstanceOf(WebhookAuthenticationException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    void failsClosedWhenSecretsAreNotConfigured() {
        WhatsAppProperties unconfigured = new WhatsAppProperties();
        WhatsAppWebhookSecurity closed = new WhatsAppWebhookSecurity(unconfigured);

        assertThatThrownBy(() -> closed.verifyPathToken("anything"))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThatThrownBy(() -> closed.verifySignature(BODY, "sha256=abc"))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void echoesChallengeOnlyForCorrectVerifyToken() {
        assertThat(security.verifyChallenge("subscribe", "verify-token", "12345"))
                .isEqualTo("12345");

        assertThatThrownBy(() ->
                security.verifyChallenge("subscribe", "wrong", "12345"))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThatThrownBy(() ->
                security.verifyChallenge("unsubscribe", "verify-token", "12345"))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThatThrownBy(() ->
                security.verifyChallenge("subscribe", "verify-token", " "))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    private String hmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
