package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.exception.SmsValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsWebhookPayloadParserTest {

    private SmsWebhookPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new SmsWebhookPayloadParser(new ObjectMapper());
    }

    @Test
    void parsesFormEncodedDeliveryReport() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/x-www-form-urlencoded");
        request.setParameter("id", "ATXid_123");
        request.setParameter("status", "Success");
        request.setParameter("phoneNumber", "+256772123456");

        SmsWebhookPayload payload = parser.parse(request);

        assertThat(payload.first("id")).isEqualTo("ATXid_123");
        assertThat(payload.first("status")).isEqualTo("Success");
        assertThat(payload.rawPayload()).contains("ATXid_123");
    }

    @Test
    void parsesJsonIncomingMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json; charset=UTF-8");
        request.setContent("""
                {"id":"ATXid_456","from":"+256772123456","to":"12345","text":"STOP"}
                """.getBytes(StandardCharsets.UTF_8));

        SmsWebhookPayload payload = parser.parse(request);

        assertThat(payload.first("id")).isEqualTo("ATXid_456");
        assertThat(payload.first("from")).isEqualTo("+256772123456");
        assertThat(payload.first("text")).isEqualTo("STOP");
    }

    @Test
    void rejectsEmptyPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/x-www-form-urlencoded");

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(SmsValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsMalformedJson() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent("{broken".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(SmsValidationException.class)
                .hasMessageContaining("valid JSON");
    }
}
