package com.sacco.communication.repository;

import com.sacco.communication.entity.WhatsAppMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    Optional<WhatsAppMessage> findByReference(String reference);

    Optional<WhatsAppMessage> findFirstByProviderMessageIdOrderByMessageIdAsc(
            String providerMessageId);

    Page<WhatsAppMessage> findAllByOrderByMessageIdDesc(Pageable pageable);

    Page<WhatsAppMessage> findByStatusOrderByMessageIdDesc(
            WhatsAppMessage.WhatsAppStatus status, Pageable pageable);

    Page<WhatsAppMessage> findByRecipientContainingOrderByMessageIdDesc(
            String recipient, Pageable pageable);

    Page<WhatsAppMessage> findByStatusAndRecipientContainingOrderByMessageIdDesc(
            WhatsAppMessage.WhatsAppStatus status, String recipient, Pageable pageable);

    long countByStatus(WhatsAppMessage.WhatsAppStatus status);

    /**
     * Atomically claims a batch for one dispatcher instance. SKIP LOCKED lets
     * multiple application instances run the dispatcher without double-sending.
     */
    @Query(value = """
            SELECT * FROM whatsapp_messages
            WHERE status = 'QUEUED' AND next_attempt_at <= :now
            ORDER BY message_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WhatsAppMessage> claimBatch(@Param("now") LocalDateTime now,
                                     @Param("limit") int limit);

    /** Recovers rows left in SENDING when a dispatcher crashed mid-send. */
    @Modifying
    @Query("""
            update WhatsAppMessage m
            set m.status = :queued, m.nextAttemptAt = :now
            where m.status = :sending
              and (m.lastAttemptAt is null or m.lastAttemptAt < :cutoff)
            """)
    int resetStaleSending(@Param("sending") WhatsAppMessage.WhatsAppStatus sending,
                          @Param("queued") WhatsAppMessage.WhatsAppStatus queued,
                          @Param("now") LocalDateTime now,
                          @Param("cutoff") LocalDateTime cutoff);

    /**
     * Moves a QUEUED row to SENDING exactly once. Returns 1 when this caller
     * won the race, 0 when another worker or an immediate send already claimed it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WhatsAppMessage m
            set m.status = :sending, m.lastAttemptAt = :now
            where m.messageId = :id and m.status = :queued
            """)
    int beginAttempt(@Param("id") Long id,
                     @Param("queued") WhatsAppMessage.WhatsAppStatus queued,
                     @Param("sending") WhatsAppMessage.WhatsAppStatus sending,
                     @Param("now") LocalDateTime now);
}
