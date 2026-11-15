package com.sacco.communication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.SmsRecipientResult;
import com.sacco.communication.exception.AfricasTalkingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the Africa's Talking messaging response:
 * <pre>
 * {"SMSMessageData":{"Message":"Sent to 1/1","Recipients":[
 *   {"statusCode":101,"number":"+256...","cost":"UGX 0","status":"Success","messageId":"ATXid_..."}
 * ]}}
 * </pre>
 * Kept separate from the HTTP client so it can be unit-tested without a server.
 */
@Component
@RequiredArgsConstructor
public class AfricasTalkingResponseParser {

    private final ObjectMapper objectMapper;

    public List<SmsRecipientResult> parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AfricasTalkingException("Africa's Talking returned an empty response", true);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("SMSMessageData");
            JsonNode recipients = data.path("Recipients");

            if (!recipients.isArray() || recipients.isEmpty()) {
                String providerMessage = data.path("Message")
                        .asText("No recipients returned by Africa's Talking");
                throw new AfricasTalkingException(
                        "Africa's Talking rejected the request: " + providerMessage, false);
            }

            List<SmsRecipientResult> results = new ArrayList<>();
            for (JsonNode node : recipients) {
                int statusCode = node.path("statusCode").asInt(-1);
                String status = node.path("status").asText("");
                boolean success = statusCode == 100 || statusCode == 101 || statusCode == 102;
                boolean retryable = !success
                        && (statusCode == 407 || statusCode >= 500 || statusCode == -1);
                String failure = success ? null
                        : status + (statusCode > 0 ? " (code " + statusCode + ")" : "");

                results.add(new SmsRecipientResult(
                        node.path("number").asText(null),
                        statusCode,
                        status,
                        node.path("messageId").asText(null),
                        node.path("cost").asText(null),
                        success,
                        retryable,
                        failure));
            }
            return results;
        } catch (JsonProcessingException exception) {
            throw new AfricasTalkingException(
                    "Could not parse the Africa's Talking response", true, exception);
        }
    }
}
