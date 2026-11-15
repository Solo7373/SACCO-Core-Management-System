package com.sacco.loan;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.auth.SecurityUtils;
import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import com.sacco.common.NumberSequenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.loan.dto.*;
import com.sacco.loan.entity.*;
import com.sacco.loan.repository.*;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Collections workspace: aging, formal demand notices and the contact trail.
 *
 * <p>All portfolio-wide reads are gated by COLLECTION_VIEW so the member
 * portal can never see another member's arrears. Loan-specific demand reads
 * use LOAN_VIEW plus {@link PermissionService#requireSelfOrPrivileged}.</p>
 */
@Service
@RequiredArgsConstructor
public class LoanCollectionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final List<Loan.LoanStatus> ACTIVE_STATUSES =
            List.of(Loan.LoanStatus.DISBURSED, Loan.LoanStatus.IN_ARREARS);

    private final LoanRepository loanRepository;
    private final LoanScheduleRepository loanScheduleRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanGuarantorRepository loanGuarantorRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final LoanDemandRepository loanDemandRepository;
    private final LoanCollectionActionRepository collectionActionRepository;
    private final NumberSequenceService numberSequenceService;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final LoanService loanService;
    private final DemandLetterService demandLetterService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CollectionSummary getSummary(Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.COLLECTION_VIEW);

        LocalDate today = LocalDate.now();
        List<CollectionLoanRow> rows = buildRows(today);
        BigDecimal totalOutstanding = rows.stream()
                .map(CollectionLoanRow::getTotalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalArrears = rows.stream()
                .map(CollectionLoanRow::getAmountInArrears)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPenalty = rows.stream()
                .map(CollectionLoanRow::getPenaltyOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long promisesDue = rows.stream()
                .filter(row -> "DUE".equals(row.getPromiseStatus())
                        || "OVERDUE".equals(row.getPromiseStatus()))
                .count();
        long promisesOverdue = rows.stream()
                .filter(row -> "OVERDUE".equals(row.getPromiseStatus()))
                .count();
        long openDemands = loanDemandRepository.countByStatusIn(List.of(
                LoanDemand.DemandStatus.ISSUED,
                LoanDemand.DemandStatus.PARTIALLY_SETTLED,
                LoanDemand.DemandStatus.ESCALATED));
        LocalDate monthStart = today.withDayOfMonth(1);
        long demandsThisMonth = loanDemandRepository.countByIssuedAtBetween(
                monthStart.atStartOfDay(), LocalDateTime.now());
        BigDecimal recoveryThisMonth = loanRepaymentRepository
                .findByPaymentDateBetween(monthStart, today).stream()
                .map(LoanRepayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal parPercent = totalOutstanding.signum() == 0
                ? BigDecimal.ZERO
                : totalArrears.multiply(HUNDRED)
                        .divide(totalOutstanding, 2, RoundingMode.HALF_UP);

        return CollectionSummary.builder()
                .asOf(today)
                .activeLoans(rows.size())
                .delinquentLoans((int) rows.stream()
                        .filter(row -> row.getAmountInArrears().signum() > 0).count())
                .totalOutstanding(totalOutstanding)
                .totalArrears(totalArrears)
                .totalPenalty(totalPenalty)
                .parPercent(parPercent)
                .promisesDue(promisesDue)
                .promisesOverdue(promisesOverdue)
                .openDemands(openDemands)
                .demandsThisMonth(demandsThisMonth)
                .recoveryThisMonth(recoveryThisMonth)
                .buckets(buildBuckets(rows))
                .build();
    }

    @Transactional(readOnly = true)
    public List<CollectionLoanRow> getCollections(Long actorId, String bucket, String search) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.COLLECTION_VIEW);

        List<CollectionLoanRow> rows = buildRows(LocalDate.now());
        if (bucket != null && !bucket.isBlank() && !"ALL".equalsIgnoreCase(bucket)) {
            rows = rows.stream()
                    .filter(row -> bucket.equalsIgnoreCase(row.getAgingBucket()))
                    .toList();
        }
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase();
            rows = rows.stream()
                    .filter(row -> contains(row.getLoanNumber(), query)
                            || contains(row.getMemberName(), query)
                            || contains(row.getMembershipNo(), query)
                            || contains(row.getPhone(), query))
                    .toList();
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public LoanCollectionDetailResponse getDetail(Long loanId, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.COLLECTION_VIEW);
        Loan loan = findLoan(loanId);
        return LoanCollectionDetailResponse.builder()
                .loan(buildRow(loan, LocalDate.now()))
                .schedule(loanService.getScheduleSnapshot(loanId))
                .demands(getDemandResponses(loan))
                .actions(getActionResponses(loanId))
                .repayments(loanRepaymentRepository.findByLoanIdOrderByCreatedAtDesc(loanId)
                        .stream().map(LoanRepaymentResponse::fromEntity).toList())
                .guarantors(getGuarantorResponses(loanId))
                .build();
    }

    @Transactional(readOnly = true)
    public List<LoanDemandResponse> getAllDemands(Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.COLLECTION_VIEW);
        return loanDemandRepository.findAllByOrderByIssuedAtDesc().stream()
                .map(demand -> toDemandResponse(demand,
                        findLoan(demand.getLoanId()),
                        findMember(findLoan(demand.getLoanId()).getMemberId())))
                .toList();
    }

    /**
     * Member-safe demand list. The caller must be able to view the loan, and a
     * MEMBER actor may only read demands for their own member id.
     */
    @Transactional(readOnly = true)
    public List<LoanDemandResponse> getDemandsForLoan(Long loanId, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.LOAN_VIEW);
        Loan loan = findLoan(loanId);
        permissionService.requireSelfOrPrivileged(actorId, loan.getMemberId());
        return getDemandResponses(loan);
    }

    @Transactional
    public LoanDemandResponse issueDemand(Long loanId, LoanDemandRequest request, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.LOAN_DEMAND);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != Loan.LoanStatus.DISBURSED
                && loan.getStatus() != Loan.LoanStatus.IN_ARREARS) {
            throw new IllegalStateException("Demand notices can only be issued for an active loan");
        }

        LocalDate today = LocalDate.now();
        CollectionLoanRow snapshot = buildRow(loan, today);
        if (snapshot.getAmountInArrears().signum() <= 0) {
            throw new IllegalStateException("This loan has no overdue installments to demand");
        }

        LocalDate dueDate = request.getDueDate() == null
                ? today.plusDays(14) : request.getDueDate();
        if (!dueDate.isAfter(today)) {
            throw new IllegalArgumentException("The demand due date must be in the future");
        }
        LoanDemand.DemandStage stage = request.getStage() != null
                ? request.getStage()
                : Optional.ofNullable(snapshot.getSuggestedStage())
                        .map(LoanDemand.DemandStage::valueOf)
                        .orElse(LoanDemand.DemandStage.FIRST_DEMAND);
        LoanDemand.DeliveryMethod deliveryMethod = request.getDeliveryMethod() == null
                ? LoanDemand.DeliveryMethod.PRINT : request.getDeliveryMethod();
        BigDecimal amountDemanded = snapshot.getAmountInArrears()
                .add(snapshot.getPenaltyOutstanding());
        String demandNumber = numberSequenceService.formatted("loan_demand", "LDN");
        String seal = seal(demandNumber, loan.getLoanNumber(), amountDemanded,
                snapshot.getDaysOverdue(), today, dueDate);
        String scheduleSnapshot = snapshotSchedule(loanId);

        LoanDemand demand = LoanDemand.builder()
                .demandNumber(demandNumber)
                .loanId(loanId)
                .stage(stage)
                .amountDemanded(amountDemanded)
                .principalArrears(snapshot.getPrincipalArrears())
                .interestArrears(snapshot.getInterestArrears())
                .penaltyArrears(snapshot.getPenaltyOutstanding())
                .daysOverdue(snapshot.getDaysOverdue())
                .issuedBy(actorId)
                .issuedAt(LocalDateTime.now())
                .dueDate(dueDate)
                .deliveryMethod(deliveryMethod)
                .status(LoanDemand.DemandStatus.ISSUED)
                .notes(trimToNull(request.getNotes()))
                .documentSeal(seal)
                .scheduleSnapshot(scheduleSnapshot)
                .build();
        LoanDemand saved = loanDemandRepository.save(demand);

        collectionActionRepository.save(LoanCollectionAction.builder()
                .loanId(loanId)
                .demandId(saved.getDemandId())
                .actionType(LoanCollectionAction.ActionType.DEMAND_ISSUED)
                .outcome(LoanCollectionAction.Outcome.NOTE)
                .notes("Demand " + demandNumber + " issued (" + stage.name() + ")")
                .actorId(actorId)
                .build());

        auditService.logEvent(actorId, "LOAN_DEMAND_ISSUED", "LOAN_DEMAND", saved.getDemandId(),
                "Loan: " + loan.getLoanNumber() + ", Stage: " + stage
                        + ", Amount: " + amountDemanded + ", Due: " + dueDate);
        return toDemandResponse(saved, loan, findMember(loan.getMemberId()));
    }

    @Transactional
    public LoanDemandResponse updateDemandStatus(Long demandId, DemandStatusRequest request, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.LOAN_DEMAND);
        LoanDemand demand = loanDemandRepository.findById(demandId)
                .orElseThrow(() -> new IllegalArgumentException("Demand notice not found: " + demandId));
        demand.setStatus(request.getStatus());
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            demand.setNotes(trimToNull(request.getNotes()));
        }
        LoanDemand saved = loanDemandRepository.save(demand);
        auditService.logEvent(actorId, "LOAN_DEMAND_STATUS_CHANGED", "LOAN_DEMAND", demandId,
                "Status: " + request.getStatus() + ", Notes: " + request.getNotes());
        Loan loan = findLoan(saved.getLoanId());
        return toDemandResponse(saved, loan, findMember(loan.getMemberId()));
    }

    @Transactional
    public CollectionActionResponse recordAction(Long loanId, CollectionActionRequest request, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.LOAN_COLLECT);
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != Loan.LoanStatus.DISBURSED
                && loan.getStatus() != Loan.LoanStatus.IN_ARREARS) {
            throw new IllegalStateException("Collection actions can only be recorded for an active loan");
        }
        if (request.getActionType() == LoanCollectionAction.ActionType.PROMISE_TO_PAY) {
            if (request.getPromisedAmount() == null
                    || request.getPromisedAmount().signum() <= 0) {
                throw new IllegalArgumentException("A promise-to-pay requires a positive promised amount");
            }
            if (request.getPromisedDate() == null) {
                throw new IllegalArgumentException("A promise-to-pay requires a promised date");
            }
        }
        if (request.getDemandId() != null) {
            LoanDemand demand = loanDemandRepository.findById(request.getDemandId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Demand notice not found: " + request.getDemandId()));
            if (!demand.getLoanId().equals(loanId)) {
                throw new IllegalArgumentException("The demand notice does not belong to this loan");
            }
        }

        LoanCollectionAction action = LoanCollectionAction.builder()
                .loanId(loanId)
                .demandId(request.getDemandId())
                .actionType(request.getActionType())
                .outcome(request.getOutcome())
                .promisedAmount(request.getPromisedAmount())
                .promisedDate(request.getPromisedDate())
                .notes(trimToNull(request.getNotes()))
                .actorId(actorId)
                .build();
        LoanCollectionAction saved = collectionActionRepository.save(action);
        auditService.logEvent(actorId, "LOAN_COLLECTION_ACTION_LOGGED", "LOAN", loanId,
                "Action: " + request.getActionType() + ", Outcome: " + request.getOutcome()
                        + ", Promise: " + request.getPromisedAmount()
                        + " by " + request.getPromisedDate());
        return toActionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CollectionActionResponse> getActions(Long loanId, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.COLLECTION_VIEW);
        findLoan(loanId);
        return getActionResponses(loanId);
    }

    @Transactional(readOnly = true)
    public DemandPdf getDemandPdfForLoan(Long loanId, Long demandId, Long actorId) {
        actorId = currentActor(actorId);
        permissionService.require(actorId, Permission.LOAN_VIEW);
        Loan loan = findLoan(loanId);
        permissionService.requireSelfOrPrivileged(actorId, loan.getMemberId());
        LoanDemand demand = loanDemandRepository.findById(demandId)
                .orElseThrow(() -> new IllegalArgumentException("Demand notice not found: " + demandId));
        if (!demand.getLoanId().equals(loanId)) {
            throw new IllegalArgumentException("The demand notice does not belong to this loan");
        }
        Member member = findMember(loan.getMemberId());
        LoanProduct product = findProduct(loan.getProductId());
        String issuedByName = demand.getIssuedBy() == null ? "System"
                : userRepository.findById(demand.getIssuedBy())
                        .map(User::getUsername).orElse("System");
        List<LoanScheduleResponse> schedule = demand.getScheduleSnapshot() == null
                ? loanService.getScheduleSnapshot(loanId)
                : readScheduleSnapshot(demand.getScheduleSnapshot());
        byte[] content = demandLetterService.generate(
                demand,
                loan,
                member,
                product,
                schedule,
                getGuarantorResponses(loanId),
                issuedByName);
        return new DemandPdf(demand.getDemandNumber() + ".pdf", content);
    }

    // ------------------------------------------------------------------
    // Calculation helpers
    // ------------------------------------------------------------------

    private String snapshotSchedule(Long loanId) {
        List<LoanScheduleResponse> overdue = loanService.getScheduleSnapshot(loanId).stream()
                .filter(row -> row.getDaysOverdue() > 0
                        && row.getBalance() != null
                        && row.getBalance().signum() > 0)
                .toList();
        if (overdue.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(overdue);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not snapshot the demand schedule", exception);
        }
    }

    private List<LoanScheduleResponse> readScheduleSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<LoanScheduleResponse>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read the demand schedule snapshot", exception);
        }
    }

    private List<CollectionLoanRow> buildRows(LocalDate today) {
        return loanRepository.findByStatusIn(ACTIVE_STATUSES).stream()
                .map(loan -> buildRow(loan, today))
                .sorted(Comparator
                        .comparingInt(CollectionLoanRow::getDaysOverdue).reversed()
                        .thenComparing(CollectionLoanRow::getAmountInArrears,
                                Comparator.reverseOrder()))
                .toList();
    }

    private CollectionLoanRow buildRow(Loan loan, LocalDate today) {
        Member member = memberRepository.findById(loan.getMemberId()).orElse(null);
        LoanProduct product = loanProductRepository.findById(loan.getProductId()).orElse(null);
        List<LoanSchedule> schedule =
                loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loan.getLoanId());

        BigDecimal outstandingPrincipal = BigDecimal.ZERO;
        BigDecimal outstandingInterest = BigDecimal.ZERO;
        BigDecimal principalArrears = BigDecimal.ZERO;
        BigDecimal interestArrears = BigDecimal.ZERO;
        BigDecimal penaltyOutstanding = BigDecimal.ZERO;
        int daysOverdue = 0;

        for (LoanSchedule row : schedule) {
            BigDecimal principalOutstanding = nonNegative(
                    row.getPrincipalDue().subtract(row.getPrincipalPaid()));
            BigDecimal interestOutstanding = nonNegative(
                    row.getInterestDue().subtract(row.getInterestPaid()));
            outstandingPrincipal = outstandingPrincipal.add(principalOutstanding);
            outstandingInterest = outstandingInterest.add(interestOutstanding);

            BigDecimal balance = principalOutstanding.add(interestOutstanding);
            if (balance.signum() > 0 && row.getDueDate().isBefore(today)) {
                principalArrears = principalArrears.add(principalOutstanding);
                interestArrears = interestArrears.add(interestOutstanding);
                daysOverdue = Math.max(daysOverdue,
                        (int) ChronoUnit.DAYS.between(row.getDueDate(), today));
            }

            BigDecimal penalty = nonNegative(row.getPenaltyDue().subtract(row.getPenaltyPaid()));
            penaltyOutstanding = penaltyOutstanding
                    .add(penalty)
                    .add(accruedPenalty(row, product, today));
        }

        BigDecimal amountInArrears = principalArrears.add(interestArrears);
        Optional<LoanCollectionAction> lastAction =
                collectionActionRepository.findFirstByLoanIdOrderByCreatedAtDesc(loan.getLoanId());
        Optional<LoanCollectionAction> promise =
                collectionActionRepository.findFirstByLoanIdAndActionTypeOrderByCreatedAtDesc(
                        loan.getLoanId(), LoanCollectionAction.ActionType.PROMISE_TO_PAY);
        Optional<LoanDemand> lastDemand =
                loanDemandRepository.findFirstByLoanIdOrderByIssuedAtDesc(loan.getLoanId());
        String suggestedStage = suggestedStageFor(daysOverdue) == null
                ? null : suggestedStageFor(daysOverdue).name();
        String promiseStatus = promise
                .map(action -> promiseStatusFor(action.getPromisedDate(), amountInArrears,
                        today, loan.getStatus() == Loan.LoanStatus.CLOSED))
                .orElse(null);

        return CollectionLoanRow.builder()
                .loanId(loan.getLoanId())
                .loanNumber(loan.getLoanNumber())
                .memberId(loan.getMemberId())
                .memberName(member == null ? null
                        : (member.getFirstName() + " " + member.getLastName()).trim())
                .membershipNo(member == null ? null : member.getMembershipNo())
                .phone(member == null ? null : member.getPhone())
                .email(member == null ? null : member.getEmail())
                .productName(product == null ? null : product.getName())
                .loanStatus(amountInArrears.signum() > 0
                        ? Loan.LoanStatus.IN_ARREARS.name() : loan.getStatus().name())
                .outstandingPrincipal(outstandingPrincipal)
                .outstandingInterest(outstandingInterest)
                .totalOutstanding(outstandingPrincipal.add(outstandingInterest))
                .principalArrears(principalArrears)
                .interestArrears(interestArrears)
                .amountInArrears(amountInArrears)
                .penaltyOutstanding(penaltyOutstanding)
                .amountDemanded(amountInArrears.add(penaltyOutstanding))
                .daysOverdue(daysOverdue)
                .agingBucket(bucketFor(daysOverdue, amountInArrears))
                .suggestedStage(suggestedStage)
                .lastActionType(lastAction.map(action -> action.getActionType().name()).orElse(null))
                .lastActionAt(lastAction.map(LoanCollectionAction::getCreatedAt).orElse(null))
                .lastDemandStage(lastDemand.map(demand -> demand.getStage().name()).orElse(null))
                .lastDemandAt(lastDemand.map(LoanDemand::getIssuedAt).orElse(null))
                .demandsIssued(loanDemandRepository.countByLoanId(loan.getLoanId()))
                .promisedAmount(promise.map(LoanCollectionAction::getPromisedAmount).orElse(null))
                .promisedDate(promise.map(LoanCollectionAction::getPromisedDate).orElse(null))
                .promiseStatus(promiseStatus)
                .nextDueDate(loan.getNextDueDate())
                .disbursedAt(loan.getDisbursedAt())
                .build();
    }

    private BigDecimal accruedPenalty(LoanSchedule row, LoanProduct product, LocalDate today) {
        if (product == null || product.getPenaltyRate() == null
                || product.getPenaltyRate().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal balance = nonNegative(row.getPrincipalDue().subtract(row.getPrincipalPaid()))
                .add(nonNegative(row.getInterestDue().subtract(row.getInterestPaid())));
        if (balance.signum() <= 0 || !row.getDueDate().isBefore(today)) {
            return BigDecimal.ZERO;
        }
        LocalDate from = row.getPenaltyChargedUntil() != null
                ? row.getPenaltyChargedUntil() : row.getDueDate();
        long days = today.isAfter(from) ? ChronoUnit.DAYS.between(from, today) : 0;
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        return balance.multiply(product.getPenaltyRate())
                .multiply(BigDecimal.valueOf(days))
                .divide(HUNDRED.multiply(THIRTY), 2, RoundingMode.HALF_UP);
    }

    private List<AgingBucketResponse> buildBuckets(List<CollectionLoanRow> rows) {
        String[][] definitions = {
                {"CURRENT", "Current / not due"},
                {"1-30", "1-30 days"},
                {"31-60", "31-60 days"},
                {"61-90", "61-90 days"},
                {"90+", "90+ days"}
        };
        return java.util.Arrays.stream(definitions)
                .map(definition -> {
                    String bucket = definition[0];
                    List<CollectionLoanRow> matching = rows.stream()
                            .filter(row -> bucket.equals(row.getAgingBucket()))
                            .toList();
                    return AgingBucketResponse.builder()
                            .bucket(bucket)
                            .label(definition[1])
                            .loanCount(matching.size())
                            .amount(matching.stream()
                                    .map(CollectionLoanRow::getAmountInArrears)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                            .build();
                })
                .toList();
    }

    private LoanDemand.DemandStage suggestedStageFor(int daysOverdue) {
        if (daysOverdue <= 0) {
            return null;
        }
        if (daysOverdue <= 14) {
            return LoanDemand.DemandStage.FIRST_DEMAND;
        }
        if (daysOverdue <= 30) {
            return LoanDemand.DemandStage.SECOND_DEMAND;
        }
        if (daysOverdue <= 60) {
            return LoanDemand.DemandStage.FINAL_DEMAND;
        }
        if (daysOverdue <= 90) {
            return LoanDemand.DemandStage.GUARANTOR_DEMAND;
        }
        return LoanDemand.DemandStage.LEGAL_NOTICE;
    }

    private String bucketFor(int daysOverdue, BigDecimal amountInArrears) {
        if (amountInArrears.signum() <= 0 || daysOverdue <= 0) {
            return "CURRENT";
        }
        if (daysOverdue <= 30) {
            return "1-30";
        }
        if (daysOverdue <= 60) {
            return "31-60";
        }
        if (daysOverdue <= 90) {
            return "61-90";
        }
        return "90+";
    }

    private String promiseStatusFor(LocalDate promisedDate, BigDecimal amountInArrears,
            LocalDate today, boolean closed) {
        if (promisedDate == null) {
            return null;
        }
        if (closed || amountInArrears.signum() <= 0) {
            return "KEPT";
        }
        if (promisedDate.isBefore(today)) {
            return "OVERDUE";
        }
        if (promisedDate.isEqual(today)) {
            return "DUE";
        }
        return "UPCOMING";
    }

    // ------------------------------------------------------------------
    // Mapping helpers
    // ------------------------------------------------------------------

    private List<LoanDemandResponse> getDemandResponses(Loan loan) {
        Member member = findMember(loan.getMemberId());
        return loanDemandRepository.findByLoanIdOrderByIssuedAtDesc(loan.getLoanId()).stream()
                .map(demand -> toDemandResponse(demand, loan, member))
                .toList();
    }

    private LoanDemandResponse toDemandResponse(LoanDemand demand, Loan loan, Member member) {
        String issuedByName = demand.getIssuedBy() == null ? null
                : userRepository.findById(demand.getIssuedBy())
                        .map(User::getUsername).orElse(null);
        return LoanDemandResponse.builder()
                .demandId(demand.getDemandId())
                .demandNumber(demand.getDemandNumber())
                .loanId(demand.getLoanId())
                .loanNumber(loan == null ? null : loan.getLoanNumber())
                .memberId(loan == null ? null : loan.getMemberId())
                .memberName(member == null ? null
                        : (member.getFirstName() + " " + member.getLastName()).trim())
                .stage(demand.getStage().name())
                .amountDemanded(demand.getAmountDemanded())
                .principalArrears(demand.getPrincipalArrears())
                .interestArrears(demand.getInterestArrears())
                .penaltyArrears(demand.getPenaltyArrears())
                .daysOverdue(demand.getDaysOverdue())
                .issuedBy(demand.getIssuedBy())
                .issuedByName(issuedByName)
                .issuedAt(demand.getIssuedAt())
                .dueDate(demand.getDueDate())
                .deliveryMethod(demand.getDeliveryMethod().name())
                .status(demand.getStatus().name())
                .notes(demand.getNotes())
                .documentSeal(demand.getDocumentSeal())
                .build();
    }

    private List<CollectionActionResponse> getActionResponses(Long loanId) {
        return collectionActionRepository.findByLoanIdOrderByCreatedAtDesc(loanId).stream()
                .map(this::toActionResponse)
                .toList();
    }

    private CollectionActionResponse toActionResponse(LoanCollectionAction action) {
        String actorName = action.getActorId() == null ? null
                : userRepository.findById(action.getActorId())
                        .map(User::getUsername).orElse(null);
        return CollectionActionResponse.builder()
                .actionId(action.getActionId())
                .loanId(action.getLoanId())
                .demandId(action.getDemandId())
                .actionType(action.getActionType().name())
                .outcome(action.getOutcome().name())
                .promisedAmount(action.getPromisedAmount())
                .promisedDate(action.getPromisedDate())
                .notes(action.getNotes())
                .actorId(action.getActorId())
                .actorName(actorName)
                .createdAt(action.getCreatedAt())
                .build();
    }

    private List<LoanGuarantorResponse> getGuarantorResponses(Long loanId) {
        return loanGuarantorRepository.findByLoanId(loanId).stream()
                .map(guarantor -> {
                    Member member = memberRepository.findById(guarantor.getMemberId()).orElse(null);
                    return LoanGuarantorResponse.builder()
                            .guarantorId(guarantor.getGuarantorId())
                            .memberId(guarantor.getMemberId())
                            .memberName(member == null ? null
                                    : (member.getFirstName() + " " + member.getLastName()).trim())
                            .guaranteedAmount(guarantor.getGuaranteedAmount())
                            .status(guarantor.getStatus())
                            .build();
                })
                .toList();
    }

    private String seal(String demandNumber, String loanNumber, BigDecimal amount,
            int daysOverdue, LocalDate issuedDate, LocalDate dueDate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = demandNumber + "|" + loanNumber + "|" + amount.toPlainString()
                    + "|" + daysOverdue + "|" + issuedDate + "|" + dueDate;
            return HexFormat.of().formatHex(
                    digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not seal the demand notice", exception);
        }
    }

    private Loan findLoan(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
    }

    private LoanProduct findProduct(Long productId) {
        return loanProductRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Loan product not found: " + productId));
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private Long currentActor(Long fallback) {
        Long authenticated = SecurityUtils.currentUserId();
        return authenticated != null ? authenticated : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
