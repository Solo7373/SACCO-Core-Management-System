package com.sacco.communication.dto;

import com.sacco.communication.entity.WhatsAppConsent;

import java.time.LocalDateTime;

public record WhatsAppConsentResponse(
        String phoneNumber,
        WhatsAppConsent.ConsentStatus status,
        String source,
        Long memberId,
        LocalDateTime lastInboundAt,
        LocalDateTime lastOutboundAt,
        LocalDateTime optedInAt,
        LocalDateTime optedOutAt,
        boolean customerServiceWindowOpen,
        LocalDateTime customerServiceWindowClosesAt) {

    public static WhatsAppConsentResponse from(WhatsAppConsent consent,
                                               boolean windowOpen,
                                               LocalDateTime windowClosesAt) {
        return new WhatsAppConsentResponse(
                consent.getPhoneNumber(),
                consent.getStatus(),
                consent.getSource(),
                consent.getMemberId(),
                consent.getLastInboundAt(),
                consent.getLastOutboundAt(),
                consent.getOptedInAt(),
                consent.getOptedOutAt(),
                windowOpen,
                windowClosesAt);
    }
}
