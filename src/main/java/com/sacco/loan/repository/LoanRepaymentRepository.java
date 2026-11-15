package com.sacco.loan.repository;

import com.sacco.loan.entity.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    List<LoanRepayment> findByLoanIdOrderByCreatedAtDesc(Long loanId);
    List<LoanRepayment> findByPaymentDateBetween(LocalDate from, LocalDate to);
}
