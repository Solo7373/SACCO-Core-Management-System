package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.SmsRecipientResult;
import com.sacco.communication.exception.AfricasTalkingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfricasTalkingResponseParserTest {

    private AfricasTalkingResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AfricasTalkingResponseParser(new ObjectMapper());
    }

    @Test
    void parsesSuccessfulRecipient() {
        String response = """
                {"SMSMessageData":{"Message":"Sent to 1/1","Recipients":[
                  {"statusCode":101,"number":"+256772123456","cost":"UGX 30",
                   "status":"Success","messageId":"ATXid_123"}
                ]}}
                """;

        List<SmsRecipientResult> results = parser.parse(response);

        assertThat(results).hasSize(1);
        SmsRecipientResult result = results.get(0);
        assertThat(result.success()).isTrue();
        assertThat(result.retryable()).isFalse();
        assertThat(result.statusCode()).isEqualTo(101);
        assertThat(result.messageId()).isEqualTo("ATXid_123");
        assertThat(result.cost()).isEqualTo("UGX 30");
    }

    @Test
    void marksInvalidNumberAsPermanentFailure() {
        String response = """
                {"SMSMessageData":{"Message":"Sent to 0/1","Recipients":[
                  {"statusCode":406,"number":"+256700000000","cost":"",
                   "status":"Invalid phone number","messageId":""}
                ]}}
                """;

        List<SmsRecipientResult> results = parser.parse(response);

        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).retryable()).isFalse();
        assertThat(results.get(0).failureReason()).contains("Invalid phone number");
    }

    @Test
    void marksServerFailureAsRetryable() {
        String response = """
                {"SMSMessageData":{"Recipients":[
                  {"statusCode":503,"number":"+256772123456","status":"Unavailable"}
                ]}}
                """;

        List<SmsRecipientResult> results = parser.parse(response);

        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).retryable()).isTrue();
    }

    @Test
    void rejectsEmptyRecipientList() {
        String response = """
                {"SMSMessageData":{"Message":"Sent to 0/1","Recipients":[]}}
                """;

        assertThatThrownBy(() -> parser.parse(response))
                .isInstanceOf(AfricasTalkingException.class)
                .hasMessageContaining("Sent to 0/1");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOf(AfricasTalkingException.class)
                .satisfies(exception -> assertThat(
                        ((AfricasTalkingException) exception).isRetryable()).isTrue());
    }
}
