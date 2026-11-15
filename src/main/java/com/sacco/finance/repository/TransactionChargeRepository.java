package com.sacco.finance.repository;

import com.sacco.finance.entity.TransactionCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionChargeRepository extends JpaRepository<TransactionCharge, Long> {
    List<TransactionCharge> findAllByOrderByCreatedAtDesc();
    List<TransactionCharge> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
    List<TransactionCharge> findByReference(String reference);
}
