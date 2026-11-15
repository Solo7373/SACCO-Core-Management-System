package com.sacco.loan.repository;

import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanGuarantor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LoanGuarantorRepository extends JpaRepository<LoanGuarantor, Long> {
    List<LoanGuarantor> findByLoanId(Long loanId);
    List<LoanGuarantor> findByMemberIdAndStatus(Long memberId, String status);

    /**
     * Guarantees that still expose the member to loss: pledged guarantees on
     * loans that are disbursed or in arrears.
     */
    @Query("""
            SELECT lg FROM LoanGuarantor lg, Loan l
            WHERE lg.loanId = l.loanId
              AND lg.memberId = :memberId
              AND lg.status = 'PLEDGED'
              AND l.status IN :loanStatuses
            """)
    List<LoanGuarantor> findActiveGuaranteesByMemberId(
            @Param("memberId") Long memberId,
            @Param("loanStatuses") Collection<Loan.LoanStatus> loanStatuses);
}
