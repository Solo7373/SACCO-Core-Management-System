package com.sacco.loan;

import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.common.NumberSequenceService;
import com.sacco.common.ReferenceNumberService;
import com.sacco.loan.dto.*;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanGuarantor;
import com.sacco.loan.entity.LoanProduct;
import com.sacco.loan.entity.LoanRepayment;
import com.sacco.loan.entity.LoanSchedule;
import com.sacco.loan.repository.LoanGuarantorRepository;
import com.sacco.loan.repository.LoanProductRepository;
import com.sacco.loan.repository.LoanRepaymentRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.loan.repository.LoanScheduleRepository;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.savings.SavingsService;
import com.sacco.share.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private final LoanProductRepository loanProductRepository;
    private final LoanRepository loanRepository;
    private final LoanScheduleRepository loanScheduleRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanGuarantorRepository loanGuarantorRepository;
    private final MemberRepository memberRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountRepository accountRepository;
    private final SavingsService savingsService;
    private final ShareService shareService;
    private final NumberSequenceService numberSequenceService;
    private final ReferenceNumberService referenceNumberService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Transactional
    public LoanProductResponse createProduct(LoanProductRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_CONFIG);
        if (loanProductRepository.findByName(request.getName().trim()).isPresent()) {
            throw new IllegalStateException("A loan product named '" + request.getName().trim() + "' already exists");
        }
        if (request.getInterestRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Interest rate cannot be negative");
        }
        if (request.getMaxTermMonths() == null || request.getMaxTermMonths() < 1) {
            throw new IllegalStateException("Maximum term must be at least one month");
        }
        if (request.getMaxMultiplier() != null && request.getMaxMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Maximum multiplier must be greater than zero");
        }
        if (request.getMinAmount() != null && request.getMaxAmount() != null
                && request.getMinAmount().compareTo(request.getMaxAmount()) > 0) {
            throw new IllegalStateException("Minimum amount cannot exceed maximum amount");
        }

        LoanProduct product = LoanProduct.builder()
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .interestRate(request.getInterestRate())
                .interestMethod(request.getInterestMethod() == null
                        ? LoanProduct.InterestMethod.REDUCING_BALANCE : request.getInterestMethod())
                .maxTermMonths(request.getMaxTermMonths())
                .minAmount(request.getMinAmount() == null ? BigDecimal.ZERO : request.getMinAmount())
                .maxAmount(request.getMaxAmount())
                .maxMultiplier(request.getMaxMultiplier() == null ? new BigDecimal("3.00") : request.getMaxMultiplier())
                .penaltyRate(request.getPenaltyRate() == null ? BigDecimal.ZERO : request.getPenaltyRate())
                .requiresGuarantor(Boolean.TRUE.equals(request.getRequiresGuarantor()))
                .status("ACTIVE")
                .build();
        LoanProduct saved = loanProductRepository.save(product);
        auditService.logEvent(actorId, "LOAN_PRODUCT_CREATED", "LOAN_PRODUCT", saved.getProductId(),
                "Name: " + saved.getName() + ", Rate: " + saved.getInterestRate() + "%");
        return LoanProductResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getProducts() {
        return loanProductRepository.findAll().stream().map(LoanProductResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getActiveProducts() {
        return loanProductRepository.findByStatus("ACTIVE").stream().map(LoanProductResponse::fromEntity).toList();
    }

    @Transactional
    public LoanResponse apply(LoanApplicationRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_APPLY);
        permissionService.requireSelfOrPrivileged(actorId, request.getMemberId());
        Member member = findMember(request.getMemberId());
        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new IllegalStateException("Only active members can apply for a loan");
        }
        LoanProduct product = findProduct(request.getProductId());
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new IllegalStateException("This loan product is not active");
        }
        if (request.getAmount().compareTo(product.getMinAmount()) < 0) {
            throw new IllegalStateException("Minimum amount for " + product.getName() + " is " + product.getMinAmount());
        }
        if (product.getMaxAmount() != null && request.getAmount().compareTo(product.getMaxAmount()) > 0) {
            throw new IllegalStateException("Maximum amount for " + product.getName() + " is " + product.getMaxAmount());
        }
        if (request.getTermMonths() > product.getMaxTermMonths()) {
            throw new IllegalStateException("Maximum term for " + product.getName() + " is "
                    + product.getMaxTermMonths() + " months");
        }
        validateGuarantors(request, product);

        Loan loan = Loan.builder()
                .loanNumber(numberSequenceService.formatted("LOAN", "LNS"))
                .memberId(member.getMemberId())
                .productId(product.getProductId())
                .amount(request.getAmount())
                .interestRate(product.getInterestRate())
                .interestMethod(product.getInterestMethod())
                .termMonths(request.getTermMonths())
                .purpose(trimToNull(request.getPurpose()))
                .status(Loan.LoanStatus.PENDING)
                .applicationDate(LocalDate.now())
                .build();
        Loan saved = loanRepository.save(loan);

        for (GuarantorRequest guarantorRequest : request.getGuarantors()) {
            loanGuarantorRepository.save(LoanGuarantor.builder()
                    .loanId(saved.getLoanId())
                    .memberId(guarantorRequest.getMemberId())
                    .guaranteedAmount(guarantorRequest.getGuaranteedAmount())
                    .status("PLEDGED")
                    .build());
        }

        auditService.logEvent(actorId, "LOAN_APPLICATION_SUBMITTED", "LOAN", saved.getLoanId(),
                "Member: " + member.getMemberId() + ", Amount: " + saved.getAmount()
                        + ", Product: " + product.getName());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse review(Long loanId, LoanReviewRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_REVIEW);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != Loan.LoanStatus.PENDING) {
            throw new IllegalStateException("Only PENDING loans can be reviewed");
        }
        LoanEligibilityResponse eligibility = checkEligibility(loan.getMemberId(), loan.getProductId(), loan.getAmount());
        String notes = request == null ? null : trimToNull(request.getNotes());
        String eligibilitySummary = "Savings " + eligibility.getSavingsBalance()
                + ", Shares " + eligibility.getShareValue()
                + ", Maximum eligible " + eligibility.getMaximumEligible()
                + (eligibility.isEligible() ? " - eligible" : " - exceeds capacity");
        loan.setEligibilityNotes(notes == null ? eligibilitySummary : notes + " | " + eligibilitySummary);
        loan.setStatus(Loan.LoanStatus.UNDER_REVIEW);
        loan.setReviewedBy(actorId);
        loan.setReviewedAt(LocalDateTime.now());
        Loan saved = loanRepository.save(loan);
        auditService.logEvent(actorId, "LOAN_REVIEWED", "LOAN", loanId, loan.getEligibilityNotes());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse approve(Long loanId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_APPROVE);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != Loan.LoanStatus.PENDING && loan.getStatus() != Loan.LoanStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Only PENDING or UNDER_REVIEW loans can be approved");
        }
        loan.setStatus(Loan.LoanStatus.APPROVED);
        loan.setApprovedBy(actorId);
        loan.setApprovedAt(LocalDateTime.now());
        Loan saved = loanRepository.save(loan);
        auditService.logEvent(actorId, "LOAN_APPROVED", "LOAN", loanId, "Amount: " + loan.getAmount());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse reject(Long loanId, LoanRejectRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_APPROVE);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() == Loan.LoanStatus.DISBURSED || loan.getStatus() == Loan.LoanStatus.IN_ARREARS
                || loan.getStatus() == Loan.LoanStatus.CLOSED || loan.getStatus() == Loan.LoanStatus.REJECTED) {
            throw new IllegalStateException("This loan can no longer be rejected");
        }
        loan.setStatus(Loan.LoanStatus.REJECTED);
        loan.setRejectedBy(actorId);
        loan.setRejectedAt(LocalDateTime.now());
        loan.setRejectionReason(request.getReason());
        Loan saved = loanRepository.save(loan);
        auditService.logEvent(actorId, "LOAN_REJECTED", "LOAN", loanId, request.getReason());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse disburse(Long loanId, LoanDisbursementRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_DISBURSE);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != Loan.LoanStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED loans can be disbursed");
        }
        if (request != null && request.getDisbursementAccountId() != null
                && !accountRepository.existsById(request.getDisbursementAccountId())) {
            throw new IllegalArgumentException("Disbursement account not found");
        }

        loanScheduleRepository.deleteByLoanId(loanId);
        List<LoanSchedule> schedule = buildSchedule(loan);
        loanScheduleRepository.saveAll(schedule);

        BigDecimal totalInterest = schedule.stream()
                .map(LoanSchedule::getInterestDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        loan.setStatus(Loan.LoanStatus.DISBURSED);
        loan.setDisbursedBy(actorId);
        loan.setDisbursedAt(LocalDateTime.now());
        loan.setDisbursementAccountId(request == null ? null : request.getDisbursementAccountId());
        loan.setOutstandingPrincipal(loan.getAmount());
        loan.setOutstandingInterest(totalInterest);
        loan.setNextDueDate(schedule.isEmpty() ? null : schedule.get(0).getDueDate());
        Loan saved = loanRepository.save(loan);
        auditService.logEvent(actorId, "LOAN_DISBURSED", "LOAN", loanId,
                "Amount: " + loan.getAmount() + ", Term: " + loan.getTermMonths() + " months");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoan(Long loanId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_VIEW);
        Loan loan = findLoan(loanId);
        permissionService.requireSelfOrPrivileged(actorId, loan.getMemberId());
        return toResponse(loan);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getAllLoans(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_VIEW);
        List<Loan> loans;
        if (permissionService.isMember(actorId)) {
            Long memberId = permissionService.memberIdForActor(actorId);
            loans = memberId == null ? List.of()
                    : loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        } else {
            loans = loanRepository.findAll();
        }
        return loans.stream()
                .sorted(Comparator.comparing(Loan::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getLoansByMember(Long memberId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_VIEW);
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getArrears(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_VIEW);
        if (permissionService.isMember(actorId)) {
            throw new AccessDeniedException("Members cannot view the portfolio arrears list");
        }
        return loanRepository.findByStatusIn(List.of(Loan.LoanStatus.DISBURSED, Loan.LoanStatus.IN_ARREARS))
                .stream()
                .map(this::toResponse)
                .filter(loan -> loan.getAmountInArrears().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanScheduleResponse> getSchedule(Long loanId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_VIEW);
        Loan loan = findLoan(loanId);
        permissionService.requireSelfOrPrivileged(actorId, loan.getMemberId());
        return getScheduleSnapshot(loanId);
    }

    /**
     * Raw schedule snapshot used by the collections engine and demand-letter
     * renderer. Callers are responsible for authorisation.
     */
    @Transactional(readOnly = true)
    public List<LoanScheduleResponse> getScheduleSnapshot(Long loanId) {
        Loan loan = findLoan(loanId);
        LoanProduct product = findProduct(loan.getProductId());
        return buildScheduleResponses(loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loanId),
                product, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<LoanRepaymentResponse> getRepayments(Long loanId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_VIEW);
        Loan loan = findLoan(loanId);
        permissionService.requireSelfOrPrivileged(actorId, loan.getMemberId());
        return loanRepaymentRepository.findByLoanIdOrderByCreatedAtDesc(loanId).stream()
                .map(LoanRepaymentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanEligibilityResponse checkEligibility(Long memberId, Long productId, BigDecimal requestedAmount) {
        Member member = findMember(memberId);
        LoanProduct product = findProduct(productId);

        BigDecimal savingsBalance = BigDecimal.ZERO;
        List<Long> accountIds = accountHolderRepository.findByMemberId(memberId).stream()
                .map(AccountHolder::getAccountId)
                .toList();
        if (!accountIds.isEmpty()) {
            for (Account account : accountRepository.findAllById(accountIds)) {
                if (account.getAccountType() == Account.AccountType.SAVINGS
                        && account.getStatus() == Account.AccountStatus.ACTIVE) {
                    savingsBalance = savingsBalance.add(savingsService.calculateBalance(account.getAccountId()));
                }
            }
        }
        BigDecimal shareValue = shareService.getMemberSummary(memberId).getTotalValue();
        BigDecimal totalCapacity = savingsBalance.add(shareValue);
        BigDecimal maximumEligible = totalCapacity.multiply(product.getMaxMultiplier()).setScale(2, RoundingMode.HALF_UP);

        List<String> reasons = new ArrayList<>();
        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            reasons.add("Member is not active");
        }
        if (requestedAmount != null && requestedAmount.compareTo(maximumEligible) > 0) {
            reasons.add("Requested amount exceeds " + product.getMaxMultiplier() + "x savings and shares ("
                    + maximumEligible + ")");
        }
        if (requestedAmount != null && requestedAmount.compareTo(product.getMinAmount()) < 0) {
            reasons.add("Requested amount is below the product minimum of " + product.getMinAmount());
        }

        return LoanEligibilityResponse.builder()
                .memberId(member.getMemberId())
                .memberName((member.getFirstName() + " " + member.getLastName()).trim())
                .membershipNo(member.getMembershipNo())
                .savingsBalance(savingsBalance)
                .shareValue(shareValue)
                .totalCapacity(totalCapacity)
                .maxMultiplier(product.getMaxMultiplier())
                .maximumEligible(maximumEligible)
                .requestedAmount(requestedAmount)
                .eligible(reasons.isEmpty())
                .reasons(reasons)
                .build();
    }

    @Transactional
    public LoanRepaymentResponse repay(Long loanId, LoanRepaymentRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LOAN_REPAY);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != Loan.LoanStatus.DISBURSED && loan.getStatus() != Loan.LoanStatus.IN_ARREARS) {
            throw new IllegalStateException("Repayments can only be posted to a disbursed loan");
        }

        LoanProduct product = findProduct(loan.getProductId());
        LocalDate paymentDate = request.getPaymentDate() == null ? LocalDate.now() : request.getPaymentDate();
        List<LoanSchedule> schedule = loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loanId);

        chargeAccruedPenalties(schedule, product, paymentDate);

        BigDecimal maximumPayable = schedule.stream()
                .map(row -> row.getTotalDue()
                        .subtract(row.getPrincipalPaid())
                        .subtract(row.getInterestPaid())
                        .add(row.getPenaltyDue().subtract(row.getPenaltyPaid())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getAmount().compareTo(maximumPayable) > 0) {
            throw new IllegalStateException("Repayment exceeds the outstanding balance of " + maximumPayable);
        }

        BigDecimal remaining = request.getAmount();
        BigDecimal penaltyPortion = BigDecimal.ZERO;
        BigDecimal interestPortion = BigDecimal.ZERO;
        BigDecimal principalPortion = BigDecimal.ZERO;

        for (LoanSchedule row : schedule) {
            BigDecimal penaltyOutstanding = row.getPenaltyDue().subtract(row.getPenaltyPaid());
            BigDecimal applied = remaining.min(penaltyOutstanding);
            if (applied.compareTo(BigDecimal.ZERO) > 0) {
                row.setPenaltyPaid(row.getPenaltyPaid().add(applied));
                penaltyPortion = penaltyPortion.add(applied);
                remaining = remaining.subtract(applied);
            }
        }
        for (LoanSchedule row : schedule) {
            BigDecimal interestOutstanding = row.getInterestDue().subtract(row.getInterestPaid());
            BigDecimal applied = remaining.min(interestOutstanding);
            if (applied.compareTo(BigDecimal.ZERO) > 0) {
                row.setInterestPaid(row.getInterestPaid().add(applied));
                interestPortion = interestPortion.add(applied);
                remaining = remaining.subtract(applied);
            }
        }
        for (LoanSchedule row : schedule) {
            BigDecimal principalOutstanding = row.getPrincipalDue().subtract(row.getPrincipalPaid());
            BigDecimal applied = remaining.min(principalOutstanding);
            if (applied.compareTo(BigDecimal.ZERO) > 0) {
                row.setPrincipalPaid(row.getPrincipalPaid().add(applied));
                principalPortion = principalPortion.add(applied);
                remaining = remaining.subtract(applied);
            }
        }

        LocalDate today = LocalDate.now();
        for (LoanSchedule row : schedule) {
            updateScheduleStatus(row, today);
        }
        loanScheduleRepository.saveAll(schedule);

        BigDecimal outstandingPrincipal = schedule.stream()
                .map(row -> row.getPrincipalDue().subtract(row.getPrincipalPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingInterest = schedule.stream()
                .map(row -> row.getInterestDue().subtract(row.getInterestPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        loan.setOutstandingPrincipal(outstandingPrincipal);
        loan.setOutstandingInterest(outstandingInterest);
        loan.setTotalPaid(loan.getTotalPaid().add(request.getAmount()));
        boolean overdue = schedule.stream().anyMatch(row -> row.getStatus() == LoanSchedule.ScheduleStatus.OVERDUE);
        if (outstandingPrincipal.compareTo(BigDecimal.ZERO) == 0
                && outstandingInterest.compareTo(BigDecimal.ZERO) == 0) {
            loan.setStatus(Loan.LoanStatus.CLOSED);
            loan.setClosedAt(LocalDateTime.now());
            loan.setNextDueDate(null);
        } else {
            loan.setStatus(overdue ? Loan.LoanStatus.IN_ARREARS : Loan.LoanStatus.DISBURSED);
            loan.setNextDueDate(schedule.stream()
                    .filter(row -> row.getPrincipalDue().subtract(row.getPrincipalPaid())
                            .add(row.getInterestDue().subtract(row.getInterestPaid()))
                            .compareTo(BigDecimal.ZERO) > 0)
                    .map(LoanSchedule::getDueDate)
                    .findFirst()
                    .orElse(null));
        }
        loanRepository.save(loan);

        LoanRepayment repayment = LoanRepayment.builder()
                .loanId(loanId)
                .reference(referenceNumberService.next("LRP"))
                .amount(request.getAmount())
                .principalPortion(principalPortion)
                .interestPortion(interestPortion)
                .penaltyPortion(penaltyPortion)
                .paymentDate(paymentDate)
                .notes(trimToNull(request.getNotes()))
                .postedBy(actorId)
                .status("COMPLETED")
                .build();
        LoanRepayment saved = loanRepaymentRepository.save(repayment);

        auditService.logEvent(actorId, "LOAN_REPAYMENT_POSTED", "LOAN", loanId,
                "Ref: " + saved.getReference() + ", Amount: " + request.getAmount()
                        + ", Principal: " + principalPortion + ", Interest: " + interestPortion
                        + ", Penalty: " + penaltyPortion);
        return LoanRepaymentResponse.fromEntity(saved);
    }

    private List<LoanSchedule> buildSchedule(Loan loan) {
        int term = loan.getTermMonths();
        BigDecimal principal = loan.getAmount();
        BigDecimal annualRate = loan.getInterestRate();
        List<LoanSchedule> schedule = new ArrayList<>();
        LocalDate start = LocalDate.now();

        if (loan.getInterestMethod() == LoanProduct.InterestMethod.FLAT) {
            BigDecimal totalInterest = principal.multiply(annualRate)
                    .multiply(BigDecimal.valueOf(term))
                    .divide(TWELVE_HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP);
            BigDecimal monthlyInterest = totalInterest.divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP);
            BigDecimal remainingPrincipal = principal;

            for (int i = 1; i <= term; i++) {
                BigDecimal principalDue = i == term ? remainingPrincipal : monthlyPrincipal;
                BigDecimal interestDue = i == term
                        ? totalInterest.subtract(monthlyInterest.multiply(BigDecimal.valueOf(term - 1)))
                        : monthlyInterest;
                schedule.add(scheduleRow(loan.getLoanId(), i, start.plusMonths(i), principalDue, interestDue));
                remainingPrincipal = remainingPrincipal.subtract(principalDue);
            }
        } else {
            BigDecimal monthlyRate = annualRate.divide(TWELVE_HUNDRED, 10, RoundingMode.HALF_UP);
            BigDecimal installment;
            if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
                installment = principal.divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP);
            } else {
                BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(term);
                installment = principal.multiply(monthlyRate).multiply(growth)
                        .divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
            }
            BigDecimal balance = principal;
            for (int i = 1; i <= term; i++) {
                BigDecimal interestDue = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal principalDue = i == term
                        ? balance
                        : installment.subtract(interestDue).setScale(2, RoundingMode.HALF_UP);
                if (principalDue.compareTo(balance) > 0) {
                    principalDue = balance;
                }
                schedule.add(scheduleRow(loan.getLoanId(), i, start.plusMonths(i), principalDue, interestDue));
                balance = balance.subtract(principalDue);
            }
        }
        return schedule;
    }

    private LoanSchedule scheduleRow(Long loanId, int installmentNo, LocalDate dueDate,
            BigDecimal principalDue, BigDecimal interestDue) {
        BigDecimal totalDue = principalDue.add(interestDue).setScale(2, RoundingMode.HALF_UP);
        return LoanSchedule.builder()
                .loanId(loanId)
                .installmentNo(installmentNo)
                .dueDate(dueDate)
                .principalDue(principalDue.setScale(2, RoundingMode.HALF_UP))
                .interestDue(interestDue.setScale(2, RoundingMode.HALF_UP))
                .totalDue(totalDue)
                .status(LoanSchedule.ScheduleStatus.PENDING)
                .build();
    }

    private void chargeAccruedPenalties(List<LoanSchedule> schedule, LoanProduct product, LocalDate asOf) {
        if (product.getPenaltyRate() == null || product.getPenaltyRate().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        for (LoanSchedule row : schedule) {
            BigDecimal remaining = row.getTotalDue()
                    .subtract(row.getPrincipalPaid())
                    .subtract(row.getInterestPaid());
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate from = row.getPenaltyChargedUntil() != null
                    ? row.getPenaltyChargedUntil()
                    : row.getDueDate();
            if (!asOf.isAfter(from)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(from, asOf);
            BigDecimal penalty = remaining.multiply(product.getPenaltyRate())
                    .multiply(BigDecimal.valueOf(days))
                    .divide(HUNDRED.multiply(THIRTY), 2, RoundingMode.HALF_UP);
            if (penalty.compareTo(BigDecimal.ZERO) > 0) {
                row.setPenaltyDue(row.getPenaltyDue().add(penalty));
                row.setPenaltyChargedUntil(asOf);
            }
        }
    }

    private void updateScheduleStatus(LoanSchedule row, LocalDate today) {
        boolean principalPaid = row.getPrincipalPaid().compareTo(row.getPrincipalDue()) >= 0;
        boolean interestPaid = row.getInterestPaid().compareTo(row.getInterestDue()) >= 0;
        if (principalPaid && interestPaid) {
            row.setStatus(LoanSchedule.ScheduleStatus.PAID);
        } else if (row.getDueDate().isBefore(today)) {
            row.setStatus(LoanSchedule.ScheduleStatus.OVERDUE);
        } else if (row.getPrincipalPaid().compareTo(BigDecimal.ZERO) > 0
                || row.getInterestPaid().compareTo(BigDecimal.ZERO) > 0) {
            row.setStatus(LoanSchedule.ScheduleStatus.PARTIALLY_PAID);
        } else {
            row.setStatus(LoanSchedule.ScheduleStatus.PENDING);
        }
    }

    private List<LoanScheduleResponse> buildScheduleResponses(List<LoanSchedule> schedule,
            LoanProduct product, LocalDate today) {
        List<LoanScheduleResponse> responses = new ArrayList<>();
        for (LoanSchedule row : schedule) {
            BigDecimal totalPaid = row.getPrincipalPaid().add(row.getInterestPaid());
            BigDecimal balance = row.getTotalDue().subtract(totalPaid);
            long daysOverdue = balance.compareTo(BigDecimal.ZERO) > 0 && row.getDueDate().isBefore(today)
                    ? ChronoUnit.DAYS.between(row.getDueDate(), today) : 0;
            BigDecimal accruedPenalty = BigDecimal.ZERO;
            if (daysOverdue > 0 && product.getPenaltyRate() != null
                    && product.getPenaltyRate().compareTo(BigDecimal.ZERO) > 0) {
                LocalDate from = row.getPenaltyChargedUntil() != null
                        ? row.getPenaltyChargedUntil() : row.getDueDate();
                long days = today.isAfter(from) ? ChronoUnit.DAYS.between(from, today) : 0;
                accruedPenalty = balance.multiply(product.getPenaltyRate())
                        .multiply(BigDecimal.valueOf(days))
                        .divide(HUNDRED.multiply(THIRTY), 2, RoundingMode.HALF_UP);
            }
            String status;
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                status = LoanSchedule.ScheduleStatus.PAID.name();
            } else if (daysOverdue > 0) {
                status = LoanSchedule.ScheduleStatus.OVERDUE.name();
            } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                status = LoanSchedule.ScheduleStatus.PARTIALLY_PAID.name();
            } else {
                status = LoanSchedule.ScheduleStatus.PENDING.name();
            }
            responses.add(LoanScheduleResponse.builder()
                    .scheduleId(row.getScheduleId())
                    .installmentNo(row.getInstallmentNo())
                    .dueDate(row.getDueDate())
                    .principalDue(row.getPrincipalDue())
                    .interestDue(row.getInterestDue())
                    .totalDue(row.getTotalDue())
                    .principalPaid(row.getPrincipalPaid())
                    .interestPaid(row.getInterestPaid())
                    .penaltyDue(row.getPenaltyDue())
                    .penaltyPaid(row.getPenaltyPaid())
                    .totalPaid(totalPaid)
                    .balance(balance)
                    .daysOverdue(daysOverdue)
                    .accruedPenalty(accruedPenalty)
                    .status(status)
                    .build());
        }
        return responses;
    }

    private LoanResponse toResponse(Loan loan) {
        Member member = memberRepository.findById(loan.getMemberId()).orElse(null);
        LoanProduct product = loanProductRepository.findById(loan.getProductId()).orElse(null);
        List<LoanSchedule> schedule = loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loan.getLoanId());
        List<LoanScheduleResponse> scheduleResponses = product == null
                ? List.of() : buildScheduleResponses(schedule, product, LocalDate.now());

        BigDecimal totalInterest = schedule.stream()
                .map(LoanSchedule::getInterestDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amountInArrears = scheduleResponses.stream()
                .filter(row -> row.getDaysOverdue() > 0)
                .map(LoanScheduleResponse::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal penaltyDue = scheduleResponses.stream()
                .map(row -> row.getPenaltyDue().subtract(row.getPenaltyPaid()).add(row.getAccruedPenalty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Loan.LoanStatus effectiveStatus = loan.getStatus() == Loan.LoanStatus.DISBURSED
                && amountInArrears.compareTo(BigDecimal.ZERO) > 0
                ? Loan.LoanStatus.IN_ARREARS : loan.getStatus();

        List<LoanGuarantorResponse> guarantors = loanGuarantorRepository.findByLoanId(loan.getLoanId()).stream()
                .map(guarantor -> {
                    Member guarantorMember = memberRepository.findById(guarantor.getMemberId()).orElse(null);
                    return LoanGuarantorResponse.builder()
                            .guarantorId(guarantor.getGuarantorId())
                            .memberId(guarantor.getMemberId())
                            .memberName(guarantorMember == null ? null
                                    : (guarantorMember.getFirstName() + " " + guarantorMember.getLastName()).trim())
                            .guaranteedAmount(guarantor.getGuaranteedAmount())
                            .status(guarantor.getStatus())
                            .build();
                })
                .toList();

        return LoanResponse.builder()
                .loanId(loan.getLoanId())
                .loanNumber(loan.getLoanNumber())
                .memberId(loan.getMemberId())
                .memberName(member == null ? null : (member.getFirstName() + " " + member.getLastName()).trim())
                .membershipNo(member == null ? null : member.getMembershipNo())
                .productId(loan.getProductId())
                .productName(product == null ? null : product.getName())
                .amount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .interestMethod(loan.getInterestMethod())
                .termMonths(loan.getTermMonths())
                .purpose(loan.getPurpose())
                .status(effectiveStatus)
                .applicationDate(loan.getApplicationDate())
                .reviewedBy(loan.getReviewedBy())
                .reviewedAt(loan.getReviewedAt())
                .eligibilityNotes(loan.getEligibilityNotes())
                .approvedBy(loan.getApprovedBy())
                .approvedAt(loan.getApprovedAt())
                .rejectedBy(loan.getRejectedBy())
                .rejectedAt(loan.getRejectedAt())
                .rejectionReason(loan.getRejectionReason())
                .disbursedBy(loan.getDisbursedBy())
                .disbursedAt(loan.getDisbursedAt())
                .disbursementAccountId(loan.getDisbursementAccountId())
                .outstandingPrincipal(loan.getOutstandingPrincipal())
                .outstandingInterest(loan.getOutstandingInterest())
                .totalPaid(loan.getTotalPaid())
                .totalInterest(totalInterest)
                .totalRepayable(loan.getAmount().add(totalInterest))
                .amountInArrears(amountInArrears)
                .penaltyDue(penaltyDue)
                .nextDueDate(loan.getNextDueDate())
                .closedAt(loan.getClosedAt())
                .createdAt(loan.getCreatedAt())
                .guarantors(guarantors)
                .build();
    }

    private void validateGuarantors(LoanApplicationRequest request, LoanProduct product) {
        List<GuarantorRequest> guarantors = request.getGuarantors() == null
                ? List.of() : request.getGuarantors();
        if (Boolean.TRUE.equals(product.getRequiresGuarantor()) && guarantors.isEmpty()) {
            throw new IllegalStateException("This loan product requires at least one guarantor");
        }
        BigDecimal guaranteedTotal = BigDecimal.ZERO;
        for (GuarantorRequest guarantor : guarantors) {
            if (guarantor.getMemberId().equals(request.getMemberId())) {
                throw new IllegalStateException("A member cannot guarantee their own loan");
            }
            if (!memberRepository.existsById(guarantor.getMemberId())) {
                throw new IllegalArgumentException("Guarantor member not found: " + guarantor.getMemberId());
            }
            guaranteedTotal = guaranteedTotal.add(guarantor.getGuaranteedAmount());
        }
        if (Boolean.TRUE.equals(product.getRequiresGuarantor())
                && guaranteedTotal.compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Total guaranteed amount must cover the loan amount ("
                    + request.getAmount() + ")");
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
    }

    private LoanProduct findProduct(Long productId) {
        return loanProductRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Loan product not found: " + productId));
    }

    private Loan findLoan(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
