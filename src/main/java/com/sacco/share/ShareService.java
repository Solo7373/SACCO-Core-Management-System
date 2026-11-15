package com.sacco.share;

import com.sacco.audit.AuditService;
import com.sacco.common.ReferenceNumberService;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.share.dto.*;
import com.sacco.share.entity.ShareProduct;
import com.sacco.share.entity.ShareTransaction;
import com.sacco.share.repository.ShareProductRepository;
import com.sacco.share.repository.ShareTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareProductRepository shareProductRepository;
    private final ShareTransactionRepository shareTransactionRepository;
    private final MemberRepository memberRepository;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;

    @Transactional
    public ShareProductResponse createShareProduct(ShareProductRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        if (request.getPricePerShare() == null || request.getPricePerShare().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Price per share must be greater than zero");
        }

        ShareProduct product = ShareProduct.builder()
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .minShares(request.getMinShares() == null ? 1 : request.getMinShares())
                .pricePerShare(request.getPricePerShare())
                .status("ACTIVE")
                .build();
        ShareProduct saved = shareProductRepository.save(product);
        auditService.logEvent(actorId, "SHARE_PRODUCT_CREATED", "SHARE_PRODUCT", saved.getProductId(),
                "Name: " + request.getName());
        return ShareProductResponse.fromEntity(saved);
    }

    @Transactional
    public ShareTransactionResponse recordShareTransaction(ShareTransactionRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + request.getMemberId()));
        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new IllegalStateException("Only active members can transact shares");
        }

        ShareProduct product = shareProductRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Share product not found: " + request.getProductId()));

        if (!"ACTIVE".equals(product.getStatus())) {
            throw new IllegalStateException("Share product is not active");
        }
        if (request.getQuantity() < product.getMinShares()) {
            throw new IllegalStateException("Minimum " + product.getMinShares() + " shares required");
        }

        BigDecimal expectedAmount = product.getPricePerShare()
                .multiply(BigDecimal.valueOf(request.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        if (request.getAmount() == null || request.getAmount().compareTo(expectedAmount) != 0) {
            throw new IllegalStateException("Share amount must equal quantity x price per share ("
                    + expectedAmount + ")");
        }

        if (isDebit(request.getTransactionType())) {
            int available = availableQuantity(request.getMemberId(), request.getProductId());
            if (available < request.getQuantity()) {
                throw new IllegalStateException("Insufficient shares. Available: " + available
                        + ", Requested: " + request.getQuantity());
            }
        }

        ShareTransaction transaction = ShareTransaction.builder()
                .memberId(request.getMemberId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .unitPrice(product.getPricePerShare())
                .amount(expectedAmount)
                .transactionType(request.getTransactionType())
                .status(ShareTransaction.ShareTransactionStatus.PENDING)
                .reference(trimToNull(request.getReference()) == null
                        ? referenceNumberService.next("SHR")
                        : request.getReference().trim())
                .build();

        ShareTransaction saved = shareTransactionRepository.save(transaction);
        auditService.logEvent(actorId, "SHARE_TRANSACTION_RECORDED", "SHARE_TRANSACTION",
                saved.getTransactionId(), "Member: " + request.getMemberId()
                        + ", Product: " + request.getProductId()
                        + ", Qty: " + request.getQuantity());
        return ShareTransactionResponse.fromEntity(saved);
    }

    @Transactional
    public ShareTransactionResponse approveShareTransaction(Long transactionId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        ShareTransaction transaction = findTransaction(transactionId);

        if (transaction.getStatus() != ShareTransaction.ShareTransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be approved");
        }

        if (isDebit(transaction.getTransactionType())) {
            int available = availableQuantity(transaction.getMemberId(), transaction.getProductId());
            if (available < transaction.getQuantity()) {
                throw new IllegalStateException("Insufficient shares to complete this transaction");
            }
        }

        transaction.setStatus(ShareTransaction.ShareTransactionStatus.COMPLETED);
        transaction.setProcessedBy(actorId);
        transaction.setProcessedAt(LocalDateTime.now());
        ShareTransaction saved = shareTransactionRepository.save(transaction);
        auditService.logEvent(actorId, "SHARE_TRANSACTION_APPROVED", "SHARE_TRANSACTION", transactionId, null);
        return ShareTransactionResponse.fromEntity(saved);
    }

    @Transactional
    public ShareTransactionResponse rejectShareTransaction(Long transactionId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        ShareTransaction transaction = findTransaction(transactionId);

        if (transaction.getStatus() != ShareTransaction.ShareTransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be rejected");
        }

        transaction.setStatus(ShareTransaction.ShareTransactionStatus.REJECTED);
        transaction.setProcessedBy(actorId);
        transaction.setProcessedAt(LocalDateTime.now());
        ShareTransaction saved = shareTransactionRepository.save(transaction);
        auditService.logEvent(actorId, "SHARE_TRANSACTION_REJECTED", "SHARE_TRANSACTION", transactionId, null);
        return ShareTransactionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ShareTransactionResponse> getAllShareTransactions() {
        return shareTransactionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(ShareTransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShareTransactionResponse> getMemberShareTransactions(Long memberId) {
        return shareTransactionRepository.findByMemberId(memberId).stream()
                .map(ShareTransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShareProductResponse> getActiveShareProducts() {
        return shareProductRepository.findByStatus("ACTIVE").stream()
                .map(ShareProductResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShareTransactionResponse getShareTransaction(Long transactionId) {
        return ShareTransactionResponse.fromEntity(findTransaction(transactionId));
    }

    @Transactional(readOnly = true)
    public ShareSummaryResponse getMemberSummary(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        return buildSummary(member, completedTransactions(memberId), activeProductMap());
    }

    @Transactional(readOnly = true)
    public SharePortfolioSummaryResponse getPortfolioSummary() {
        List<Member> members = memberRepository.findAll();
        Map<Long, ShareProduct> products = activeProductMap();
        List<ShareSummaryResponse> summaries = new ArrayList<>();

        for (Member member : members) {
            List<ShareTransaction> transactions = completedTransactions(member.getMemberId());
            if (transactions.isEmpty()) {
                continue;
            }
            ShareSummaryResponse summary = buildSummary(member, transactions, products);
            if (summary.getTotalShares() != null && summary.getTotalShares() > 0) {
                summaries.add(summary);
            }
        }

        summaries.sort(Comparator.comparing(ShareSummaryResponse::getTotalValue).reversed());
        int totalShares = summaries.stream().mapToInt(ShareSummaryResponse::getTotalShares).sum();
        BigDecimal totalValue = summaries.stream()
                .map(ShareSummaryResponse::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SharePortfolioSummaryResponse.builder()
                .totalMembersWithShares(summaries.size())
                .totalShares(totalShares)
                .totalValue(totalValue)
                .members(summaries)
                .build();
    }

    @Transactional(readOnly = true)
    public DividendCalculationResponse calculateDividends(BigDecimal ratePercent) {
        if (ratePercent == null || ratePercent.compareTo(BigDecimal.ZERO) < 0
                || ratePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("Dividend rate must be between 0 and 100 percent");
        }

        SharePortfolioSummaryResponse portfolio = getPortfolioSummary();
        BigDecimal factor = ratePercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        List<MemberDividendResponse> members = portfolio.getMembers().stream()
                .map(summary -> MemberDividendResponse.builder()
                        .memberId(summary.getMemberId())
                        .memberName(summary.getMemberName())
                        .membershipNo(summary.getMembershipNo())
                        .totalShares(summary.getTotalShares())
                        .shareValue(summary.getTotalValue())
                        .dividend(summary.getTotalValue().multiply(factor).setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();

        BigDecimal totalDividend = members.stream()
                .map(MemberDividendResponse::getDividend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DividendCalculationResponse.builder()
                .ratePercent(ratePercent)
                .totalShareValue(portfolio.getTotalValue())
                .totalDividend(totalDividend)
                .members(members)
                .build();
    }

    private ShareSummaryResponse buildSummary(Member member, List<ShareTransaction> transactions,
            Map<Long, ShareProduct> products) {
        Map<Long, HoldingAccumulator> holdings = new HashMap<>();
        for (ShareTransaction tx : transactions) {
            HoldingAccumulator acc = holdings.computeIfAbsent(tx.getProductId(), id -> new HoldingAccumulator());
            int signedQuantity = isDebit(tx.getTransactionType()) ? -tx.getQuantity() : tx.getQuantity();
            BigDecimal signedValue = isDebit(tx.getTransactionType()) ? tx.getAmount().negate() : tx.getAmount();
            acc.quantity += signedQuantity;
            acc.value = acc.value.add(signedValue);
            acc.unitPrice = tx.getUnitPrice();
        }

        List<ShareHoldingResponse> holdingResponses = holdings.entrySet().stream()
                .filter(entry -> entry.getValue().quantity != 0)
                .map(entry -> {
                    ShareProduct product = products.get(entry.getKey());
                    HoldingAccumulator acc = entry.getValue();
                    return ShareHoldingResponse.builder()
                            .productId(entry.getKey())
                            .productName(product == null ? "Product #" + entry.getKey() : product.getName())
                            .quantity(acc.quantity)
                            .unitPrice(acc.unitPrice)
                            .value(acc.value)
                            .build();
                })
                .sorted(Comparator.comparing(ShareHoldingResponse::getProductName))
                .toList();

        int totalShares = holdingResponses.stream().mapToInt(ShareHoldingResponse::getQuantity).sum();
        BigDecimal totalValue = holdingResponses.stream()
                .map(ShareHoldingResponse::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int requiredShares = products.values().stream()
                .mapToInt(ShareProduct::getMinShares)
                .max()
                .orElse(0);

        return ShareSummaryResponse.builder()
                .memberId(member.getMemberId())
                .memberName((member.getFirstName() + " " + member.getLastName()).trim())
                .membershipNo(member.getMembershipNo())
                .totalShares(totalShares)
                .totalValue(totalValue)
                .minimumSharesMet(totalShares >= requiredShares)
                .holdings(holdingResponses)
                .build();
    }

    private int availableQuantity(Long memberId, Long productId) {
        int quantity = 0;
        for (ShareTransaction tx : completedTransactions(memberId)) {
            if (!tx.getProductId().equals(productId)) {
                continue;
            }
            quantity += isDebit(tx.getTransactionType()) ? -tx.getQuantity() : tx.getQuantity();
        }
        return quantity;
    }

    private List<ShareTransaction> completedTransactions(Long memberId) {
        return shareTransactionRepository.findByMemberIdAndStatus(
                memberId, ShareTransaction.ShareTransactionStatus.COMPLETED);
    }

    private Map<Long, ShareProduct> activeProductMap() {
        return shareProductRepository.findAll().stream()
                .collect(Collectors.toMap(ShareProduct::getProductId, Function.identity()));
    }

    private ShareTransaction findTransaction(Long transactionId) {
        return shareTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
    }

    private boolean isDebit(ShareTransaction.ShareTransactionType type) {
        return type == ShareTransaction.ShareTransactionType.TRANSFER_OUT
                || type == ShareTransaction.ShareTransactionType.REDEMPTION;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class HoldingAccumulator {
        private int quantity;
        private BigDecimal value = BigDecimal.ZERO;
        private BigDecimal unitPrice = BigDecimal.ZERO;
    }
}
