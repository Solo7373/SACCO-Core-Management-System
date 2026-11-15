package com.sacco.communication.repository;

import com.sacco.communication.entity.SmsOptOut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmsOptOutRepository extends JpaRepository<SmsOptOut, Long> {

    Optional<SmsOptOut> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndActiveTrue(String phoneNumber);
}
