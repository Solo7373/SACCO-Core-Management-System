package com.sacco.audit;

import com.sacco.audit.entity.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(Long actorId, String action, String entityType, Long entityId, String reason) {
        String correlationId = UUID.randomUUID().toString();
        AuditEvent event = AuditEvent.builder()
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .reason(reason)
                .correlationId(correlationId)
                .build();
        auditEventRepository.save(event);
        log.info("Audit: {} on {} #{} by actor #{} [correlationId={}]", action, entityType, entityId, actorId, correlationId);
    }
}
