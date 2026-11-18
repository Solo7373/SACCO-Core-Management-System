package com.sacco.communication.email;

import com.sacco.communication.email.dto.EmailSendRequest;
import com.sacco.communication.email.dto.EmailSendResponse;
import com.sacco.communication.email.dto.EmailStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff email workspace. Sending is permission-gated by {@link EmailService}.
 */
@RestController
@RequestMapping("/api/communications/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @GetMapping("/status")
    public ResponseEntity<EmailStatusResponse> status(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(emailService.status(actorId));
    }

    @PostMapping("/send")
    public ResponseEntity<EmailSendResponse> send(
            @Valid @RequestBody EmailSendRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(emailService.send(request, actorId));
    }
}
