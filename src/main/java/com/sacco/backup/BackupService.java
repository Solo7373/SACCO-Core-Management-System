package com.sacco.backup;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.backup.dto.BackupJobResponse;
import com.sacco.backup.dto.BackupStatusResponse;
import com.sacco.backup.dto.SetupBackupSummary;
import com.sacco.backup.entity.BackupJob;
import com.sacco.backup.repository.BackupJobRepository;
import com.sacco.setup.SystemSettingsService;
import com.sacco.setup.entity.SystemSettings;
import com.sacco.storage.drive.DriveUploadResult;
import com.sacco.storage.drive.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Three-tier hybrid resilience engine:
 *
 *  Tier 1 - pg_dump snapshots written to the host disk on a schedule.
 *  Tier 2 - mirror the latest snapshot to an external USB / mounted drive.
 *  Tier 3 - persist cloud upload jobs in PostgreSQL so power or Wi-Fi outages
 *           cannot lose them. A background worker retries with backoff and
 *           resumes automatically when connectivity returns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupJobRepository backupJobRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final GoogleDriveService googleDriveService;
    private final SystemSettingsService systemSettingsService;

    @Value("${sacco.backup.local.directory:backups/local}")
    private String localDirectory;

    @Value("${sacco.backup.external.directory:backups/external}")
    private String externalDirectory;

    @Value("${sacco.backup.cloud.directory:backups/cloud}")
    private String cloudDirectory;

    @Value("${sacco.backup.cloud.endpoint:}")
    private String cloudEndpoint;

    @Value("${sacco.backup.cloud.retry-interval-ms:60000}")
    private long cloudRetryIntervalMs;

    @Value("${sacco.backup.scheduler.enabled:true}")
    private boolean automaticSchedulerEnabled;

    @Value("${sacco.backup.pg-dump:}")
    private String configuredPgDump;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<String> CLOUD_ACTIVE_STATUSES =
            List.of("QUEUED", "RETRYING", "RUNNING");

    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private final AtomicBoolean cloudRunning = new AtomicBoolean(false);

    public BackupStatusResponse getStatus(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.BACKUP_VIEW);
        return buildStatus();
    }

    public List<BackupJobResponse> getJobs(int limit, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.BACKUP_VIEW);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return backupJobRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .limit(safeLimit)
                .map(BackupJobResponse::fromEntity)
                .toList();
    }

    public BackupStatusResponse runBackup(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.BACKUP_MANAGE);
        executeFullBackup(actorId, true);
        return buildStatus();
    }

    public int processCloudQueue(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.BACKUP_MANAGE);
        return processQueueInternal();
    }

    /**
     * Runs a backup from the setup wizard while the normal backup API is still
     * gated by the first-run setup guard. SetupService enforces administrator
     * access before this method is called.
     */
    public BackupStatusResponse runSetupBackup(Long actorId) {
        executeFullBackup(actorId == null ? 0L : actorId, true);
        return buildStatus();
    }

    /**
     * Compact status for the setup wizard. It deliberately avoids the
     * {@code pg_dump --version} probe so opening the wizard stays cheap.
     */
    public SetupBackupSummary setupSummary() {
        BackupJob last = lastLocalBackup();
        return SetupBackupSummary.builder()
                .automaticEnabled(systemSettingsService.isAutomaticBackupEnabled())
                .schedule(systemSettingsService.backupSchedule())
                .nextBackupAt(nextBackupAt())
                .retentionDays(systemSettingsService.backupRetentionDays())
                .cloudMode(normalizedCloudMode())
                .cloudTarget(cloudTarget())
                .cloudConfigured(isCloudConfigured())
                .lastBackupFile(last == null ? null : last.getFileName())
                .lastBackupStatus(last == null ? null : last.getStatus())
                .lastBackupAt(last == null ? null : last.getCompletedAt())
                .lastBackupSizeBytes(last == null ? null : last.getSizeBytes())
                .lastError(last == null ? null : last.getErrorMessage())
                .queuedCloudJobs(backupJobRepository.countByTierAndStatusIn("CLOUD", CLOUD_ACTIVE_STATUSES))
                .build();
    }

    @Scheduled(
            initialDelayString = "${sacco.backup.scheduler.initial-delay-ms:60000}",
            fixedDelayString = "${sacco.backup.scheduler.check-interval-ms:60000}")
    public void scheduledAutomaticBackup() {
        if (!automaticSchedulerEnabled || !systemSettingsService.isAutomaticBackupEnabled()) {
            return;
        }
        if (!isAutomaticBackupDue()) {
            return;
        }
        executeFullBackup(0L, false);
    }

    @Scheduled(
            initialDelayString = "${sacco.backup.cloud.initial-delay-ms:30000}",
            fixedDelayString = "${sacco.backup.cloud.retry-interval-ms:60000}")
    public void scheduledCloudRetry() {
        processQueueInternal();
    }

    private void executeFullBackup(Long actorId, boolean manual) {
        if (!backupRunning.compareAndSet(false, true)) {
            if (manual) {
                throw new IllegalStateException("A backup is already running");
            }
            return;
        }
        try {
            Path snapshot = createLocalSnapshot(actorId);
            if (snapshot != null) {
                mirrorToExternal(snapshot, actorId);
                enqueueCloud(snapshot, actorId);
                processQueueInternal();
                pruneExpiredSnapshots(actorId);
            }
        } finally {
            backupRunning.set(false);
        }
    }

    private Path createLocalSnapshot(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        String fileName = "sacco_" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".sql";
        BackupJob job = backupJobRepository.save(BackupJob.builder()
                .tier("LOCAL")
                .fileName(fileName)
                .status("RUNNING")
                .attempts(0)
                .build());

        try {
            Path directory = Path.of(localDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path output = directory.resolve(fileName);

            runPgDump(output);

            job.setLocalPath(output.toString());
            job.setSizeBytes(Files.size(output));
            job.setChecksum(checksum(output));
            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            backupJobRepository.save(job);

            auditService.logEvent(actorId, "BACKUP_COMPLETED", "BACKUP_JOB", job.getJobId(),
                    "Tier 1 local snapshot " + fileName);
            log.info("Tier 1 backup completed: {}", output);
            return output;
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(truncate(ex.getMessage(), 1900));
            backupJobRepository.save(job);
            auditService.logEvent(actorId, "BACKUP_FAILED", "BACKUP_JOB", job.getJobId(),
                    truncate(ex.getMessage(), 900));
            log.error("Tier 1 backup failed", ex);
            return null;
        }
    }

    private void runPgDump(Path output) throws IOException, InterruptedException {
        String executable = resolvePgDump();
        URI uri = URI.create(datasourceUrl.substring("jdbc:".length()));
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = uri.getPort() <= 0 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

        List<String> command = List.of(
                executable,
                "--host=" + host,
                "--port=" + port,
                "--username=" + datasourceUsername,
                "--dbname=" + database,
                "--format=plain",
                "--no-owner",
                "--no-privileges",
                "--encoding=UTF8",
                "--file=" + output.toAbsolutePath()
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", datasourcePassword);
        Process process = builder.start();

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("pg_dump timed out after 5 minutes");
        }
        if (process.exitValue() != 0) {
            throw new IOException("pg_dump failed with exit code " + process.exitValue()
                    + (processOutput.isBlank() ? "" : ": " + truncate(processOutput, 800)));
        }
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new IOException("pg_dump completed without producing a snapshot file");
        }
    }

    private void mirrorToExternal(Path source, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        BackupJob job = backupJobRepository.save(BackupJob.builder()
                .tier("EXTERNAL")
                .fileName(source.getFileName().toString())
                .localPath(source.toString())
                .status("RUNNING")
                .attempts(0)
                .build());

        try {
            if (externalDirectory == null || externalDirectory.isBlank()) {
                throw new IOException("External backup directory is not configured");
            }
            Path directory = Path.of(externalDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(source.getFileName());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            job.setTargetPath(target.toString());
            job.setSizeBytes(Files.size(target));
            job.setChecksum(checksum(target));
            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            backupJobRepository.save(job);

            auditService.logEvent(actorId, "BACKUP_EXTERNAL_MIRRORED", "BACKUP_JOB", job.getJobId(),
                    "Tier 2 mirror " + target);
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(truncate(ex.getMessage(), 1900));
            backupJobRepository.save(job);
            auditService.logEvent(actorId, "BACKUP_EXTERNAL_FAILED", "BACKUP_JOB", job.getJobId(),
                    truncate(ex.getMessage(), 900));
            log.warn("Tier 2 external mirror failed: {}", ex.getMessage());
        }
    }

    private void enqueueCloud(Path source, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        BackupJob job = backupJobRepository.save(BackupJob.builder()
                .tier("CLOUD")
                .fileName(source.getFileName().toString())
                .localPath(source.toString())
                .targetPath(cloudTarget())
                .status("QUEUED")
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .build());

        auditService.logEvent(actorId, "BACKUP_CLOUD_QUEUED", "BACKUP_JOB", job.getJobId(),
                "Tier 3 cloud queue entry " + job.getFileName());
    }

    private int processQueueInternal() {
        if (!cloudRunning.compareAndSet(false, true)) {
            return 0;
        }
        try {
            List<BackupJob> due = backupJobRepository
                    .findByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                            List.of("QUEUED", "RETRYING"),
                            LocalDateTime.now().plusSeconds(1));
            int processed = 0;
            for (BackupJob job : due) {
                attemptCloudUpload(job);
                processed++;
            }
            return processed;
        } finally {
            cloudRunning.set(false);
        }
    }

    private void attemptCloudUpload(BackupJob job) {
        LocalDateTime now = LocalDateTime.now();
        job.setStatus("RUNNING");
        job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
        job.setLastAttemptAt(now);
        backupJobRepository.save(job);

        try {
            String mode = normalizedCloudMode();
            if ("QUEUE_ONLY".equals(mode)) {
                job.setStatus("QUEUED");
                job.setErrorMessage("Queued persistently — configure an HTTP endpoint or cloud directory to upload");
                job.setNextAttemptAt(now.plusSeconds(Math.max(cloudRetryIntervalMs / 1000, 30)));
                backupJobRepository.save(job);
                return;
            }

            Path source = Path.of(job.getLocalPath());
            if (!Files.isRegularFile(source)) {
                throw new IOException("Local snapshot no longer exists: " + source);
            }

            if ("DIRECTORY".equals(mode)) {
                if (cloudDirectory == null || cloudDirectory.isBlank()) {
                    throw new IOException("Cloud directory is not configured");
                }
                Path directory = Path.of(cloudDirectory).toAbsolutePath().normalize();
                Files.createDirectories(directory);
                Path target = directory.resolve(job.getFileName());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                job.setTargetPath(target.toString());
            } else if ("HTTP".equals(mode)) {
                if (cloudEndpoint == null || cloudEndpoint.isBlank()) {
                    throw new IOException("Cloud HTTP endpoint is not configured");
                }
                uploadHttp(source, job);
                job.setTargetPath(cloudEndpoint);
            } else if ("GOOGLE_DRIVE".equals(mode)) {
                DriveUploadResult result = googleDriveService.uploadBackup(source, job.getFileName());
                job.setTargetPath(result.webViewLink() != null && !result.webViewLink().isBlank()
                        ? result.webViewLink()
                        : "Google Drive file " + result.fileId());
            } else {
                throw new IOException("Unknown cloud backup mode: " + mode);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            backupJobRepository.save(job);
            auditService.logEvent(0L, "BACKUP_CLOUD_UPLOADED", "BACKUP_JOB", job.getJobId(),
                    "Tier 3 upload completed to " + job.getTargetPath());
        } catch (Exception ex) {
            int attempts = job.getAttempts() == null ? 1 : job.getAttempts();
            long backoffSeconds = Math.min(Math.max(attempts, 1) * 60L,
                    Math.max(cloudRetryIntervalMs / 1000, 60));
            job.setStatus("RETRYING");
            job.setErrorMessage(truncate(ex.getMessage(), 1900));
            job.setNextAttemptAt(now.plusSeconds(backoffSeconds));
            backupJobRepository.save(job);
            if (attempts == 1 || attempts % 10 == 0) {
                auditService.logEvent(0L, "BACKUP_CLOUD_RETRY", "BACKUP_JOB", job.getJobId(),
                        "Tier 3 retry scheduled after: " + truncate(ex.getMessage(), 700));
            }
            log.warn("Tier 3 cloud upload attempt {} failed: {}", attempts, ex.getMessage());
        }
    }

    private void uploadHttp(Path source, BackupJob job) throws IOException, InterruptedException {
        String boundary = "----SCMSBackup" + UUID.randomUUID();
        byte[] fileBytes = Files.readAllBytes(source);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + job.getFileName().replace("\"", "") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(cloudEndpoint))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Backup-Checksum", job.getChecksum() == null ? "" : job.getChecksum())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Cloud endpoint returned HTTP " + response.statusCode()
                    + (response.body() == null || response.body().isBlank()
                    ? "" : ": " + truncate(response.body(), 500)));
        }
    }

    private BackupStatusResponse buildStatus() {
        List<BackupJobResponse> recent = backupJobRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .limit(12)
                .map(BackupJobResponse::fromEntity)
                .toList();

        long total = backupJobRepository.count();
        long queued = backupJobRepository.countByTierAndStatusIn("CLOUD", CLOUD_ACTIVE_STATUSES);
        long failed = backupJobRepository.countByStatus("FAILED");

        return BackupStatusResponse.builder()
                .localDirectory(Path.of(localDirectory).toAbsolutePath().normalize().toString())
                .externalDirectory(externalDirectory == null || externalDirectory.isBlank()
                        ? null
                        : Path.of(externalDirectory).toAbsolutePath().normalize().toString())
                .externalConfigured(externalDirectory != null && !externalDirectory.isBlank())
                .cloudMode(normalizedCloudMode())
                .cloudTarget(cloudTarget())
                .cloudConfigured(isCloudConfigured())
                .automaticBackupsEnabled(systemSettingsService.isAutomaticBackupEnabled())
                .backupSchedule(systemSettingsService.backupSchedule())
                .nextBackupAt(nextBackupAt())
                .retentionDays(systemSettingsService.backupRetentionDays())
                .backupRunning(backupRunning.get())
                .pgDumpAvailable(isPgDumpAvailable())
                .pgDumpPath(resolvePgDump())
                .queuedCloudJobs(queued)
                .failedJobs(failed)
                .totalJobs(total)
                .lastLocalBackup(BackupJobResponse.fromEntity(
                        backupJobRepository.findFirstByTierAndStatusOrderByCreatedAtDesc("LOCAL", "COMPLETED")
                                .orElse(null)))
                .lastExternalBackup(BackupJobResponse.fromEntity(
                        backupJobRepository.findFirstByTierAndStatusOrderByCreatedAtDesc("EXTERNAL", "COMPLETED")
                                .orElse(null)))
                .lastCloudUpload(BackupJobResponse.fromEntity(
                        backupJobRepository.findFirstByTierAndStatusOrderByCreatedAtDesc("CLOUD", "COMPLETED")
                                .orElse(null)))
                .recentJobs(recent)
                .build();
    }

    private String normalizedCloudMode() {
        return systemSettingsService.effectiveBackupMode();
    }

    private BackupJob lastLocalBackup() {
        return backupJobRepository
                .findFirstByTierAndStatusOrderByCreatedAtDesc("LOCAL", "COMPLETED")
                .orElse(null);
    }

    private boolean isAutomaticBackupDue() {
        LocalDateTime next = nextBackupAt();
        return next == null || !LocalDateTime.now().isBefore(next);
    }

    /**
     * Next time the background scheduler should take a snapshot. When the
     * application was closed past the due time this returns a moment in the
     * past, so the next scheduler tick catches up immediately.
     */
    private LocalDateTime nextBackupAt() {
        if (!systemSettingsService.isAutomaticBackupEnabled()) {
            return null;
        }
        BackupJob last = lastLocalBackup();
        if (last == null) {
            return LocalDateTime.now();
        }
        LocalDateTime anchor = last.getCompletedAt() != null
                ? last.getCompletedAt()
                : last.getCreatedAt();
        if (anchor == null) {
            return LocalDateTime.now();
        }
        return anchor.plus(BackupSchedule.from(systemSettingsService.backupSchedule()).interval());
    }

    /**
     * Keeps a rolling window of snapshots, like WhatsApp replacing old
     * backups. Only files created by this engine are considered.
     */
    private void pruneExpiredSnapshots(Long actorId) {
        int retentionDays = systemSettingsService.backupRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int removed = pruneDirectory(localDirectory, cutoff)
                + pruneDirectory(externalDirectory, cutoff);
        if (removed > 0) {
            auditService.logEvent(actorId, "BACKUP_RETENTION_PRUNED", "BACKUP_JOB", 0L,
                    "Removed " + removed + " snapshot(s) older than " + retentionDays + " days");
            log.info("Backup retention removed {} snapshot(s) older than {} days",
                    removed, retentionDays);
        }
    }

    private int pruneDirectory(String directory, LocalDateTime cutoff) {
        if (directory == null || directory.isBlank()) {
            return 0;
        }
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int removed = 0;
        try (var files = Files.list(dir)) {
            List<Path> expired = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("sacco_") && name.endsWith(".sql");
                    })
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant()
                                    .isBefore(cutoff.atZone(ZoneId.systemDefault()).toInstant());
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .toList();
            for (Path path : expired) {
                try {
                    Files.deleteIfExists(path);
                    removed++;
                } catch (IOException ex) {
                    log.warn("Could not prune expired backup {}: {}", path, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            log.warn("Could not inspect backup directory {} for retention: {}",
                    dir, ex.getMessage());
        }
        return removed;
    }

    private boolean isCloudConfigured() {
        return switch (normalizedCloudMode()) {
            case "DIRECTORY" -> cloudDirectory != null && !cloudDirectory.isBlank();
            case "HTTP" -> cloudEndpoint != null && !cloudEndpoint.isBlank();
            case "GOOGLE_DRIVE" -> googleDriveService.isConfigured();
            default -> false;
        };
    }

    private String cloudTarget() {
        return switch (normalizedCloudMode()) {
            case "DIRECTORY" -> cloudDirectory;
            case "HTTP" -> cloudEndpoint;
            case "GOOGLE_DRIVE" -> {
                SystemSettings settings = systemSettingsService.getExisting();
                String folderId = settings == null ? null : settings.getDriveFolderId();
                yield folderId == null || folderId.isBlank()
                        ? "Google Drive (folder not configured)"
                        : "Google Drive folder " + folderId;
            }
            default -> "Persistent PostgreSQL queue (awaiting cloud configuration)";
        };
    }

    private String resolvePgDump() {
        if (configuredPgDump != null && !configuredPgDump.isBlank()) {
            return configuredPgDump;
        }
        List<String> candidates = List.of(
                "C:\\Program Files\\PostgreSQL\\16\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\15\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\14\\bin\\pg_dump.exe"
        );
        for (String candidate : candidates) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return "pg_dump";
    }

    private boolean isPgDumpAvailable() {
        String executable = resolvePgDump();
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private String checksum(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
