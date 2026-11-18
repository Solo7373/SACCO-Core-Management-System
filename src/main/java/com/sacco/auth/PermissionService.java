package com.sacco.auth;

import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central role-to-permission map. Endpoints and services call require(...)
 * before performing sensitive actions so a TELLER cannot approve loans and a
 * LOAN_OFFICER cannot disburse funds.
 *
 * Operational roles are split into two groups:
 *  - Input-only roles (TELLER, LOAN_OFFICER) may submit new records but may
 *    never edit, archive or restore a submitted record. Mistakes are corrected
 *    through inverse administrative reversals.
 *  - Control roles (ADMIN, RECOVERY_ADMIN, MANAGER, AUDITOR) may edit,
 *    soft-delete and restore records. The auditor compliance workspace remains
 *    restricted to ADMIN and AUDITOR, and financial reversals remain limited
 *    to ADMIN, RECOVERY_ADMIN and MANAGER.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRepository userRepository;

    public static final List<String> ROLES =
            List.of("ADMIN", "RECOVERY_ADMIN", "MANAGER", "TELLER",
                    "LOAN_OFFICER", "AUDITOR", "MEMBER");

    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = buildRolePermissions();

    private static Map<String, Set<Permission>> buildRolePermissions() {
        Map<String, Set<Permission>> map = new LinkedHashMap<>();

        map.put("ADMIN", EnumSet.allOf(Permission.class));

        // Break-glass recovery role. It carries the full operational toolkit so
        // an administrator can recover access and repair records after an
        // incident, but every action is still written to the audit trail.
        map.put("RECOVERY_ADMIN", EnumSet.allOf(Permission.class));

        // Managers run operations but cannot inspect the detailed audit trail,
        // compliance spreadsheet or tax breakdowns. Those are auditor-only.
        EnumSet<Permission> manager = EnumSet.allOf(Permission.class);
        manager.remove(Permission.AUDIT_VIEW);
        manager.remove(Permission.COMPLIANCE_VIEW);
        manager.remove(Permission.SETTINGS_MANAGE);
        map.put("MANAGER", manager);

        map.put("TELLER", EnumSet.of(
                Permission.ACCOUNT_VIEW,
                Permission.ACCOUNT_MANAGE,
                Permission.SAVINGS_VIEW,
                Permission.SAVINGS_POST,
                Permission.SHARE_VIEW,
                Permission.SHARE_RECORD,
                Permission.LOAN_VIEW,
                Permission.LOAN_REPAY,
                Permission.COLLECTION_VIEW,
                Permission.LOAN_COLLECT,
                Permission.COLLATERAL_MANAGE,
                Permission.MEMBER_MANAGE,
                Permission.INVENTORY_VIEW,
                Permission.SMS_VIEW,
                Permission.SMS_SEND,
                Permission.WHATSAPP_VIEW,
                Permission.WHATSAPP_SEND,
                Permission.EMAIL_VIEW,
                Permission.EMAIL_SEND
        ));

        map.put("LOAN_OFFICER", EnumSet.of(
                Permission.ACCOUNT_VIEW,
                Permission.SAVINGS_VIEW,
                Permission.SHARE_VIEW,
                Permission.LOAN_VIEW,
                Permission.LOAN_APPLY,
                Permission.LOAN_REVIEW,
                Permission.LOAN_REPAY,
                Permission.COLLECTION_VIEW,
                Permission.LOAN_COLLECT,
                Permission.LOAN_DEMAND,
                Permission.MEMBER_MANAGE,
                Permission.COLLATERAL_MANAGE,
                Permission.INVENTORY_VIEW,
                Permission.SMS_VIEW,
                Permission.SMS_SEND,
                Permission.WHATSAPP_VIEW,
                Permission.WHATSAPP_SEND,
                Permission.EMAIL_VIEW,
                Permission.EMAIL_SEND
        ));

        // Auditor workspace: compliance exports plus the control-role ability
        // to edit, archive and restore records. Financial posting and reversal
        // permissions are deliberately absent.
        map.put("AUDITOR", EnumSet.of(
                Permission.ACCOUNT_VIEW,
                Permission.ACCOUNT_EDIT,
                Permission.SAVINGS_VIEW,
                Permission.SHARE_VIEW,
                Permission.LOAN_VIEW,
                Permission.REPORT_VIEW,
                Permission.AUDIT_VIEW,
                Permission.COMPLIANCE_VIEW,
                Permission.COLLECTION_VIEW,
                Permission.TAX_VIEW,
                Permission.FEE_VIEW,
                Permission.INVENTORY_VIEW,
                Permission.MEMBER_EDIT,
                Permission.MEMBER_ARCHIVE,
                Permission.MEMBER_RESTORE,
                Permission.BACKUP_VIEW,
                Permission.SMS_VIEW,
                Permission.WHATSAPP_VIEW,
                Permission.EMAIL_VIEW
        ));

        map.put("MEMBER", EnumSet.of(
                Permission.ACCOUNT_VIEW,
                Permission.SAVINGS_VIEW,
                Permission.SHARE_VIEW,
                Permission.LOAN_VIEW,
                Permission.LOAN_APPLY
        ));

        return map;
    }

    public void require(Long actorId, Permission permission) {
        Long authenticated = SecurityUtils.currentUserId();
        Long effectiveActor = (authenticated != null) ? authenticated : actorId;
        if (effectiveActor == null) {
            throw new AccessDeniedException("An authenticated actor is required for this action");
        }
        final Long actor = effectiveActor;
        User user = userRepository.findById(actor)
                .orElseThrow(() -> new AccessDeniedException("Unknown actor: " + actor));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AccessDeniedException("This user account is deactivated");
        }
        if (!permissionsForRole(user.getRole()).contains(permission)) {
            throw new AccessDeniedException("Role " + user.getRole()
                    + " is not allowed to perform " + permission);
        }
    }

    public void requireSelfOrPrivileged(Long actorId, Long targetMemberId) {
        Long authenticated = SecurityUtils.currentUserId();
        Long effectiveActor = (authenticated != null) ? authenticated : actorId;
        if (effectiveActor == null || targetMemberId == null) {
            return;
        }
        final Long actor = effectiveActor;
        User user = userRepository.findById(actor).orElse(null);
        if (user == null || !"MEMBER".equals(user.getRole())) {
            return;
        }
        if (!targetMemberId.equals(user.getMemberId())) {
            throw new AccessDeniedException("Members can only access their own records.");
        }
    }

    public boolean isMember(Long actorId) {
        Long authenticated = SecurityUtils.currentUserId();
        Long effectiveActor = (authenticated != null) ? authenticated : actorId;
        if (effectiveActor == null) {
            return false;
        }
        return userRepository.findById(effectiveActor)
                .map(user -> "MEMBER".equals(user.getRole()))
                .orElse(false);
    }

    public Long memberIdForActor(Long actorId) {
        Long authenticated = SecurityUtils.currentUserId();
        Long effectiveActor = (authenticated != null) ? authenticated : actorId;
        if (effectiveActor == null) {
            return null;
        }
        return userRepository.findById(effectiveActor)
                .map(User::getMemberId)
                .orElse(null);
    }

    public Set<Permission> permissionsForRole(String role) {
        if (role == null) {
            return Set.of();
        }
        return ROLE_PERMISSIONS.getOrDefault(role.trim().toUpperCase(), Set.of());
    }

    public Map<String, Set<Permission>> allRolePermissions() {
        return ROLE_PERMISSIONS;
    }
}
