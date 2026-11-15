package com.sacco.audit.dto;

import com.sacco.audit.entity.AuditEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditEventResponse {
    private Long eventId;
    private Long actorId;
    private String action;
    private String entityType;
    private Long entityId;
    private String reason;
    private LocalDateTime timestamp;
    private String correlationId;

    public static AuditEventResponse fromEntity(AuditEvent event) {
        return AuditEventResponse.builder()
                .eventId(event.getEventId())
                .actorId(event.getActorId())
                .action(event.getAction())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .reason(event.getReason())
                .timestamp(event.getTimestamp())
                .correlationId(event.getCorrelationId())
                .build();
    }
}
