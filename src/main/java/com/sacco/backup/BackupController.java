package com.sacco.backup;

import com.sacco.backup.dto.BackupJobResponse;
import com.sacco.backup.dto.BackupStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/status")
    public ResponseEntity<BackupStatusResponse> getStatus(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(backupService.getStatus(actorId));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<BackupJobResponse>> getJobs(
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(backupService.getJobs(limit, actorId));
    }

    @PostMapping("/run")
    public ResponseEntity<BackupStatusResponse> runBackup(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(backupService.runBackup(actorId));
    }

    @PostMapping("/cloud/retry")
    public ResponseEntity<Integer> retryCloudQueue(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(backupService.processCloudQueue(actorId));
    }
}
