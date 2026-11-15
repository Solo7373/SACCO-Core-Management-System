package com.sacco.communication;

import com.sacco.communication.dto.SmsRecipientResult;
import com.sacco.communication.exception.AfricasTalkingException;
import com.sacco.communication.exception.SmsProviderUnavailableException;
import com.sacco.communication.exception.SmsValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Thin, stateless Africa's Talking REST client.
 *
 * <p>The API key is sent in the {@code apiKey} header and is never logged.
 * Every failure is converted to {@link AfricasTalkingException} with a
 * retryable flag so the outbox can decide whether to back off or fail fast.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfricasTalkingClient {

    private final RestClient africasTalkingRestClient;
    private final AfricasTalkingProperties properties;
    private final AfricasTalkingResponseParser responseParser;

    /** Sends one SMS and returns the provider's per-recipient result. */
    public List<SmsRecipientResult> send(String to, String message, String from) {
        return sendBulk(List.of(to), message, from);
    }

    /**
     * Sends one API request to multiple recipients (comma-separated {@code to}).
     * Africa's Talking returns one recipient entry per number.
     */
    public List<SmsRecipientResult> sendBulk(List<String> recipients, String message, String from) {
        if (!properties.isConfigured()) {
            throw new SmsProviderUnavailableException(
                    "SMS provider is not configured. Set AT_ENABLED, AT_USERNAME and AT_API_KEY.");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new SmsValidationException("At least one recipient is required.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", properties.getUsername());
        form.add("to", String.join(",", recipients));
        form.add("message", message);
        if (from != null && !from.isBlank()) {
            form.add("from", from);
        }
        // Let AT queue the message if the destination network is temporarily
        // unavailable; delivery reports still arrive asynchronously.
        form.add("enqueue", "1");

        String responseBody;
        try {
            responseBody = africasTalkingRestClient.post()
                    .uri("/version1/messaging")
                    .header("apiKey", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw classifyHttpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AfricasTalkingException(
                    "Could not reach Africa's Talking: " + exception.getMessage(), true, exception);
        } catch (AfricasTalkingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AfricasTalkingException(
                    "Unexpected error calling Africa's Talking", true, exception);
        }

        return responseParser.parse(responseBody);
    }

    private AfricasTalkingException classifyHttpFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new AfricasTalkingException(
                    "Africa's Talking authentication failed. Check AT_USERNAME and AT_API_KEY.",
                    false, exception);
        }
        if (status == 429) {
            return new AfricasTalkingException(
                    "Africa's Talking rate limit reached. The message will be retried.",
                    true, exception);
        }
        if (status >= 500) {
            return new AfricasTalkingException(
                    "Africa's Talking server error (" + status + "). The message will be retried.",
                    true, exception);
        }
        String detail = truncate(exception.getResponseBodyAsString(), 300);
        return new AfricasTalkingException(
                "Africa's Talking rejected the request (" + status + "): " + detail,
                false, exception);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
