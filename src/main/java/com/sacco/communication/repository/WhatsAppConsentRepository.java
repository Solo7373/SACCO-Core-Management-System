package com.sacco.communication.repository;

import com.sacco.communication.entity.WhatsAppConsent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppConsentRepository extends JpaRepository<WhatsAppConsent, Long> {

    Optional<WhatsAppConsent> findByPhoneNumber(String phoneNumber);

    Page<WhatsAppConsent> findAllByOrderByConsentIdDesc(Pageable pageable);

    boolean existsByPhoneNumberAndStatus(
            String phoneNumber, WhatsAppConsent.ConsentStatus status);

    long countByStatus(WhatsAppConsent.ConsentStatus status);
}
