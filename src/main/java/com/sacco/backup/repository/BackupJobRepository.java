package com.sacco.backup.repository;

import com.sacco.backup.entity.BackupJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupJobRepository extends JpaRepository<BackupJob, Long> {

    List<BackupJob> findTop100ByOrderByCreatedAtDesc();

    List<BackupJob> findByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            Collection<String> statuses, LocalDateTime cutoff);

    Optional<BackupJob> findFirstByTierAndStatusOrderByCreatedAtDesc(String tier, String status);

    long countByTierAndStatusIn(String tier, Collection<String> statuses);

    long countByStatus(String status);
}
