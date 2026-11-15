package com.sacco.communication;

import com.sacco.communication.exception.WebhookAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsWebhookSecurityTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private AfricasTalkingProperties properties;
    private SmsWebhookSecurity security;

    @BeforeEach
    void setUp() {
        properties = new AfricasTalkingProperties();
        properties.setWebhookToken(TOKEN);
        security = new SmsWebhookSecurity(properties, new ClientIpResolver(properties));
    }

    @Test
    void acceptsCorrectToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatCode(() -> security.verify(TOKEN, request))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsIncorrectToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> security.verify("wrong-token", request))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void failsClosedWhenTokenIsNotConfigured() {
        properties.setWebhookToken("");
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> security.verify("anything", request))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void acceptsClientInsideAllowlistedCidr() {
        properties.setWebhookAllowedIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.24.5.9");

        assertThatCode(() -> security.verify(TOKEN, request))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsClientOutsideAllowlistedCidr() {
        properties.setWebhookAllowedIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");

        assertThatThrownBy(() -> security.verify(TOKEN, request))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void doesNotTrustForwardedHeaderByDefault() {
        properties.setWebhookAllowedIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");
        request.addHeader("X-Forwarded-For", "10.24.5.9");

        assertThatThrownBy(() -> security.verify(TOKEN, request))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void trustsForwardedHeaderWhenExplicitlyEnabled() {
        properties.setWebhookAllowedIps(List.of("10.0.0.0/8"));
        properties.setWebhookTrustForwardedHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");
        request.addHeader("X-Forwarded-For", "10.24.5.9, 192.168.1.20");

        assertThatCode(() -> security.verify(TOKEN, request))
                .doesNotThrowAnyException();
    }
}
