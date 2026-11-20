package com.sacco.loan.repository;

import com.sacco.loan.entity.LoanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LoanScheduleRepository extends JpaRepository<LoanSchedule, Long> {
    List<LoanSchedule> findByLoanIdOrderByInstallmentNoAsc(Long loanId);
    void deleteByLoanId(Long loanId);
    List<LoanSchedule> findByStatusIn(Collection<LoanSchedule.ScheduleStatus> statuses);
    List<LoanSchedule> findByDueDateLessThanEqual(LocalDate dueDate);
}
