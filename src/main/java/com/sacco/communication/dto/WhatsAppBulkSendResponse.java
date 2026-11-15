package com.sacco.communication.dto;

import java.util.List;

/**
 * Result of a bulk queue request. Recipients that are opted out, have not
 * opted in, or are outside the 24-hour window are skipped rather than failing
 * the whole batch; the counts make the skips explicit to the operator.
 */
public record WhatsAppBulkSendResponse(
        String batchId,
        int queued,
        int skippedOptOut,
        int skippedNoConsent,
        int skippedWindowClosed,
        List<WhatsAppMessageResponse> messages) {
}
