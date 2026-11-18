package com.sacco.communication.email;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.communication.email.dto.EmailSendRequest;
import com.sacco.communication.email.dto.EmailSendResponse;
import com.sacco.communication.email.dto.EmailStatusResponse;
import com.sacco.storage.google.GmailSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Staff-facing email workspace. Sends are permission-gated and audited.
 * Unlike SMS, email is delivered synchronously because Gmail acknowledges the
 * message immediately and there is no provider-side delivery report to poll.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final GmailService gmailService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public EmailStatusResponse status(Long actorId) {
        permissionService.require(actorId, Permission.EMAIL_VIEW);
        GmailSettings settings = gmailService.settings();
        boolean configured = gmailService.isConfigured();
        return EmailStatusResponse.builder()
                .configured(configured)
                .enabled(true)
                .authMode(settings.authMode())
                .fromAddress(settings.fromAddress())
                .fromName(settings.fromName())
                .replyTo(settings.replyTo())
                .message(configured
                        ? "Gmail is connected and ready to send member email."
                        : "Gmail is not connected. Complete the email test in system setup.")
                .build();
    }

    public EmailSendResponse send(EmailSendRequest request, Long actorId) {
        permissionService.require(actorId, Permission.EMAIL_SEND);

        String to = request.getTo() == null ? "" : request.getTo().trim();
        String subject = request.getSubject() == null ? "" : request.getSubject().trim();
        String body = request.getBody() == null ? "" : request.getBody().trim();

        GmailSendResult result = gmailService.send(to, subject, body);
        auditService.logEvent(actorId, "EMAIL_SENT", "EMAIL_MESSAGE", 0L,
                "Email to " + mask(result.to()) + ": " + truncate(result.subject(), 120));

        return EmailSendResponse.builder()
                .messageId(result.messageId())
                .threadId(result.threadId())
                .to(result.to())
                .from(result.from())
                .subject(result.subject())
                .sentAt(LocalDateTime.now())
                .build();
    }

    private String mask(String address) {
        if (address == null || address.isBlank()) {
            return "(unknown)";
        }
        int at = address.indexOf('@');
        if (at <= 1) {
            return address;
        }
        String local = address.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + address.substring(at);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
