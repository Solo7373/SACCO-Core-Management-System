package com.sacco.communication.voice;

import com.sacco.communication.voice.dto.VoiceCallRequest;
import com.sacco.communication.voice.dto.VoiceCallResponse;
import com.sacco.communication.voice.dto.VoiceCallEventResponse;
import com.sacco.communication.voice.dto.VoiceSummaryResponse;
import com.sacco.communication.voice.entity.VoiceCall;
import com.sacco.communication.voice.entity.VoiceCallEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff Africa Talking voice workspace.
 *
 * <p>Calls can be initiated manually for a collection conversation or from the
 * automated reminder engine, and the asynchronous callback events update the
 * call timeline. Both are permission-gated and audited.
 */
@RestController
@RequestMapping("/api/communications/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceCallService voiceCallService;

    @PostMapping("/calls")
    public ResponseEntity<VoiceCallResponse> initiate(
            @Valid @RequestBody VoiceCallRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        VoiceCallResponse response = voiceCallService.initiate(
                request.getLoanId(),
                request.getMemberId(),
                request.getToPhone(),
                request.getFromCallerId(),
                request.getScript(),
                actorId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/calls/{callId}/hangup")
    public ResponseEntity<Void> hangUp(
            @PathVariable Long callId,
            @RequestParam(defaultValue = "1") Long actorId) {
        voiceCallService.hangUp(callId, actorId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/calls/summary")
    public ResponseEntity<VoiceSummaryResponse> summary(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(voiceCallService.summary(actorId));
    }

    @GetMapping("/calls")
    public ResponseEntity<List<VoiceCallResponse>> calls(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        List<VoiceCall> calls = voiceCallService.calls(page, size, status, actorId);
        return ResponseEntity.ok(calls.stream().map(VoiceCallResponse::from).toList());
    }

    @GetMapping("/calls/{callId}")
    public ResponseEntity<VoiceCallResponse> call(
            @PathVariable Long callId,
            @RequestParam(defaultValue = "1") Long actorId) {
        VoiceCall call = voiceCallService.call(callId, actorId);
        return ResponseEntity.ok(VoiceCallResponse.from(call));
    }

    @GetMapping("/calls/{callId}/events")
    public ResponseEntity<List<VoiceCallEventResponse>> events(
            @PathVariable Long callId,
            @RequestParam(defaultValue = "1") Long actorId) {
        List<VoiceCallEvent> events = voiceCallService.events(callId, actorId);
        return ResponseEntity.ok(events.stream().map(VoiceCallEventResponse::from).toList());
    }
}
