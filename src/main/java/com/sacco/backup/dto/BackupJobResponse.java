package com.sacco.backup.dto;

import com.sacco.backup.entity.BackupJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BackupJobResponse {
    private Long jobId;
    private String tier;
    private String fileName;
    private String localPath;
    private String targetPath;
    private String status;
    private Integer attempts;
    private Long sizeBytes;
    private String checksum;
    private String errorMessage;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BackupJobResponse fromEntity(BackupJob job) {
        if (job == null) {
            return null;
        }
        return BackupJobResponse.builder()
                .jobId(job.getJobId())
                .tier(job.getTier())
                .fileName(job.getFileName())
                .localPath(job.getLocalPath())
                .targetPath(job.getTargetPath())
                .status(job.getStatus())
                .attempts(job.getAttempts())
                .sizeBytes(job.getSizeBytes())
                .checksum(job.getChecksum())
                .errorMessage(job.getErrorMessage())
                .nextAttemptAt(job.getNextAttemptAt())
                .lastAttemptAt(job.getLastAttemptAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
