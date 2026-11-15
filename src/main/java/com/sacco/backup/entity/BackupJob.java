package com.sacco.backup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    /** LOCAL, EXTERNAL or CLOUD. */
    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "local_path", length = 1000)
    private String localPath;

    @Column(name = "target_path", length = 1000)
    private String targetPath;

    /** PENDING, RUNNING, COMPLETED, FAILED, QUEUED or RETRYING. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
