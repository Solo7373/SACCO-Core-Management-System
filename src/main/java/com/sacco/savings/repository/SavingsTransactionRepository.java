package com.sacco.savings.repository;

import com.sacco.savings.entity.SavingsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {
    List<SavingsTransaction> findByAccountId(Long accountId);
    List<SavingsTransaction> findByAccountIdAndStatus(Long accountId, SavingsTransaction.SavingsTransactionStatus status);
    List<SavingsTransaction> findByStatus(SavingsTransaction.SavingsTransactionStatus status);
    List<SavingsTransaction> findByAccountIdAndValueDateBetween(Long accountId, LocalDate start, LocalDate end);
}
