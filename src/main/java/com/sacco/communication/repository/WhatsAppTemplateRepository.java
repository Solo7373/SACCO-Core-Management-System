package com.sacco.communication.repository;

import com.sacco.communication.entity.WhatsAppTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, Long> {

    Optional<WhatsAppTemplate> findByNameAndLanguage(String name, String language);

    Optional<WhatsAppTemplate> findByMetaTemplateId(String metaTemplateId);

    List<WhatsAppTemplate> findAllByOrderByNameAscLanguageAsc();

    long countByStatus(WhatsAppTemplate.TemplateStatus status);
}
