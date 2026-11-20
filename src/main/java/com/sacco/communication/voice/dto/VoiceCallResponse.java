package com.sacco.communication.voice.dto;

import com.sacco.communication.voice.entity.VoiceCall;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Staff-facing voice call record, including the asynchronous Africa Talking
 * callback events attached to it.
 */
@Data
@Builder
public class VoiceCallResponse {

    private String callId;
    private Long loanId;
    private Long memberId;
    private String toPhone;
    private String fromCallerId;
    private String status;
    private String providerCallId;
    private String providerStatus;
    private Integer durationSeconds;
    private String cost;
    private String failureReason;
    private LocalDateTime initiatedAt;
    private LocalDateTime answeredAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;

    public static VoiceCallResponse from(VoiceCall call) {
        return VoiceCallResponse.builder()
                .callId(call.getCallId() == null ? null : String.valueOf(call.getCallId()))
                .loanId(call.getLoanId())
                .memberId(call.getMemberId())
                .toPhone(call.getToPhone())
                .fromCallerId(call.getFromCallerId())
                .status(call.getStatus() == null ? null : call.getStatus().name())
                .providerCallId(call.getProviderCallId())
                .providerStatus(call.getProviderStatus())
                .durationSeconds(call.getDurationSeconds())
                .cost(call.getCost())
                .failureReason(call.getFailureReason())
                .initiatedAt(call.getInitiatedAt())
                .answeredAt(call.getAnsweredAt())
                .endedAt(call.getEndedAt())
                .createdAt(call.getCreatedAt())
                .build();
    }
}
