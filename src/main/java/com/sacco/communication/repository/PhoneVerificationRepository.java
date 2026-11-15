package com.sacco.communication.repository;

import com.sacco.communication.entity.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String phoneNumber,
            PhoneVerification.OtpPurpose purpose,
            LocalDateTime now);

    Optional<PhoneVerification> findFirstByPhoneNumberAndPurposeOrderByCreatedAtDesc(
            String phoneNumber,
            PhoneVerification.OtpPurpose purpose);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime after);

    long countByRequestIpAndCreatedAtAfter(String requestIp, LocalDateTime after);

    @Modifying
    @Query("""
            update PhoneVerification p
            set p.consumedAt = :now
            where p.phoneNumber = :phone
              and p.purpose = :purpose
              and p.consumedAt is null
            """)
    int invalidateActive(@Param("phone") String phone,
                         @Param("purpose") PhoneVerification.OtpPurpose purpose,
                         @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update PhoneVerification p
            set p.attempts = p.attempts + 1
            where p.verificationId = :id
            """)
    int incrementAttempts(@Param("id") Long id);

    /**
     * Atomically consumes a code only if it is still active. Returns 1 for the
     * winning verifier and 0 for a concurrent double-submit.
     */
    @Modifying
    @Query("""
            update PhoneVerification p
            set p.verifiedAt = :now, p.consumedAt = :now
            where p.verificationId = :id and p.consumedAt is null
            """)
    int markVerified(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from PhoneVerification p where p.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
