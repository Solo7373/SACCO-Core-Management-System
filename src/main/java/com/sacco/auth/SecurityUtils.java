package com.sacco.auth;

import com.sacco.auth.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the authenticated actor from the SecurityContext populated by
 * {@link JwtAuthFilter}. Falls back to {@code null} when no valid token is
 * present (anonymous request).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object details = auth.getDetails();
        if (details instanceof User u) {
            return u;
        }
        return null;
    }

    public static Long currentUserId() {
        User u = currentUser();
        return u == null ? null : u.getUserId();
    }

    public static String currentRole() {
        User u = currentUser();
        return u == null ? null : u.getRole();
    }

    public static Long currentMemberId() {
        User u = currentUser();
        return u == null ? null : u.getMemberId();
    }
}
