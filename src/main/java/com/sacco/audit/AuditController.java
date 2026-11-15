package com.sacco.audit;

import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository auditEventRepository;
    private final PermissionService permissionService;

    @GetMapping("/events")
    public ResponseEntity<List<AuditEventResponse>> getRecentEvents(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.AUDIT_VIEW);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<AuditEventResponse> events = auditEventRepository.findTop100ByOrderByTimestampDesc().stream()
                .limit(safeLimit)
                .map(AuditEventResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(events);
    }
}
