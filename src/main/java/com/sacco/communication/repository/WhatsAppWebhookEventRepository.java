package com.sacco.communication.repository;

import com.sacco.communication.entity.WhatsAppWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppWebhookEventRepository
        extends JpaRepository<WhatsAppWebhookEvent, Long> {

    Optional<WhatsAppWebhookEvent> findByEventKey(String eventKey);

    List<WhatsAppWebhookEvent> findTop100ByProcessedFalseAndReceivedAtAfterOrderByEventIdAsc(
            LocalDateTime after);

    @Modifying
    @Query("""
            update WhatsAppWebhookEvent e
            set e.processed = true, e.processedAt = :now
            where e.processed = false and e.receivedAt < :cutoff
            """)
    int markOldEventsProcessed(@Param("cutoff") LocalDateTime cutoff,
                               @Param("now") LocalDateTime now);
}
