package com.sacco.setup;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Blocks business APIs until the first-run setup is complete. The setup,
 * login, webhook and OTP paths remain reachable so the wizard can run and
 * provider callbacks are not lost during commissioning.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class SetupGuardFilter extends OncePerRequestFilter {

    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;

    @Value("${sacco.setup.enforce:true}")
    private boolean enforce;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!enforce || path == null || !path.startsWith("/api/") || isAllowed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (settingsService.isSetupCompleted()) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (Exception ex) {
            log.error("Could not read the system setup state", ex);
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "The system configuration could not be read. Please try again.");
            return;
        }

        writeError(response, HttpStatus.PRECONDITION_REQUIRED,
                "Complete the system setup before using this feature.");
    }

    private boolean isAllowed(String path) {
        return path.startsWith("/api/setup")
                || path.startsWith("/api/auth")
                || path.startsWith("/api/webhooks")
                || path.startsWith("/api/communications/otp");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(),
                "message", message,
                "setupRequired", status == HttpStatus.PRECONDITION_REQUIRED));
    }
}
