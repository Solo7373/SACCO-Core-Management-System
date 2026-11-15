package com.sacco.auth;

import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the Bearer JWT on every request and populates the SecurityContext.
 * When no/invalid token is present, the context is left empty and the request
 * proceeds as anonymous (subject to SecurityConfig rules).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (jwtService.isValid(token)) {
                try {
                    Claims claims = jwtService.parseClaims(token);
                    Long userId = jwtService.getUserId(claims);
                    String role = jwtService.getRole(claims);
                    User user = userId == null ? null : userRepository.findById(userId).orElse(null);
                    if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user.getUsername(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                        auth.setDetails(user);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        request.setAttribute("authenticatedUserId", userId);
                        request.setAttribute("authenticatedUser", user);
                    }
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
