package com.sacco.communication.voice;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.communication.AfricasTalkingProperties;
import com.sacco.communication.voice.dto.VoiceCallResponse;
import com.sacco.communication.voice.dto.VoiceSummaryResponse;
import com.sacco.communication.voice.entity.VoiceCall;
import com.sacco.communication.voice.entity.VoiceCallEvent;
import com.sacco.communication.voice.exception.VoiceProviderUnavailableException;
import com.sacco.communication.voice.exception.VoiceValidationException;
import com.sacco.communication.voice.repository.VoiceCallEventRepository;
import com.sacco.communication.voice.repository.VoiceCallRepository;
import com.sacco.setup.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Africa Talking voice workspace: manual call initiation plus the state
 * transitions applied by asynchronous callbacks.
 *
 * <p>Manual calls are permission-gated and audited. Callback events are
 * persisted first and then applied to the call row so an out-of-order delivery
 * cannot corrupt the timeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceCallService {

    private final VoiceCallRepository voiceCallRepository;
    private final VoiceCallEventRepository voiceCallEventRepository;
    private final AfricasTalkingProperties properties;
    private final AfricasTalkingVoiceClient voiceClient;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final SystemSettingsService settingsService;

    @Transactional
    public VoiceCallResponse initiate(Long loanId,
                                      Long memberId,
                                      String toPhone,
                                      String fromCallerId,
                                      String script,
                                      Long actorId) {
        permissionService.require(actorId, Permission.VOICE_SEND);
        if (!properties.isVoiceConfigured()) {
            throw new VoiceProviderUnavailableException(
                    "Africa's Talking voice is not configured. Enable AT_VOICE_ENABLED and set the "
                            + "voice API key before initiating calls.");
        }
        if (toPhone == null || toPhone.isBlank()) {
            throw new VoiceValidationException("A destination phone number is required for a voice call.");
        }
        if (fromCallerId == null || fromCallerId.isBlank()) {
            fromCallerId = settingsService.getOrCreate().getVoiceCallerId();
        }
        if (fromCallerId == null || fromCallerId.isBlank()) {
            throw new VoiceValidationException(
                    "A voice caller ID is required for outbound calls. Configure it in system settings.");
        }

        VoiceCall call = VoiceCall.builder()
                .loanId(loanId)
                .memberId(memberId)
                .toPhone(toPhone)
                .fromCallerId(fromCallerId)
                .status(VoiceCall.VoiceCallStatus.QUEUED)
                .initiatedAt(LocalDateTime.now())
                .build();
        call = voiceCallRepository.save(call);

        VoiceCallResponse response = voiceClient.initiateCall(toPhone, fromCallerId, script);
        call.setStatus(VoiceCall.VoiceCallStatus.RINGING);
        call.setProviderStatus("RINGING");
        if (response.getCallId() != null) {
            call.setProviderCallId(String.valueOf(response.getCallId()));
        }
        call = voiceCallRepository.save(call);

        auditService.logEvent(actorId, "VOICE_CALL_INITIATED", "VOICE_CALL", call.getCallId(),
                "Outbound Africa's Talking voice call to " + toPhone
                        + (loanId == null ? "" : " for loan " + loanId));

        return response;
    }

    @Transactional(readOnly = true)
    public List<VoiceCall> calls(int page, int size, String status, Long actorId) {
        permissionService.require(actorId, Permission.VOICE_VIEW);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page), Math.min(Math.max(1, size), 100));
        if (status == null || status.isBlank()) {
            return voiceCallRepository.findAllByOrderByCallIdDesc(pageable).getContent();
        }
        return voiceCallRepository.findByStatusOrderByCallIdDesc(
                VoiceCall.VoiceCallStatus.valueOf(status), pageable).getContent();
    }

    @Transactional(readOnly = true)
    public VoiceCall call(Long callId, Long actorId) {
        permissionService.require(actorId, Permission.VOICE_VIEW);
        return voiceCallRepository.findById(callId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Voice call not found: " + callId));
    }

    @Transactional(readOnly = true)
    public VoiceSummaryResponse summary(Long actorId) {
        permissionService.require(actorId, Permission.VOICE_VIEW);
        Page<VoiceCall> calls = voiceCallRepository.findAllByOrderByCallIdDesc(
                PageRequest.of(0, 200));
        return VoiceSummaryResponse.fromCalls(calls.getContent());
    }

    @Transactional(readOnly = true)
    public List<VoiceCallEvent> events(Long callId, Long actorId) {
        permissionService.require(actorId, Permission.VOICE_VIEW);
        return voiceCallEventRepository.findAllByCallIdOrderByEventIdAsc(callId);
    }

    @Transactional
    public void hangUp(Long callId, Long actorId) {
        permissionService.require(actorId, Permission.VOICE_SEND);
        VoiceCall call = voiceCallRepository.findById(callId)
                .orElseThrow(() -> new NoSuchElementException("Voice call not found: " + callId));
        if (call.getProviderCallId() == null) {
            throw new com.sacco.communication.voice.exception.VoiceValidationException(
                    "This voice call has no provider call id to hang up.");
        }
        voiceClient.hangUp(call.getProviderCallId());
        call.setStatus(VoiceCall.VoiceCallStatus.CANCELLED);
        call.setEndedAt(java.time.LocalDateTime.now());
        voiceCallRepository.save(call);
    }

    @Transactional
    public VoiceCallEvent recordEvent(VoiceCallEvent event) {
        return voiceCallEventRepository.save(event);
    }
}
