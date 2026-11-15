package com.sacco.communication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the SMS outbox. Safe to run on multiple application instances:
 * the claim query uses FOR UPDATE SKIP LOCKED, so each row is delivered by
 * exactly one worker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsDispatcher {

    private final AfricasTalkingProperties properties;
    private final SmsOutboxClaimer claimer;
    private final SmsDeliveryService deliveryService;
    private final SmsWebhookService webhookService;

    @Scheduled(
            fixedDelayString = "${sacco.africastalking.dispatch.interval-ms:5000}",
            initialDelayString = "${sacco.africastalking.dispatch.initial-delay-ms:15000}")
    public void dispatch() {
        // Reconcile delivery reports that arrived before their outbox row was
        // updated with the provider message id.
        try {
            webhookService.reprocessPendingDeliveryReports();
        } catch (Exception exception) {
            log.warn("Could not reconcile delivery reports: {}", exception.getMessage());
        }

        if (!properties.isConfigured()) {
            log.debug("Africa's Talking is not configured; SMS outbox is paused.");
            return;
        }

        try {
            int requeued = claimer.resetStale(
                    properties.getDispatch().getStaleSendingSeconds());
            if (requeued > 0) {
                log.warn("Requeued {} stale SMS messages", requeued);
            }

            List<Long> messageIds = claimer.claim(
                    properties.getDispatch().getBatchSize());
            for (Long messageId : messageIds) {
                try {
                    deliveryService.deliverClaimed(messageId);
                } catch (Exception exception) {
                    log.error("SMS dispatch failed for message {}", messageId, exception);
                }
            }
        } catch (Exception exception) {
            log.error("SMS dispatcher tick failed", exception);
        }
    }
}
