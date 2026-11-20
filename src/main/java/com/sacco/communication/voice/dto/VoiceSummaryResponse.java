package com.sacco.communication.voice.dto;

import com.sacco.communication.voice.entity.VoiceCall;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated voice workspace numbers: totals per call status plus the
 * aggregate duration and cost of answered/completed calls.
 */
@Data
@Builder
public class VoiceSummaryResponse {

    private long total;
    private long queued;
    private long ringing;
    private long answered;
    private long completed;
    private long noAnswer;
    private long failed;
    private long cancelled;
    private long answeredCount;
    private long completedCount;
    @Builder.Default
    private BigDecimal totalDurationSeconds = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    public static VoiceSummaryResponse fromCalls(List<VoiceCall> calls) {
        VoiceSummaryResponse response = VoiceSummaryResponse.builder().build();
        response.setTotal(calls.size());
        for (VoiceCall call : calls) {
            String status = call.getStatus() == null ? "QUEUED" : call.getStatus().name();
            switch (status) {
                case "QUEUED" -> response.setQueued(response.getQueued() + 1);
                case "RINGING" -> response.setRinging(response.getRinging() + 1);
                case "ANSWERED" -> response.setAnswered(response.getAnswered() + 1);
                case "COMPLETED" -> response.setCompleted(response.getCompleted() + 1);
                case "NO_ANSWER" -> response.setNoAnswer(response.getNoAnswer() + 1);
                case "FAILED" -> response.setFailed(response.getFailed() + 1);
                case "CANCELLED" -> response.setCancelled(response.getCancelled() + 1);
                default -> {
                }
            }
            if ("ANSWERED".equals(status)) {
                response.setAnsweredCount(response.getAnsweredCount() + 1);
            }
            if ("COMPLETED".equals(status)) {
                response.setCompletedCount(response.getCompletedCount() + 1);
            }
            if (call.getDurationSeconds() != null) {
                response.setTotalDurationSeconds(response.getTotalDurationSeconds()
                        .add(BigDecimal.valueOf(call.getDurationSeconds())));
            }
            if (call.getCost() != null) {
                try {
                    response.setTotalCost(response.getTotalCost()
                            .add(new BigDecimal(call.getCost())));
                } catch (NumberFormatException ignored) {
                    // Provider costs are advisory; ignore malformed values.
                }
            }
        }
        return response;
    }
}
