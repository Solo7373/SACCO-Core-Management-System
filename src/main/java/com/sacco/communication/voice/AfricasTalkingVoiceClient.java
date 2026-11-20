package com.sacco.communication.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.AfricasTalkingProperties;
import com.sacco.communication.exception.AfricasTalkingException;
import com.sacco.communication.exception.SmsProviderUnavailableException;
import com.sacco.communication.exception.SmsValidationException;
import com.sacco.communication.voice.dto.VoiceCallResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Africa's Talking programmable voice client.
 *
 * <p>Implements the outbound call surface used by the reminder engine and the
 * manual collections "call member" action:
 * <pre>
 *   POST /version1/calls                 initiate an outbound call
 *   POST /version1/calls/{callId}/say   speak a text-to-speech script
 *   POST /version1/calls/{callId}/hangup  end an active call
 * </pre>
 *
 * <p>The API key is sent in the {@code apiKey} header and is never logged. Every
 * failure is converted to {@link AfricasTalkingException} with a retryable flag
 * so callers can decide between back-off and fail-fast handling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfricasTalkingVoiceClient {

    private static final String CALL_INITIATE_PATH = "/version1/calls";
    private static final String CALL_SAY_PATH = "/version1/calls/{callId}/say";
    private static final String CALL_HANGUP_PATH = "/version1/calls/{callId}/hangup";

    private final RestClient africasTalkingRestClient;
    private final AfricasTalkingProperties properties;
    private final ObjectMapper objectMapper;

    /** Starts an outbound voice call and returns the provider call reference. */
    public VoiceCallResponse initiateCall(String toPhone, String fromCallerId, String script) {
        requireConfigured();
        if (toPhone == null || toPhone.isBlank()) {
            throw new SmsValidationException("A destination phone number is required for a voice call.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", toPhone);
        body.put("callType", "OUTBOUND");
        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("engine", properties.getVoice().getEngine());
        if (properties.getVoice().getVoiceId() != null && !properties.getVoice().getVoiceId().isBlank()) {
            voice.put("voiceId", properties.getVoice().getVoiceId().trim());
        }
        if (script != null && !script.isBlank()) {
            voice.put("text", script.trim());
        }
        body.put("voice", voice);

        String responseBody = post(CALL_INITIATE_PATH, body);
        VoiceCallResponse response = VoiceCallResponse.builder()
                .callId(extractCallId(responseBody))
                .status("RINGING")
                .build();
        return response;
    }

    /** Speaks a text-to-speech script on an active call. */
    public void speak(String callId, String text) {
        if (callId == null || callId.isBlank()) {
            throw new SmsValidationException("A provider call id is required before speaking.");
        }
        if (text == null || text.isBlank()) {
            throw new SmsValidationException("A script is required before speaking.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text.trim());
        post(String.format(CALL_SAY_PATH, callId), body);
    }

    /** Ends an active call. */
    public void hangUp(String callId) {
        if (callId == null || callId.isBlank()) {
            throw new SmsValidationException("A provider call id is required before hanging up.");
        }
        post(String.format(CALL_HANGUP_PATH, callId), new LinkedHashMap<>());
    }

    private String post(String path, Object body) {
        try {
            String responseBody = africasTalkingRestClient.post()
                    .uri(path)
                    .header("apiKey", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new AfricasTalkingException(
                        "Africa's Talking returned an empty response for voice call", true);
            }
            return responseBody;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AfricasTalkingException(
                        "Africa's Talking voice authentication failed. Check the voice API key.",
                        false, exception);
            }
            if (status >= 500) {
                throw new AfricasTalkingException(
                        "Africa's Talking voice server error (" + status + "). The call will be retried.",
                        true, exception);
            }
            String detail = exception.getResponseBodyAsString();
            throw new AfricasTalkingException(
                    "Africa's Talking rejected the voice request (" + status + "): " + detail,
                    false, exception);
        } catch (ResourceAccessException exception) {
            throw new AfricasTalkingException(
                    "Could not reach Africa's Talking voice: " + exception.getMessage(), true, exception);
        } catch (AfricasTalkingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AfricasTalkingException(
                    "Unexpected error calling Africa's Talking voice", true, exception);
        }
    }

    private String extractCallId(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode callId = root.path("callId").path("id");
            if (callId.isMissingNode() || callId.isNull()) {
                callId = root.path("callId");
            }
            if (callId.isMissingNode() || callId.isNull()) {
                callId = root.path("id");
            }
            String value = callId.asText(null);
            return value == null ? null : value.trim();
        } catch (Exception exception) {
            log.warn("Could not parse Africa's Talking voice response: {}", responseBody);
            return null;
        }
    }

    private void requireConfigured() {
        if (!properties.isVoiceConfigured()) {
            throw new SmsProviderUnavailableException(
                    "Africa's Talking voice is not configured. Set AT_VOICE_ENABLED and the voice API key.");
        }
    }
}
