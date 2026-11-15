package com.sacco.loan.repository;

import com.sacco.loan.entity.LoanDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanDemandRepository extends JpaRepository<LoanDemand, Long> {
    List<LoanDemand> findByLoanIdOrderByIssuedAtDesc(Long loanId);
    List<LoanDemand> findAllByOrderByIssuedAtDesc();
    Optional<LoanDemand> findFirstByLoanIdOrderByIssuedAtDesc(Long loanId);
    long countByLoanId(Long loanId);
    long countByIssuedAtBetween(LocalDateTime from, LocalDateTime to);
    long countByStatusIn(List<LoanDemand.DemandStatus> statuses);
}
