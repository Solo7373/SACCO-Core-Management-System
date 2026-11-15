package com.sacco.loan;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.auth.repository.UserRepository;
import com.sacco.common.NumberSequenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.loan.dto.CollectionActionRequest;
import com.sacco.loan.dto.CollectionSummary;
import com.sacco.loan.dto.LoanDemandRequest;
import com.sacco.loan.dto.LoanDemandResponse;
import com.sacco.loan.dto.LoanScheduleResponse;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanCollectionAction;
import com.sacco.loan.entity.LoanDemand;
import com.sacco.loan.entity.LoanProduct;
import com.sacco.loan.entity.LoanSchedule;
import com.sacco.loan.repository.LoanCollectionActionRepository;
import com.sacco.loan.repository.LoanDemandRepository;
import com.sacco.loan.repository.LoanGuarantorRepository;
import com.sacco.loan.repository.LoanProductRepository;
import com.sacco.loan.repository.LoanRepaymentRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.loan.repository.LoanScheduleRepository;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanCollectionServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private LoanScheduleRepository loanScheduleRepository;
    @Mock private LoanProductRepository loanProductRepository;
    @Mock private LoanRepaymentRepository loanRepaymentRepository;
    @Mock private LoanGuarantorRepository loanGuarantorRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private LoanDemandRepository loanDemandRepository;
    @Mock private LoanCollectionActionRepository collectionActionRepository;
    @Mock private NumberSequenceService numberSequenceService;
    @Mock private AuditService auditService;
    @Mock private PermissionService permissionService;
    @Mock private LoanService loanService;
    @Mock private DemandLetterService demandLetterService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private LoanCollectionService collectionService;

    private Loan loan;
    private Member member;
    private LoanProduct product;

    @BeforeEach
    void setUp() {
        loan = Loan.builder()
                .loanId(1L)
                .loanNumber("LNS-2026-0001")
                .memberId(2L)
                .productId(3L)
                .amount(new BigDecimal("1000000.00"))
                .interestRate(new BigDecimal("12.000"))
                .termMonths(12)
                .status(Loan.LoanStatus.DISBURSED)
                .disbursedAt(LocalDateTime.now().minusMonths(2))
                .build();

        member = Member.builder()
                .memberId(2L)
                .membershipNo("SCM-2026-0001")
                .firstName("Jane")
                .lastName("Doe")
                .phone("+256712345678")
                .email("jane@example.com")
                .build();

        product = LoanProduct.builder()
                .productId(3L)
                .name("Development Loan")
                .penaltyRate(new BigDecimal("2.000"))
                .build();
    }

    private LoanSchedule overdueInstallment(int daysOverdue) {
        return LoanSchedule.builder()
                .scheduleId(10L)
                .loanId(1L)
                .installmentNo(1)
                .dueDate(LocalDate.now().minusDays(daysOverdue))
                .principalDue(new BigDecimal("100000.00"))
                .interestDue(new BigDecimal("10000.00"))
                .totalDue(new BigDecimal("110000.00"))
                .principalPaid(BigDecimal.ZERO)
                .interestPaid(BigDecimal.ZERO)
                .penaltyDue(BigDecimal.ZERO)
                .penaltyPaid(BigDecimal.ZERO)
                .build();
    }

    private void stubPortfolio(LoanSchedule schedule) {
        when(loanRepository.findByStatusIn(any())).thenReturn(List.of(loan));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(loanProductRepository.findById(3L)).thenReturn(Optional.of(product));
        when(loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(1L))
                .thenReturn(List.of(schedule));
        when(collectionActionRepository.findFirstByLoanIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(collectionActionRepository.findFirstByLoanIdAndActionTypeOrderByCreatedAtDesc(
                1L, LoanCollectionAction.ActionType.PROMISE_TO_PAY))
                .thenReturn(Optional.empty());
        when(loanDemandRepository.findFirstByLoanIdOrderByIssuedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(loanDemandRepository.countByLoanId(1L)).thenReturn(0L);
        when(loanDemandRepository.countByStatusIn(any())).thenReturn(0L);
        when(loanDemandRepository.countByIssuedAtBetween(any(), any())).thenReturn(0L);
        when(loanRepaymentRepository.findByPaymentDateBetween(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void summaryShouldAgeOverdueInstallmentsAndIncludeAccruedPenalty() {
        stubPortfolio(overdueInstallment(45));

        CollectionSummary summary = collectionService.getSummary(1L);

        assertThat(summary.getActiveLoans()).isEqualTo(1);
        assertThat(summary.getDelinquentLoans()).isEqualTo(1);
        assertThat(summary.getTotalArrears()).isEqualByComparingTo("110000.00");
        assertThat(summary.getTotalOutstanding()).isEqualByComparingTo("110000.00");
        assertThat(summary.getTotalPenalty()).isEqualByComparingTo("3300.00");
        assertThat(summary.getParPercent()).isEqualByComparingTo("100.00");
        assertThat(summary.getBuckets())
                .filteredOn(bucket -> bucket.getBucket().equals("31-60"))
                .singleElement()
                .satisfies(bucket -> {
                    assertThat(bucket.getLoanCount()).isEqualTo(1);
                    assertThat(bucket.getAmount()).isEqualByComparingTo("110000.00");
                });
        verify(permissionService).require(1L, Permission.COLLECTION_VIEW);
    }

    private void stubLoanLookup() {
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(loanProductRepository.findById(3L)).thenReturn(Optional.of(product));
    }

    private LoanScheduleResponse scheduleResponse(int daysOverdue) {
        return LoanScheduleResponse.builder()
                .scheduleId(10L)
                .installmentNo(1)
                .dueDate(LocalDate.now().minusDays(daysOverdue))
                .principalDue(new BigDecimal("100000.00"))
                .interestDue(new BigDecimal("10000.00"))
                .totalDue(new BigDecimal("110000.00"))
                .principalPaid(BigDecimal.ZERO)
                .interestPaid(BigDecimal.ZERO)
                .penaltyDue(BigDecimal.ZERO)
                .penaltyPaid(BigDecimal.ZERO)
                .balance(new BigDecimal("110000.00"))
                .daysOverdue(daysOverdue)
                .accruedPenalty(new BigDecimal("3300.00"))
                .status("OVERDUE")
                .build();
    }

    @Test
    void issueDemandShouldSnapshotArrearsAndStage() throws Exception {
        stubLoanLookup();
        when(loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(1L))
                .thenReturn(List.of(overdueInstallment(45)));
        when(loanService.getScheduleSnapshot(1L))
                .thenReturn(List.of(scheduleResponse(45)));
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"installmentNo\":1}]");
        when(numberSequenceService.formatted("loan_demand", "LDN"))
                .thenReturn("LDN-2026-0001");
        when(loanDemandRepository.save(any(LoanDemand.class))).thenAnswer(invocation -> {
            LoanDemand demand = invocation.getArgument(0);
            demand.setDemandId(99L);
            return demand;
        });
        when(collectionActionRepository.save(any(LoanCollectionAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LoanDemandRequest request = new LoanDemandRequest();
        request.setDeliveryMethod(LoanDemand.DeliveryMethod.PRINT);

        LoanDemandResponse response = collectionService.issueDemand(1L, request, 1L);

        assertThat(response.getDemandNumber()).isEqualTo("LDN-2026-0001");
        assertThat(response.getStage()).isEqualTo("FINAL_DEMAND");
        assertThat(response.getAmountDemanded()).isEqualByComparingTo("113300.00");
        assertThat(response.getDaysOverdue()).isEqualTo(45);
        assertThat(response.getDocumentSeal()).hasSize(64);
        ArgumentCaptor<LoanDemand> demandCaptor = ArgumentCaptor.forClass(LoanDemand.class);
        verify(loanDemandRepository).save(demandCaptor.capture());
        assertThat(demandCaptor.getValue().getScheduleSnapshot())
                .isEqualTo("[{\"installmentNo\":1}]");
        verify(auditService).logEvent(eq(1L), eq("LOAN_DEMAND_ISSUED"),
                eq("LOAN_DEMAND"), eq(99L), any(String.class));
    }

    @Test
    void issueDemandShouldRejectLoanWithoutOverdueInstallments() {
        stubLoanLookup();
        when(loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(1L))
                .thenReturn(List.of(overdueInstallment(-10)));

        LoanDemandRequest request = new LoanDemandRequest();

        assertThatThrownBy(() -> collectionService.issueDemand(1L, request, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no overdue installments");
    }

    @Test
    void promiseToPayShouldRequireAmountAndDate() {
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        CollectionActionRequest request = new CollectionActionRequest();
        request.setActionType(LoanCollectionAction.ActionType.PROMISE_TO_PAY);
        request.setOutcome(LoanCollectionAction.Outcome.PROMISE_TO_PAY);

        assertThatThrownBy(() -> collectionService.recordAction(1L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive promised amount");
    }

    @Test
    void demandHistoryShouldEnforceMemberOwnership() {
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(loanDemandRepository.findByLoanIdOrderByIssuedAtDesc(1L))
                .thenReturn(List.of());

        collectionService.getDemandsForLoan(1L, 7L);

        verify(permissionService).require(7L, Permission.LOAN_VIEW);
        verify(permissionService).requireSelfOrPrivileged(7L, 2L);
    }
}
