package com.sacco.communication;

import com.sacco.communication.entity.PhoneVerification;
import com.sacco.communication.repository.PhoneVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Short transactions for OTP persistence, kept out of the network path. */
@Service
@RequiredArgsConstructor
public class OtpVerificationWriter {

    private final PhoneVerificationRepository repository;

    @Transactional
    public void invalidateActive(String phone, PhoneVerification.OtpPurpose purpose) {
        repository.invalidateActive(phone, purpose, LocalDateTime.now());
    }

    @Transactional
    public PhoneVerification create(PhoneVerification verification) {
        return repository.save(verification);
    }

    @Transactional
    public void consume(Long verificationId) {
        repository.findById(verificationId).ifPresent(verification -> {
            verification.setConsumedAt(LocalDateTime.now());
            repository.save(verification);
        });
    }

    @Transactional
    public int incrementAttempts(Long verificationId) {
        return repository.incrementAttempts(verificationId);
    }

    /** Returns true only for the caller that actually consumes the code. */
    @Transactional
    public boolean markVerified(Long verificationId) {
        return repository.markVerified(verificationId, LocalDateTime.now()) == 1;
    }

    @Transactional
    public int purgeOlderThan(LocalDateTime cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}
