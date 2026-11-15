package com.sacco.communication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the WhatsApp outbox. Safe to run on multiple application instances:
 * the claim query uses FOR UPDATE SKIP LOCKED, so each row is delivered by
 * exactly one worker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppDispatcher {

    private final WhatsAppProperties properties;
    private final WhatsAppOutboxClaimer claimer;
    private final WhatsAppDeliveryService deliveryService;
    private final WhatsAppWebhookService webhookService;

    @Scheduled(
            fixedDelayString = "${sacco.whatsapp.dispatch.interval-ms:5000}",
            initialDelayString = "${sacco.whatsapp.dispatch.initial-delay-ms:20000}")
    public void dispatch() {
        // Reconcile status events that arrived before their outbox row was
        // updated with the Meta message id.
        try {
            webhookService.reprocessPendingEvents();
        } catch (Exception exception) {
            log.warn("Could not reconcile WhatsApp webhook events: {}", exception.getMessage());
        }

        if (!properties.isConfigured()) {
            log.debug("WhatsApp is not configured; outbox is paused.");
            return;
        }

        try {
            int requeued = claimer.resetStale(
                    properties.getDispatch().getStaleSendingSeconds());
            if (requeued > 0) {
                log.warn("Requeued {} stale WhatsApp messages", requeued);
            }

            List<Long> messageIds = claimer.claim(
                    properties.getDispatch().getBatchSize());
            for (Long messageId : messageIds) {
                try {
                    deliveryService.deliverClaimed(messageId);
                } catch (Exception exception) {
                    log.error("WhatsApp dispatch failed for message {}", messageId, exception);
                }
            }
        } catch (Exception exception) {
            log.error("WhatsApp dispatcher tick failed", exception);
        }
    }
}
