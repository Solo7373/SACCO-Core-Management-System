package com.sacco.setup;

import com.sacco.communication.email.dto.EmailSendResponse;
import com.sacco.communication.email.dto.EmailTestRequest;
import com.sacco.backup.dto.BackupStatusResponse;
import com.sacco.setup.dto.AdminSetupRequest;
import com.sacco.setup.dto.BillingSetupRequest;
import com.sacco.setup.dto.DriveTestResponse;
import com.sacco.setup.dto.EmailTestResponse;
import com.sacco.setup.dto.MessagingSetupRequest;
import com.sacco.setup.dto.OrganizationSetupRequest;
import com.sacco.setup.dto.SetupStatusResponse;
import com.sacco.setup.dto.StorageCredentialsResponse;
import com.sacco.setup.dto.StorageSetupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> status() {
        return ResponseEntity.ok(setupService.status());
    }

    @PostMapping("/organization")
    public ResponseEntity<SetupStatusResponse> organization(
            @Valid @RequestBody OrganizationSetupRequest request) {
        return ResponseEntity.ok(setupService.saveOrganization(request));
    }

    @PostMapping("/admin")
    public ResponseEntity<SetupStatusResponse> admin(
            @Valid @RequestBody AdminSetupRequest request) {
        return ResponseEntity.ok(setupService.saveAdmin(request));
    }

    @PostMapping("/storage")
    public ResponseEntity<SetupStatusResponse> storage(
            @Valid @RequestBody StorageSetupRequest request) {
        return ResponseEntity.ok(setupService.saveStorage(request));
    }

    @PostMapping(value = "/storage/credentials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageCredentialsResponse> uploadCredentials(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(setupService.uploadServiceAccount(file));
    }

    @PostMapping("/storage/test")
    public ResponseEntity<DriveTestResponse> testStorage() {
        return ResponseEntity.ok(setupService.testStorage());
    }

    @PostMapping("/backup/run")
    public ResponseEntity<BackupStatusResponse> runBackup() {
        return ResponseEntity.ok(setupService.runBackupNow());
    }

    @PostMapping("/email/test")
    public ResponseEntity<EmailTestResponse> testEmail() {
        return ResponseEntity.ok(setupService.testEmail());
    }

    @PostMapping("/email/send-test")
    public ResponseEntity<EmailSendResponse> sendTestEmail(
            @Valid @RequestBody(required = false) EmailTestRequest request) {
        return ResponseEntity.ok(setupService.sendTestEmail(request == null ? null : request.getTo()));
    }

    @PostMapping("/messaging")
    public ResponseEntity<SetupStatusResponse> messaging(
            @Valid @RequestBody MessagingSetupRequest request) {
        return ResponseEntity.ok(setupService.saveMessaging(request));
    }

    @PostMapping("/billing")
    public ResponseEntity<SetupStatusResponse> billing(
            @Valid @RequestBody BillingSetupRequest request) {
        return ResponseEntity.ok(setupService.saveBilling(request));
    }

    @PostMapping("/complete")
    public ResponseEntity<SetupStatusResponse> complete() {
        return ResponseEntity.ok(setupService.completeSetup());
    }
}
