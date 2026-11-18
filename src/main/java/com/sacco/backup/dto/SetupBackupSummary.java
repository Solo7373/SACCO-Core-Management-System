package com.sacco.backup.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Compact automatic-backup state shown inside the first-run setup wizard,
 * where the full backup dashboard is not reachable until go-live.
 */
@Data
@Builder
public class SetupBackupSummary {
    private boolean automaticEnabled;
    private String schedule;
    private LocalDateTime nextBackupAt;
    private int retentionDays;
    private String cloudMode;
    private String cloudTarget;
    private boolean cloudConfigured;
    private String lastBackupFile;
    private String lastBackupStatus;
    private LocalDateTime lastBackupAt;
    private Long lastBackupSizeBytes;
    private String lastError;
    private long queuedCloudJobs;
}
