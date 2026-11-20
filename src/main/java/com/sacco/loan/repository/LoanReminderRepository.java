package com.sacco.loan.repository;

import com.sacco.loan.entity.LoanReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface LoanReminderRepository extends JpaRepository<LoanReminder, Long> {

    /**
     * Idempotency lookup. The explicit query is required because Spring Data
     * otherwise splits "DaysBefore" into a "Days" property plus a "Before"
     * keyword.
     */
    @Query("select r from LoanReminder r "
            + "where r.loanId = :loanId "
            + "and r.scheduleId = :scheduleId "
            + "and r.daysBefore = :daysBefore "
            + "and r.channel = :channel "
            + "and r.sendDate = :sendDate")
    Optional<LoanReminder> findByLoanIdAndScheduleIdAndDaysBeforeAndChannelAndSendDate(
            @Param("loanId") Long loanId,
            @Param("scheduleId") Long scheduleId,
            @Param("daysBefore") Integer daysBefore,
            @Param("channel") String channel,
            @Param("sendDate") LocalDate sendDate);

    Page<LoanReminder> findByStatusOrderByReminderIdDesc(
            LoanReminder.ReminderStatus status,
            Pageable pageable);

    Page<LoanReminder> findByStatusAndChannelOrderByReminderIdDesc(
            LoanReminder.ReminderStatus status,
            String channel,
            Pageable pageable);

    Page<LoanReminder> findByStatusAndLoanIdOrderByReminderIdDesc(
            LoanReminder.ReminderStatus status,
            Long loanId,
            Pageable pageable);

    /**
     * Staff history query. Every filter is optional so "All statuses" and
     * "All channels" really mean no filter rather than defaulting to PENDING.
     */
    @Query("select r from LoanReminder r "
            + "where (:status is null or r.status = :status) "
            + "and (:channel is null or r.channel = :channel) "
            + "and (:loanId is null or r.loanId = :loanId) "
            + "order by r.reminderId desc")
    Page<LoanReminder> search(@Param("status") LoanReminder.ReminderStatus status,
                              @Param("channel") String channel,
                              @Param("loanId") Long loanId,
                              Pageable pageable);
}
