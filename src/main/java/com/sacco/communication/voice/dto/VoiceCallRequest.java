package com.sacco.communication.voice.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Manual voice call initiation request. The reminder engine fills this from
 * the stored policy; staff can also call a member directly.
 */
@Data
@Builder
public class VoiceCallRequest {
    private Long loanId;
    private Long memberId;
    private String toPhone;
    private String fromCallerId;
    private String script;
}
