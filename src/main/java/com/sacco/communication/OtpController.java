package com.sacco.communication;

import com.sacco.auth.SecurityUtils;
import com.sacco.communication.dto.OtpRequest;
import com.sacco.communication.dto.OtpRequestResponse;
import com.sacco.communication.dto.OtpVerifyRequest;
import com.sacco.communication.dto.OtpVerifyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public OTP endpoints used by login, registration and phone-change flows.
 * They are intentionally outside JWT authentication; the OTP itself and the
 * per-phone/per-IP rate limits are the security control. When a staff user is
 * already authenticated, the audit trail records their user id.
 */
@RestController
@RequestMapping("/api/communications/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/request")
    public ResponseEntity<OtpRequestResponse> request(
            @Valid @RequestBody OtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        Long requestedBy = SecurityUtils.currentUserId();
        return ResponseEntity.ok(otpService.request(request, requestedBy, clientIp));
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verify(
            @Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(otpService.verify(request));
    }
}
