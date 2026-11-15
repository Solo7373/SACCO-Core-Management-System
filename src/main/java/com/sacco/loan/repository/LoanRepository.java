package com.sacco.loan.repository;

import com.sacco.loan.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<Loan> findByStatus(Loan.LoanStatus status);
    List<Loan> findByStatusIn(Collection<Loan.LoanStatus> statuses);
    List<Loan> findByMemberIdAndStatusIn(Long memberId, Collection<Loan.LoanStatus> statuses);
}
