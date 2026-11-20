package com.sacco.communication.voice.dto;

import com.sacco.communication.voice.entity.VoiceCallEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Africa Talking voice callback event as surfaced to staff.
 */
@Data
@Builder
public class VoiceCallEventResponse {
    private Long eventId;
    private Long callId;
    private String eventType;
    private String status;
    private String payload;
    private Integer durationSeconds;
    private String cost;
    private LocalDateTime occurredAt;

    public static VoiceCallEventResponse from(VoiceCallEvent event) {
        return VoiceCallEventResponse.builder()
                .eventId(event.getEventId())
                .callId(event.getCallId())
                .eventType(event.getEventType())
                .status(event.getStatus() == null ? null : event.getStatus().name())
                .payload(event.getPayload())
                .durationSeconds(event.getDurationSeconds())
                .cost(event.getCost())
                .occurredAt(event.getOccurredAt())
                .build();
    }
}
