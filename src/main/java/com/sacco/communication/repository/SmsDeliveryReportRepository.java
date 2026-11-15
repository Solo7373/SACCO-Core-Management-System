package com.sacco.communication.repository;

import com.sacco.communication.entity.SmsDeliveryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SmsDeliveryReportRepository extends JpaRepository<SmsDeliveryReport, Long> {

    List<SmsDeliveryReport> findTop100ByProcessedFalseAndReceivedAtAfterOrderByReportIdAsc(
            LocalDateTime after);

    @Modifying
    @Query("""
            update SmsDeliveryReport r
            set r.processed = true
            where r.processed = false and r.receivedAt < :cutoff
            """)
    int markOldReportsProcessed(@Param("cutoff") LocalDateTime cutoff);
}
