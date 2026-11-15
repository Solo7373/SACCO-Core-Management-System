package com.sacco.communication.repository;

import com.sacco.communication.entity.WhatsAppInboundMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppInboundMessageRepository
        extends JpaRepository<WhatsAppInboundMessage, Long> {

    Optional<WhatsAppInboundMessage> findByProviderMessageId(String providerMessageId);

    Page<WhatsAppInboundMessage> findAllByOrderByInboundIdDesc(Pageable pageable);

    long countByProcessedFalse();
}
