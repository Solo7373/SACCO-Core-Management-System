package com.sacco.loan.repository;

import com.sacco.loan.entity.LoanCollectionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanCollectionActionRepository extends JpaRepository<LoanCollectionAction, Long> {
    List<LoanCollectionAction> findByLoanIdOrderByCreatedAtDesc(Long loanId);
    Optional<LoanCollectionAction> findFirstByLoanIdOrderByCreatedAtDesc(Long loanId);
    Optional<LoanCollectionAction> findFirstByLoanIdAndActionTypeOrderByCreatedAtDesc(
            Long loanId, LoanCollectionAction.ActionType actionType);
    List<LoanCollectionAction> findByPromisedDateLessThanEqualAndPromisedDateIsNotNull(LocalDate date);
}
