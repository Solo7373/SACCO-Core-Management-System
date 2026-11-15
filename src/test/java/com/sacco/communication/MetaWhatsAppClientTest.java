package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.MetaSendResult;
import com.sacco.communication.dto.MetaTemplateDefinition;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.exception.WhatsAppProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MetaWhatsAppClientTest {

    private WhatsAppProperties properties;
    private MockRestServiceServer server;
    private MetaWhatsAppClient client;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setPhoneNumberId("12345");
        properties.setWabaId("waba-1");
        properties.setAccessToken("test-token");
        properties.setGraphBaseUrl("https://graph.test");
        properties.setGraphVersion("v23.0");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MetaWhatsAppClient(builder.build(), properties, new ObjectMapper());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void sendsTextWithBearerTokenAndOpaqueReference() {
        server.expect(requestTo("https://graph.test/v23.0/12345/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("""
                        {
                          "messaging_product": "whatsapp",
                          "recipient_type": "individual",
                          "to": "256772123456",
                          "biz_opaque_callback_data": "ref-1",
                          "type": "text",
                          "text": {"preview_url": false, "body": "Hello"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "messages": [{"id": "wamid.1", "message_status": "accepted"}],
                          "contacts": [{"wa_id": "256772123456"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        MetaSendResult result = client.sendText(
                "+256 772 123456", " Hello ", "ref-1", null);

        assertThat(result.providerMessageId()).isEqualTo("wamid.1");
        assertThat(result.recipientWaId()).isEqualTo("256772123456");
        assertThat(result.providerStatus()).isEqualTo("accepted");
    }

    @Test
    void sendsTemplateWithOrderedBodyParameters() {
        server.expect(requestTo("https://graph.test/v23.0/12345/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "messaging_product": "whatsapp",
                          "recipient_type": "individual",
                          "to": "256772123456",
                          "biz_opaque_callback_data": "ref-2",
                          "type": "template",
                          "template": {
                            "name": "loan_due",
                            "language": {"code": "en"},
                            "components": [{
                              "type": "body",
                              "parameters": [
                                {"type": "text", "text": "Alice"},
                                {"type": "text", "text": "1000"}
                              ]
                            }]
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {"messages": [{"id": "wamid.2"}]}
                        """, MediaType.APPLICATION_JSON));

        MetaSendResult result = client.sendTemplate(
                "256772123456", "loan_due", "en",
                List.of("Alice", "1000"), "ref-2");

        assertThat(result.providerMessageId()).isEqualTo("wamid.2");
        assertThat(result.providerStatus()).isEqualTo("accepted");
    }

    @Test
    void mapsPermanentMetaErrorWithCodeAndTrace() {
        server.expect(requestTo("https://graph.test/v23.0/12345/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "Invalid parameter",
                                    "type": "OAuthException",
                                    "code": 100,
                                    "error_subcode": 33,
                                    "title": "Invalid parameter",
                                    "fbtrace_id": "trace-1"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.sendText(
                "256772123456", "Hello", "ref-3", null))
                .isInstanceOfSatisfying(WhatsAppException.class, exception -> {
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getCode()).isEqualTo(100);
                    assertThat(exception.getSubcode()).isEqualTo(33);
                    assertThat(exception.getFbtraceId()).isEqualTo("trace-1");
                    assertThat(exception.getMessage()).contains("Invalid parameter");
                });
    }

    @Test
    void treatsServerErrorsAndRateLimitsAsRetryable() {
        server.expect(requestTo("https://graph.test/v23.0/12345/messages"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>upstream failure</html>"));

        assertThatThrownBy(() -> client.sendText(
                "256772123456", "Hello", "ref-4", null))
                .isInstanceOfSatisfying(WhatsAppException.class,
                        exception -> assertThat(exception.isRetryable()).isTrue());
    }

    @Test
    void rejectsSendWhenCredentialsAreMissing() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> client.sendText(
                "256772123456", "Hello", "ref-5", null))
                .isInstanceOf(WhatsAppProviderUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void rejectsInvalidRecipientBeforeCallingMeta() {
        assertThatThrownBy(() -> client.sendText(
                "not-a-number", "Hello", "ref-6", null))
                .isInstanceOf(WhatsAppValidationException.class)
                .hasMessageContaining("Invalid WhatsApp recipient");
    }

    @Test
    void fetchesTemplatesAndCountsBodyParameters() {
        server.expect(requestTo("https://graph.test/v23.0/waba-1/message_templates"
                        + "?fields=id,name,status,category,language,components&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [{
                            "id": "t1",
                            "name": "loan_due",
                            "status": "APPROVED",
                            "category": "UTILITY",
                            "language": "en",
                            "components": [{
                              "type": "BODY",
                              "text": "Hello {{1}}, your balance is {{2}}."
                            }]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<MetaTemplateDefinition> templates = client.fetchTemplates();

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).name()).isEqualTo("loan_due");
        assertThat(templates.get(0).status()).isEqualTo("APPROVED");
        assertThat(templates.get(0).parameterCount()).isEqualTo(2);
    }
}
