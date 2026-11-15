package com.sacco.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.MetaSendResult;
import com.sacco.communication.dto.MetaTemplateDefinition;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.exception.WhatsAppProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin, stateless Meta WhatsApp Cloud API client.
 *
 * <p>The access token is sent in the Authorization header and is never logged.
 * Every failure is converted to {@link WhatsAppException} with a retryable
 * flag so the outbox can decide whether to back off or fail fast.
 */
@Slf4j
@Component
public class MetaWhatsAppClient {

    private static final Pattern TEMPLATE_PARAMETER = Pattern.compile("\\{\\{\\s*(\\d+)\\s*}}");
    private static final Set<Integer> RETRYABLE_CODES = Set.of(
            1, 2, 4, 17, 32, 341, 368, 80007,
            130429, 131048, 131056, 131057, 131052, 131053);

    private final RestClient restClient;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    public MetaWhatsAppClient(@Qualifier("whatsAppRestClient") RestClient restClient,
                              WhatsAppProperties properties,
                              ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MetaSendResult sendText(String to,
                                   String body,
                                   String reference,
                                   String contextMessageId) {
        if (body == null || body.isBlank()) {
            throw new WhatsAppValidationException("Message body is required.");
        }
        if (body.length() > properties.getMaxTextLength()) {
            throw new WhatsAppValidationException(
                    "Message must be " + properties.getMaxTextLength()
                            + " characters or fewer.");
        }

        Map<String, Object> payload = basePayload(to, reference);
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("preview_url", false);
        text.put("body", body.trim());
        payload.put("type", "text");
        payload.put("text", text);
        if (contextMessageId != null && !contextMessageId.isBlank()) {
            payload.put("context", Map.of("message_id", contextMessageId.trim()));
        }
        return postMessage(payload);
    }

    public MetaSendResult sendTemplate(String to,
                                       String templateName,
                                       String language,
                                       List<String> parameters,
                                       String reference) {
        if (templateName == null || templateName.isBlank()) {
            throw new WhatsAppValidationException("Template name is required.");
        }
        String code = language == null || language.isBlank()
                ? properties.getDefaultLanguage()
                : language.trim();

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName.trim());
        template.put("language", Map.of("code", code));
        if (parameters != null && !parameters.isEmpty()) {
            List<Map<String, Object>> values = parameters.stream()
                    .map(value -> Map.<String, Object>of(
                            "type", "text",
                            "text", value == null ? "" : value))
                    .toList();
            template.put("components", List.of(
                    Map.of("type", "body", "parameters", values)));
        }

        Map<String, Object> payload = basePayload(to, reference);
        payload.put("type", "template");
        payload.put("template", template);
        return postMessage(payload);
    }

    public MetaSendResult sendMedia(String to,
                                    String mediaType,
                                    String mediaUrl,
                                    String mediaId,
                                    String caption,
                                    String reference) {
        String type = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("image", "document", "audio", "video", "sticker").contains(type)) {
            throw new WhatsAppValidationException("Unsupported media type: " + mediaType);
        }
        if ((mediaUrl == null || mediaUrl.isBlank())
                && (mediaId == null || mediaId.isBlank())) {
            throw new WhatsAppValidationException(
                    "A media URL or media ID is required.");
        }

        Map<String, Object> media = new LinkedHashMap<>();
        if (mediaId != null && !mediaId.isBlank()) {
            media.put("id", mediaId.trim());
        } else {
            media.put("link", mediaUrl.trim());
        }
        if (caption != null && !caption.isBlank() && !"audio".equals(type)
                && !"sticker".equals(type)) {
            media.put("caption", caption.trim());
        }

        Map<String, Object> payload = basePayload(to, reference);
        payload.put("type", type);
        payload.put(type, media);
        return postMessage(payload);
    }

    /** Marks an inbound message as read (blue ticks). Best-effort. */
    public void markRead(String providerMessageId) {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("status", "read");
        payload.put("message_id", providerMessageId);
        postRaw(payload);
    }

    /**
     * Lists templates from the WABA endpoint. Follows Meta's paging links up to
     * a bounded number of pages so a misconfigured account cannot loop forever.
     */
    public List<MetaTemplateDefinition> fetchTemplates() {
        if (!properties.isTemplateSyncConfigured()) {
            throw new WhatsAppProviderUnavailableException(
                    "WhatsApp template sync is not configured. Set WA_WABA_ID.");
        }

        String next = properties.resolvedBaseUrl()
                + "/" + properties.getWabaId()
                + "/message_templates?fields=id,name,status,category,language,components&limit=100";
        List<MetaTemplateDefinition> templates = new ArrayList<>();

        for (int page = 0; page < 10 && next != null && !next.isBlank(); page++) {
            JsonNode root = readJson(get(next));
            for (JsonNode node : root.path("data")) {
                String components = node.path("components").toString();
                templates.add(new MetaTemplateDefinition(
                        text(node, "id"),
                        text(node, "name"),
                        text(node, "language"),
                        text(node, "category"),
                        text(node, "status"),
                        components,
                        countParameters(components)));
            }
            next = text(root.path("paging"), "next");
        }
        return templates;
    }

    private MetaSendResult postMessage(Map<String, Object> payload) {
        String body = postRaw(payload);
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode messages = root.path("messages");
            if (!messages.isArray() || messages.isEmpty()) {
                throw new WhatsAppException(
                        "Meta accepted the request but returned no message id.", false);
            }
            JsonNode first = messages.get(0);
            String wamid = text(first, "id");
            if (wamid == null || wamid.isBlank()) {
                throw new WhatsAppException(
                        "Meta returned an empty message id.", false);
            }
            String waId = null;
            JsonNode contacts = root.path("contacts");
            if (contacts.isArray() && !contacts.isEmpty()) {
                waId = text(contacts.get(0), "wa_id");
            }
            String status = text(first, "message_status");
            return new MetaSendResult(wamid, waId,
                    status == null || status.isBlank() ? "accepted" : status);
        } catch (WhatsAppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WhatsAppException(
                    "Could not parse Meta's send response.", false, exception);
        }
    }

    private String postRaw(Map<String, Object> payload) {
        if (!properties.isConfigured()) {
            throw new WhatsAppProviderUnavailableException(
                    "WhatsApp is not configured. Set WA_ENABLED, WA_PHONE_NUMBER_ID and WA_ACCESS_TOKEN.");
        }

        try {
            return restClient.post()
                    .uri(properties.resolvedBaseUrl()
                            + "/" + properties.getPhoneNumberId() + "/messages")
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw classifyHttpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new WhatsAppException(
                    "Could not reach Meta WhatsApp: " + exception.getMessage(), true, exception);
        } catch (WhatsAppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WhatsAppException(
                    "Unexpected error calling Meta WhatsApp", true, exception);
        }
    }

    private String get(String uri) {
        if (!properties.isConfigured()) {
            throw new WhatsAppProviderUnavailableException(
                    "WhatsApp is not configured. Set WA_ENABLED, WA_PHONE_NUMBER_ID and WA_ACCESS_TOKEN.");
        }
        try {
            return restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw classifyHttpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new WhatsAppException(
                    "Could not reach Meta WhatsApp: " + exception.getMessage(), true, exception);
        } catch (WhatsAppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WhatsAppException(
                    "Unexpected error calling Meta WhatsApp", true, exception);
        }
    }

    private WhatsAppException classifyHttpFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String raw = exception.getResponseBodyAsString();
        Integer code = null;
        Integer subcode = null;
        String title = null;
        String message = null;
        String fbtraceId = null;

        try {
            JsonNode error = objectMapper.readTree(raw).path("error");
            if (error.isObject()) {
                code = integer(error, "code");
                subcode = integer(error, "error_subcode");
                title = text(error, "title");
                message = text(error, "message");
                fbtraceId = text(error, "fbtrace_id");
            }
        } catch (Exception ignored) {
            // The body may be HTML from a proxy; fall back to the HTTP status.
        }

        boolean retryable = status == 429
                || status >= 500
                || (code != null && RETRYABLE_CODES.contains(code));

        String detail = message != null && !message.isBlank()
                ? message
                : truncate(raw, 300);
        String composed = "Meta WhatsApp rejected the request (" + status + ")"
                + (code == null ? "" : " code " + code)
                + (subcode == null ? "" : "/" + subcode)
                + ": " + detail;

        return new WhatsAppException(composed, retryable, code, subcode,
                title, fbtraceId, exception);
    }

    private Map<String, Object> basePayload(String to, String reference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizeRecipient(to));
        if (reference != null && !reference.isBlank()) {
            payload.put("biz_opaque_callback_data", reference.trim());
        }
        return payload;
    }

    private String normalizeRecipient(String to) {
        if (to == null || to.isBlank()) {
            throw new WhatsAppValidationException("A recipient is required.");
        }
        String digits = to.trim()
                .replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
        if (!digits.matches("\\d{8,15}")) {
            throw new WhatsAppValidationException(
                    "Invalid WhatsApp recipient. Use E.164 format, for example +256700123456.");
        }
        return digits;
    }

    private int countParameters(String componentsJson) {
        try {
            JsonNode components = objectMapper.readTree(componentsJson);
            int max = 0;
            for (JsonNode component : components) {
                if (!"BODY".equalsIgnoreCase(text(component, "type"))) {
                    continue;
                }
                String text = text(component, "text");
                if (text == null) {
                    continue;
                }
                Matcher matcher = TEMPLATE_PARAMETER.matcher(text);
                while (matcher.find()) {
                    max = Math.max(max, Integer.parseInt(matcher.group(1)));
                }
            }
            return max;
        } catch (Exception exception) {
            return 0;
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception exception) {
            throw new WhatsAppException(
                    "Meta returned a malformed JSON response.", false, exception);
        }
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
