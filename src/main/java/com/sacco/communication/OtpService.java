package com.sacco.communication;

import com.sacco.audit.AuditService;
import com.sacco.communication.dto.OtpRequest;
import com.sacco.communication.dto.OtpRequestResponse;
import com.sacco.communication.dto.OtpVerifyRequest;
import com.sacco.communication.dto.OtpVerifyResponse;
import com.sacco.communication.entity.PhoneVerification;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.exception.OtpRateLimitException;
import com.sacco.communication.exception.OtpVerificationException;
import com.sacco.communication.exception.SmsProviderUnavailableException;
import com.sacco.communication.repository.PhoneVerificationRepository;
import com.sacco.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * OTP issuance and verification.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Codes are generated with {@link SecureRandom} and stored only as
 *       HMAC-SHA256 hashes keyed by a server-side pepper.</li>
 *   <li>Codes expire after 5 minutes, allow 5 attempts and are single-use.</li>
 *   <li>Per-phone and per-IP windows limit SMS pumping/toll fraud.</li>
 *   <li>A resend cooldown prevents rapid-fire requests.</li>
 *   <li>Verification is atomic, so two concurrent submits cannot both win.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final PhoneVerificationRepository repository;
    private final OtpVerificationWriter writer;
    private final PhoneNumberService phoneNumbers;
    private final SmsService smsService;
    private final MemberRepository memberRepository;
    private final AfricasTalkingProperties properties;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpRequestResponse request(OtpRequest request, Long requestedBy, String requestIp) {
        String phone = phoneNumbers.normalize(request.getPhone());
        PhoneVerification.OtpPurpose purpose = request.getPurpose();
        AfricasTalkingProperties.Otp otp = properties.getOtp();
        LocalDateTime now = LocalDateTime.now();

        if (otp.isRequireActiveMember()
                && memberRepository.findActiveByPhone(phone).isEmpty()) {
            // Deliberately generic: do not disclose whether a phone is registered.
            throw new OtpVerificationException(
                    "A verification code cannot be sent to this phone number.");
        }

        LocalDateTime windowStart = now.minusMinutes(otp.getWindowMinutes());
        if (repository.countByPhoneNumberAndCreatedAtAfter(phone, windowStart)
                >= otp.getMaxPerPhonePerWindow()) {
            throw new OtpRateLimitException(
                    "Too many verification codes requested for this number. Try again later.",
                    otp.getWindowMinutes() * 60);
        }
        if (requestIp != null && !requestIp.isBlank()
                && repository.countByRequestIpAndCreatedAtAfter(requestIp, windowStart)
                >= otp.getMaxPerIpPerWindow()) {
            throw new OtpRateLimitException(
                    "Too many verification codes requested from this network. Try again later.",
                    otp.getWindowMinutes() * 60);
        }

        repository.findFirstByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, purpose)
                .ifPresent(last -> {
                    LocalDateTime lastCreated = last.getCreatedAt();
                    if (lastCreated != null
                            && lastCreated.isAfter(now.minusSeconds(
                                    otp.getResendCooldownSeconds()))) {
                        long wait = Math.max(1, Duration.between(
                                now,
                                lastCreated.plusSeconds(otp.getResendCooldownSeconds()))
                                .getSeconds());
                        throw new OtpRateLimitException(
                                "Please wait " + wait
                                        + " seconds before requesting another code.",
                                wait);
                    }
                });

        writer.invalidateActive(phone, purpose);

        String code = generateCode(otp.getLength());
        PhoneVerification verification = PhoneVerification.builder()
                .reference(UUID.randomUUID().toString())
                .phoneNumber(phone)
                .purpose(purpose)
                .codeHash(hash(phone, purpose, code))
                .expiresAt(now.plusSeconds(otp.getTtlSeconds()))
                .attempts(0)
                .maxAttempts(otp.getMaxAttempts())
                .requestedBy(requestedBy)
                .requestIp(truncate(requestIp, 45))
                .createdAt(now)
                .build();

        try {
            verification = writer.create(verification);
        } catch (DataIntegrityViolationException exception) {
            // The partial unique index caught a simultaneous request.
            throw new OtpRateLimitException(
                    "A verification code was just requested. Please wait before trying again.",
                    otp.getResendCooldownSeconds());
        }

        String smsBody = "Your SCMS verification code is " + code
                + ". It expires in " + Math.max(1, otp.getTtlSeconds() / 60)
                + " minutes. Never share this code.";

        try {
            smsService.sendImmediate(
                    phone, smsBody, SmsMessage.SmsMessageType.OTP, null, null, requestedBy);
        } catch (RuntimeException exception) {
            // The code must not remain usable if the SMS could not be sent.
            writer.consume(verification.getVerificationId());
            throw exception;
        }

        auditService.logEvent(
                requestedBy == null ? 0L : requestedBy,
                "OTP_REQUESTED",
                "PHONE_VERIFICATION",
                verification.getVerificationId(),
                purpose.name() + " " + phoneNumbers.mask(phone));

        return new OtpRequestResponse(
                verification.getReference(),
                phoneNumbers.mask(phone),
                verification.getExpiresAt(),
                otp.getTtlSeconds(),
                "Verification code sent.");
    }

    public OtpVerifyResponse verify(OtpVerifyRequest request) {
        String phone = phoneNumbers.normalize(request.getPhone());
        PhoneVerification.OtpPurpose purpose = request.getPurpose();
        LocalDateTime now = LocalDateTime.now();

        PhoneVerification verification = repository
                .findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        phone, purpose, now)
                .orElseThrow(() -> new OtpVerificationException(
                        "No active verification code. Request a new one."));

        if (verification.getAttempts() >= verification.getMaxAttempts()) {
            writer.consume(verification.getVerificationId());
            throw new OtpVerificationException(
                    "Too many incorrect attempts. Request a new code.");
        }

        String expected = hash(phone, purpose, request.getCode().trim());
        if (!constantTimeEquals(verification.getCodeHash(), expected)) {
            int attempts = writer.incrementAttempts(verification.getVerificationId());
            if (attempts >= verification.getMaxAttempts()) {
                writer.consume(verification.getVerificationId());
                throw new OtpVerificationException(
                        "Too many incorrect attempts. Request a new code.");
            }
            throw new OtpVerificationException("Invalid verification code.");
        }

        if (!writer.markVerified(verification.getVerificationId())) {
            throw new OtpVerificationException(
                    "This verification code has already been used.");
        }

        auditService.logEvent(
                verification.getRequestedBy() == null ? 0L : verification.getRequestedBy(),
                "OTP_VERIFIED",
                "PHONE_VERIFICATION",
                verification.getVerificationId(),
                purpose.name() + " " + phoneNumbers.mask(phone));

        return new OtpVerifyResponse(true, phoneNumbers.mask(phone), now);
    }

    /** Removes old challenges; hashes are useless after expiry but should not accumulate. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpired() {
        int removed = writer.purgeOlderThan(LocalDateTime.now().minusDays(30));
        if (removed > 0) {
            log.info("Purged {} expired phone verification records", removed);
        }
    }

    String hash(String phone, PhoneVerification.OtpPurpose purpose, String code) {
        String pepper = properties.getOtp().getPepper();
        if (pepper == null || pepper.isBlank()) {
            throw new SmsProviderUnavailableException(
                    "OTP pepper is not configured. Set AT_OTP_PEPPER.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((phone + "|" + purpose.name() + "|" + code)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not hash the verification code", exception);
        }
    }

    private String generateCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int digit = index == 0
                    ? 1 + secureRandom.nextInt(9)
                    : secureRandom.nextInt(10);
            code.append(digit);
        }
        return code.toString();
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
