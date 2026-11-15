package com.sacco.communication;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Meta WhatsApp Cloud API configuration. Secrets are supplied through
 * environment variables so they are never committed to source control.
 *
 * <p>Production minimum:
 * <pre>
 *   WA_ENABLED=true
 *   WA_PHONE_NUMBER_ID=123456789012345
 *   WA_WABA_ID=123456789012345
 *   WA_ACCESS_TOKEN=EAAG...
 *   WA_APP_SECRET=&lt;Meta app secret&gt;
 *   WA_VERIFY_TOKEN=&lt;32+ random characters&gt;
 *   WA_WEBHOOK_TOKEN=&lt;32+ random URL-safe characters&gt;
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sacco.whatsapp")
public class WhatsAppProperties {

    /** Master switch. When false the dispatcher pauses and sends fail with 503. */
    private boolean enabled = false;

    /** Graph API host. Override for a local mock server during development. */
    private String graphBaseUrl = "https://graph.facebook.com";

    /** Pinned Graph API version. Upgrade deliberately, never implicitly. */
    private String graphVersion = "v23.0";

    /** Meta phone number ID (not the phone number itself). */
    private String phoneNumberId = "";

    /** WhatsApp Business Account ID, used to list and sync templates. */
    private String wabaId = "";

    /** System User access token with whatsapp_business_messaging and _management. */
    private String accessToken = "";

    /** Meta app secret. Used to verify X-Hub-Signature-256 on every callback. */
    private String appSecret = "";

    /** Value configured in the Meta app dashboard as the webhook verify token. */
    private String verifyToken = "";

    /**
     * Extra high-entropy token embedded in the callback path. Meta signs every
     * callback, but the path token blocks unauthenticated scanners and keeps
     * the endpoint fail-closed if the app secret is ever misconfigured.
     */
    private String webhookToken = "";

    /** Default template language code. */
    private String defaultLanguage = "en";

    /** Cloud API text body limit. */
    private int maxTextLength = 4096;

    /**
     * Meta only allows free-form messages inside the customer service window
     * that opens when the member messages the business. Outside it, a template
     * is required.
     */
    private int customerServiceWindowHours = 24;

    /** Reject sends to numbers that opted out. Disable only with a legal review. */
    private boolean blockOptedOut = true;

    /**
     * Require a recorded opt-in before a business-initiated template is queued.
     * Meta's policy requires prior consent for every template conversation.
     */
    private boolean requireOptInForTemplates = true;

    /** Allow the scheduled template sync to run. */
    private boolean templateSyncEnabled = true;

    private Http http = new Http();
    private Dispatch dispatch = new Dispatch();

    /** Credentials present and the integration switched on. */
    public boolean isConfigured() {
        return enabled
                && notBlank(phoneNumberId)
                && notBlank(accessToken);
    }

    /** Webhook verification and signature validation are fully configured. */
    public boolean isWebhookConfigured() {
        return notBlank(appSecret)
                && notBlank(verifyToken)
                && notBlank(webhookToken);
    }

    /** True when template management can call the WABA endpoint. */
    public boolean isTemplateSyncConfigured() {
        return isConfigured() && notBlank(wabaId);
    }

    public String resolvedBaseUrl() {
        String base = notBlank(graphBaseUrl)
                ? graphBaseUrl.trim().replaceAll("/+$", "")
                : "https://graph.facebook.com";
        String version = notBlank(graphVersion)
                ? graphVersion.trim().replaceAll("^/+", "").replaceAll("/+$", "")
                : "v23.0";
        return base + "/" + version;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    @Setter
    public static class Http {
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 15000;
    }

    @Getter
    @Setter
    public static class Dispatch {
        private long intervalMs = 5000;
        private long initialDelayMs = 20000;
        private int batchSize = 25;
        private int maxAttempts = 5;
        private long baseBackoffMs = 30000;
        private long maxBackoffMs = 3600000;
        private long staleSendingSeconds = 600;
        private int bulkLimit = 200;
    }
}
