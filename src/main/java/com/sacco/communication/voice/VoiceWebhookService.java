package com.sacco.communication.voice;

import com.sacco.communication.voice.entity.VoiceCall;
import com.sacco.communication.voice.entity.VoiceCallEvent;
import com.sacco.communication.voice.repository.VoiceCallEventRepository;
import com.sacco.communication.voice.repository.VoiceCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies Africa Africa's Talking voice callbacks to the stored call
 * timeline. Every callback is persisted as an event first, then applied to the
 * call row so out-of-order delivery can never corrupt state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceWebhookService {

    private final VoiceCallRepository voiceCallRepository;
    private final VoiceCallEventRepository voiceCallEventRepository;

    @Transactional
    public void process(VoiceWebhookPayload payload) {
        String eventType = firstText(payload, "eventType", "type", "event");
        String callKey = firstText(payload, "callId", "call_id", "id");
        String status = firstText(payload, "status", "callStatus", "state");
        Integer duration = payload.integer("duration", "durationSeconds", "seconds");
        String failureReason = firstText(payload, "failureReason", "error", "reason");

        Long callId = resolveCallId(callKey);
        VoiceCall call = callId == null ? null
                : voiceCallRepository.findById(callId).orElse(null);

        VoiceCallEvent event = VoiceCallEvent.builder()
                .callId(callId)
                .eventType(eventType)
                .status(toCallStatus(status))
                .payload(payload.rawPayload())
                .durationSeconds(duration)
                .cost(firstText(payload, "cost"))
                .build();
        event = voiceCallEventRepository.save(event);

        if (call != null) {
            apply(event, call);
        }
    }

    private void apply(VoiceCallEvent event, VoiceCall call) {
        if (event.getStatus() == null) {
            return;
        }
        switch (event.getStatus()) {
            case ANSWERED -> call.setStatus(VoiceCall.VoiceCallStatus.ANSWERED);
            case COMPLETED -> call.setStatus(VoiceCall.VoiceCallStatus.COMPLETED);
            case FAILED -> {
                call.setStatus(VoiceCall.VoiceCallStatus.FAILED);
                call.setFailureReason(event.getPayload() == null ? null : truncate(event.getPayload(), 300));
            }
            case NO_ANSWER -> call.setStatus(VoiceCall.VoiceCallStatus.NO_ANSWER);
            default -> {
            }
        }
        if (event.getDurationSeconds() != null) {
            call.setDurationSeconds(event.getDurationSeconds());
        }
        if (event.getCost() != null) {
            call.setCost(event.getCost());
        }
        if (event.getStatus() == VoiceCall.VoiceCallStatus.COMPLETED
                || event.getStatus() == VoiceCall.VoiceCallStatus.FAILED) {
            call.setEndedAt(java.time.LocalDateTime.now());
        }
        voiceCallRepository.save(call);
    }

    private Long resolveCallId(String callKey) {
        if (callKey == null || callKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(callKey.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstText(VoiceWebhookPayload payload, String... names) {
        String value = payload.first(names);
        return value == null ? null : value.trim();
    }

    private VoiceCall.VoiceCallStatus toCallStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "ANSWERED", "ANSWER", "ACTIVE" -> VoiceCall.VoiceCallStatus.ANSWERED;
            case "COMPLETED", "COMPLETE", "SUCCESS" -> VoiceCall.VoiceCallStatus.COMPLETED;
            case "FAILED", "FAILURE", "ERROR" -> VoiceCall.VoiceCallStatus.FAILED;
            case "NO_ANSWER", "NOANSWER", "MISSED" -> VoiceCall.VoiceCallStatus.NO_ANSWER;
            case "CANCELLED", "CANCEL", "HANGUP" -> VoiceCall.VoiceCallStatus.CANCELLED;
            case "QUEUED", "RINGING", "RING" -> VoiceCall.VoiceCallStatus.RINGING;
            default -> null;
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
