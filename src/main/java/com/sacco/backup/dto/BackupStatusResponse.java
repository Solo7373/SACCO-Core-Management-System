package com.sacco.backup.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BackupStatusResponse {
    private String localDirectory;
    private String externalDirectory;
    private boolean externalConfigured;
    private String cloudMode;
    private String cloudTarget;
    private boolean cloudConfigured;
    private boolean automaticBackupsEnabled;
    private String backupSchedule;
    private LocalDateTime nextBackupAt;
    private int retentionDays;
    private boolean backupRunning;
    private boolean pgDumpAvailable;
    private String pgDumpPath;
    private long queuedCloudJobs;
    private long failedJobs;
    private long totalJobs;
    private BackupJobResponse lastLocalBackup;
    private BackupJobResponse lastExternalBackup;
    private BackupJobResponse lastCloudUpload;
    private List<BackupJobResponse> recentJobs;
}
