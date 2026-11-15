package com.sacco.communication;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Africa's Talking configuration. Secrets are read from environment variables
 * (see application.yml) so they are never committed to source control.
 *
 * <p>Production minimum:
 * <pre>
 *   AT_ENABLED=true
 *   AT_USERNAME=your-production-username
 *   AT_API_KEY=atsk_...
 *   AT_SENDER_ID=SCMS
 *   AT_SANDBOX=false
 *   AT_WEBHOOK_TOKEN=&lt;32+ random URL-safe characters&gt;
 *   AT_OTP_PEPPER=&lt;32+ random characters, stable across restarts&gt;
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sacco.africastalking")
public class AfricasTalkingProperties {

    /** Master switch. When false the dispatcher pauses and immediate sends fail with 503. */
    private boolean enabled = false;

    /** Africa's Talking username. Use "sandbox" for the sandbox environment. */
    private String username = "";

    /** API key from the Africa's Talking dashboard. */
    private String apiKey = "";

    /** Optional alphanumeric sender ID / short code. */
    private String senderId = "";

    /**
     * Optional explicit base URL. When blank, the sandbox/production URL is
     * derived from {@link #sandbox}.
     */
    private String baseUrl = "";

    /** True for api.sandbox.africastalking.com, false for production. */
    private boolean sandbox = true;

    /** ISO-3166 alpha-2 region used to parse local numbers such as 0770123456. */
    private String defaultCountryCode = "UG";

    /**
     * Shared secret embedded in the webhook URL. Africa's Talking cannot add
     * custom headers to callbacks, so the token is part of the path:
     * /api/webhooks/africastalking/{token}/delivery
     */
    private String webhookToken = "";

    /**
     * Optional callback source allowlist. Accepts single IPs and CIDR ranges,
     * e.g. 52.16.171.135,52.16.0.0/16. Leave empty to rely on the path token
     * plus HTTPS; populate it in production with Africa's Talking's published
     * callback addresses.
     */
    private List<String> webhookAllowedIps = new ArrayList<>();

    /**
     * Trust {@code X-Forwarded-For} / {@code X-Real-IP}. Enable only when the
     * application is behind a trusted reverse proxy (ngrok, nginx, a cloud
     * load balancer). When false, the TCP peer address is used, which prevents
     * a caller from spoofing its source address with a header.
     */
    private boolean webhookTrustForwardedHeaders = false;

    /** Hard cap for a single SMS body. AT concatenates longer messages into segments. */
    private int maxMessageLength = 1000;

    /** Reject sends to numbers that replied STOP. Disable only with a legal review. */
    private boolean blockOptedOut = true;

    private Http http = new Http();
    private Dispatch dispatch = new Dispatch();
    private Otp otp = new Otp();

    /** Credentials present and the integration switched on. */
    public boolean isConfigured() {
        return enabled
                && username != null && !username.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    public String resolvedBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.replaceAll("/+$", "");
        }
        return sandbox
                ? "https://api.sandbox.africastalking.com"
                : "https://api.africastalking.com";
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
        private long initialDelayMs = 15000;
        private int batchSize = 50;
        private int maxAttempts = 5;
        private long baseBackoffMs = 30000;
        private long maxBackoffMs = 3600000;
        private long staleSendingSeconds = 600;
        private int bulkLimit = 500;
    }

    @Getter
    @Setter
    public static class Otp {
        private int length = 6;
        private long ttlSeconds = 300;
        private int maxAttempts = 5;
        private long resendCooldownSeconds = 60;
        private int maxPerPhonePerWindow = 5;
        private int maxPerIpPerWindow = 20;
        private long windowMinutes = 15;

        /**
         * HMAC pepper for OTP hashes. Must be stable across restarts and kept
         * secret. Never reuse the JWT signing key in production.
         */
        private String pepper = "";

        /**
         * When true, OTPs are only sent to phone numbers belonging to an active
         * member. Keep false only for a public registration flow.
         */
        private boolean requireActiveMember = false;
    }
}
