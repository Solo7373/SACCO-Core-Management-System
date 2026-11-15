package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.exception.WhatsAppValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsAppWebhookPayloadParserTest {

    private WhatsAppWebhookPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new WhatsAppWebhookPayloadParser(new ObjectMapper());
    }

    @Test
    void parsesStatusAndInboundMessageFromOneCallback() {
        WhatsAppWebhookPayload payload = parser.parse("""
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "waba-1",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                          "display_phone_number": "256700000000",
                          "phone_number_id": "12345"
                        },
                        "contacts": [{
                          "profile": {"name": "Alice Member"},
                          "wa_id": "256772123456"
                        }],
                        "statuses": [{
                          "id": "wamid.1",
                          "status": "delivered",
                          "timestamp": "1700000000",
                          "recipient_id": "256772123456"
                        }],
                        "messages": [{
                          "from": "256772123456",
                          "id": "wamid.2",
                          "timestamp": "1700000001",
                          "type": "text",
                          "text": {"body": "Hello SACCO"}
                        }]
                      }
                    }]
                  }]
                }
                """);

        assertThat(payload.statuses()).hasSize(1);
        assertThat(payload.statuses().get(0).providerMessageId()).isEqualTo("wamid.1");
        assertThat(payload.statuses().get(0).status()).isEqualTo("delivered");
        assertThat(payload.statuses().get(0).timestamp()).isNotNull();

        assertThat(payload.messages()).hasSize(1);
        WhatsAppWebhookPayload.InboundMessage message = payload.messages().get(0);
        assertThat(message.providerMessageId()).isEqualTo("wamid.2");
        assertThat(message.from()).isEqualTo("256772123456");
        assertThat(message.contactName()).isEqualTo("Alice Member");
        assertThat(message.body()).isEqualTo("Hello SACCO");
        assertThat(message.messageType()).isEqualTo("text");
    }

    @Test
    void parsesMediaAndInteractiveContent() {
        WhatsAppWebhookPayload payload = parser.parse("""
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "256772123456",
                          "id": "wamid.3",
                          "type": "image",
                          "image": {
                            "id": "media-1",
                            "mime_type": "image/jpeg",
                            "caption": "Receipt"
                          }
                        }, {
                          "from": "256772123456",
                          "id": "wamid.4",
                          "type": "interactive",
                          "interactive": {
                            "button_reply": {"id": "yes", "title": "Yes"}
                          }
                        }]
                      }
                    }]
                  }]
                }
                """);

        assertThat(payload.messages()).hasSize(2);
        assertThat(payload.messages().get(0).mediaId()).isEqualTo("media-1");
        assertThat(payload.messages().get(0).mediaMimeType()).isEqualTo("image/jpeg");
        assertThat(payload.messages().get(0).body()).isEqualTo("Receipt");
        assertThat(payload.messages().get(1).body()).isEqualTo("Yes");
    }

    @Test
    void ignoresUnrelatedWebhookObjects() {
        WhatsAppWebhookPayload payload = parser.parse("""
                {"object": "page", "entry": [{"id": "1"}]}
                """);

        assertThat(payload.statuses()).isEmpty();
        assertThat(payload.messages()).isEmpty();
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not json"))
                .isInstanceOf(WhatsAppValidationException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(WhatsAppValidationException.class)
                .hasMessageContaining("empty");
    }
}
