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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String PHONE = "+256772123456";
    private static final PhoneVerification.OtpPurpose PURPOSE =
            PhoneVerification.OtpPurpose.REGISTRATION;

    @Mock private PhoneVerificationRepository repository;
    @Mock private OtpVerificationWriter writer;
    @Mock private PhoneNumberService phoneNumbers;
    @Mock private SmsService smsService;
    @Mock private MemberRepository memberRepository;
    @Mock private AuditService auditService;

    private AfricasTalkingProperties properties;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        properties = new AfricasTalkingProperties();
        properties.getOtp().setPepper("test-pepper-that-is-long-and-stable");
        otpService = new OtpService(
                repository, writer, phoneNumbers, smsService,
                memberRepository, properties, auditService);
    }

    @Test
    void requestCreatesHashedChallengeAndSendsSms() {
        when(phoneNumbers.normalize("0772123456")).thenReturn(PHONE);
        when(phoneNumbers.mask(PHONE)).thenReturn("+2567****456");
        when(repository.countByPhoneNumberAndCreatedAtAfter(eq(PHONE), any()))
                .thenReturn(0L);
        when(repository.countByRequestIpAndCreatedAtAfter(eq("127.0.0.1"), any()))
                .thenReturn(0L);
        when(repository.findFirstByPhoneNumberAndPurposeOrderByCreatedAtDesc(PHONE, PURPOSE))
                .thenReturn(Optional.empty());
        when(writer.create(any(PhoneVerification.class))).thenAnswer(invocation -> {
            PhoneVerification verification = invocation.getArgument(0);
            verification.setVerificationId(1L);
            return verification;
        });

        OtpRequest request = new OtpRequest();
        request.setPhone("0772123456");
        request.setPurpose(PURPOSE);

        OtpRequestResponse response = otpService.request(request, 7L, "127.0.0.1");

        assertThat(response.reference()).isNotBlank();
        assertThat(response.maskedPhone()).isEqualTo("+2567****456");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());

        ArgumentCaptor<PhoneVerification> captor =
                ArgumentCaptor.forClass(PhoneVerification.class);
        verify(writer).create(captor.capture());
        PhoneVerification saved = captor.getValue();
        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(saved.getPurpose()).isEqualTo(PURPOSE);
        assertThat(saved.getCodeHash()).hasSize(64);
        assertThat(saved.getCodeHash()).doesNotContain("123456");
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getMaxAttempts()).isEqualTo(5);
        assertThat(saved.getRequestedBy()).isEqualTo(7L);
        assertThat(saved.getRequestIp()).isEqualTo("127.0.0.1");

        verify(writer).invalidateActive(PHONE, PURPOSE);
        verify(smsService).sendImmediate(
                eq(PHONE), contains("verification code"), eq(SmsMessage.SmsMessageType.OTP),
                isNull(), isNull(), eq(7L));
        verify(auditService).logEvent(
                eq(7L), eq("OTP_REQUESTED"), eq("PHONE_VERIFICATION"), eq(1L),
                contains("REGISTRATION"));
    }

    @Test
    void requestEnforcesPerPhoneWindowLimit() {
        when(phoneNumbers.normalize("0772123456")).thenReturn(PHONE);
        when(repository.countByPhoneNumberAndCreatedAtAfter(eq(PHONE), any()))
                .thenReturn(5L);

        OtpRequest request = new OtpRequest();
        request.setPhone("0772123456");
        request.setPurpose(PURPOSE);

        assertThatThrownBy(() -> otpService.request(request, null, null))
                .isInstanceOf(OtpRateLimitException.class)
                .satisfies(exception -> assertThat(
                        ((OtpRateLimitException) exception).getRetryAfterSeconds())
                        .isEqualTo(15 * 60));
    }

    @Test
    void requestEnforcesResendCooldown() {
        PhoneVerification previous = PhoneVerification.builder()
                .verificationId(1L)
                .phoneNumber(PHONE)
                .purpose(PURPOSE)
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(phoneNumbers.normalize("0772123456")).thenReturn(PHONE);
        when(repository.countByPhoneNumberAndCreatedAtAfter(eq(PHONE), any()))
                .thenReturn(1L);
        when(repository.findFirstByPhoneNumberAndPurposeOrderByCreatedAtDesc(PHONE, PURPOSE))
                .thenReturn(Optional.of(previous));

        OtpRequest request = new OtpRequest();
        request.setPhone("0772123456");
        request.setPurpose(PURPOSE);

        assertThatThrownBy(() -> otpService.request(request, null, null))
                .isInstanceOf(OtpRateLimitException.class)
                .satisfies(exception -> assertThat(
                        ((OtpRateLimitException) exception).getRetryAfterSeconds())
                        .isBetween(1L, 60L));
    }

    @Test
    void verifyAcceptsCorrectCodeExactlyOnce() {
        String code = "123456";
        PhoneVerification verification = verification(code, 0);

        when(phoneNumbers.normalize("0772123456")).thenReturn(PHONE);
        when(phoneNumbers.mask(PHONE)).thenReturn("+2567****456");
        when(repository
                .findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(PHONE), eq(PURPOSE), any()))
                .thenReturn(Optional.of(verification));
        when(writer.markVerified(1L)).thenReturn(true);

        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setPhone("0772123456");
        request.setPurpose(PURPOSE);
        request.setCode(code);

        OtpVerifyResponse response = otpService.verify(request);

        assertThat(response.verified()).isTrue();
        verify(writer).markVerified(1L);
        verify(auditService).logEvent(
                eq(7L), eq("OTP_VERIFIED"), eq("PHONE_VERIFICATION"), eq(1L),
                contains("REGISTRATION"));
    }

    @Test
    void verifyRejectsWrongCodeAndIncrementsAttempts() {
        PhoneVerification verification = verification("654321", 0);

        when(phoneNumbers.normalize("0772123456")).thenReturn(PHONE);
        when(repository
                .findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(PHONE), eq(PURPOSE), any()))
                .thenReturn(Optional.of(verification));
        when(writer.incrementAttempts(1L)).thenReturn(1);

        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setPhone("0772123456");
        request.setPurpose(PURPOSE);
        request.setCode("123456");

        assertThatThrownBy(() -> otpService.verify(request))
                .isInstanceOf(OtpVerificationException.class)
                .hasMessageContaining("Invalid");
        verify(writer).incrementAttempts(1L);
        verify(writer, never()).markVerified(any());
    }

    @Test
    void verifyLocksChallengeAfterMaximumAttempts() {
        PhoneVerification verification = verification("654321", 5);

        when(phoneNumbers.normalize("0772123456")).thenReturn(PHONE);
        when(repository
                .findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(PHONE), eq(PURPOSE), any()))
                .thenReturn(Optional.of(verification));

        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setPhone("0772123456");
        request.setPurpose(PURPOSE);
        request.setCode("123456");

        assertThatThrownBy(() -> otpService.verify(request))
                .isInstanceOf(OtpVerificationException.class)
                .hasMessageContaining("Too many");
        verify(writer).consume(1L);
    }

    @Test
    void hashingFailsClosedWithoutPepper() {
        properties.getOtp().setPepper("");

        assertThatThrownBy(() -> otpService.hash(PHONE, PURPOSE, "123456"))
                .isInstanceOf(SmsProviderUnavailableException.class)
                .hasMessageContaining("AT_OTP_PEPPER");
    }

    private PhoneVerification verification(String code, int attempts) {
        return PhoneVerification.builder()
                .verificationId(1L)
                .phoneNumber(PHONE)
                .purpose(PURPOSE)
                .codeHash(otpService.hash(PHONE, PURPOSE, code))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(attempts)
                .maxAttempts(5)
                .requestedBy(7L)
                .build();
    }
}
