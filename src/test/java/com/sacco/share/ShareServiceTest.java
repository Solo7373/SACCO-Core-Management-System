package com.sacco.share;

import com.sacco.audit.AuditService;
import com.sacco.common.ReferenceNumberService;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.share.dto.DividendCalculationResponse;
import com.sacco.share.dto.SharePortfolioSummaryResponse;
import com.sacco.share.dto.ShareProductRequest;
import com.sacco.share.dto.ShareProductResponse;
import com.sacco.share.dto.ShareSummaryResponse;
import com.sacco.share.dto.ShareTransactionRequest;
import com.sacco.share.dto.ShareTransactionResponse;
import com.sacco.share.entity.ShareProduct;
import com.sacco.share.entity.ShareTransaction;
import com.sacco.share.repository.ShareProductRepository;
import com.sacco.share.repository.ShareTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock private ShareProductRepository shareProductRepository;
    @Mock private ShareTransactionRepository shareTransactionRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ReferenceNumberService referenceNumberService;
    @Mock private AuditService auditService;

    @InjectMocks private ShareService shareService;

    private ShareProductRequest productRequest;
    private ShareProduct savedProduct;
    private ShareTransactionRequest transactionRequest;
    private ShareTransaction savedTransaction;
    private Member activeMember;

    @BeforeEach
    void setUp() {
        productRequest = new ShareProductRequest();
        productRequest.setName("Founding Member Shares");
        productRequest.setDescription("Initial share capital for new members");
        productRequest.setMinShares(10);
        productRequest.setPricePerShare(new BigDecimal("5000.00"));

        savedProduct = ShareProduct.builder()
                .productId(1L)
                .name("Founding Member Shares")
                .minShares(10)
                .pricePerShare(new BigDecimal("5000.00"))
                .status("ACTIVE")
                .build();

        activeMember = Member.builder()
                .memberId(1L)
                .membershipNo("MEM-2026-0001")
                .firstName("John")
                .lastName("Doe")
                .status(Member.MemberStatus.ACTIVE)
                .build();

        transactionRequest = new ShareTransactionRequest();
        transactionRequest.setMemberId(1L);
        transactionRequest.setProductId(1L);
        transactionRequest.setQuantity(20);
        transactionRequest.setAmount(new BigDecimal("100000.00"));
        transactionRequest.setTransactionType(ShareTransaction.ShareTransactionType.PURCHASE);
        transactionRequest.setReference("REF-001");

        savedTransaction = ShareTransaction.builder()
                .transactionId(1L)
                .memberId(1L)
                .productId(1L)
                .quantity(20)
                .unitPrice(new BigDecimal("5000.00"))
                .amount(new BigDecimal("100000.00"))
                .transactionType(ShareTransaction.ShareTransactionType.PURCHASE)
                .status(ShareTransaction.ShareTransactionStatus.PENDING)
                .build();
    }

    @Test
    void createShareProduct_shouldCreateActiveProduct() {
        when(shareProductRepository.save(any(ShareProduct.class))).thenReturn(savedProduct);

        ShareProductResponse response = shareService.createShareProduct(productRequest, 1L);

        assertThat(response.getName()).isEqualTo("Founding Member Shares");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void recordShareTransaction_shouldRejectInactiveMember() {
        activeMember.setStatus(Member.MemberStatus.SUSPENDED);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));

        assertThatThrownBy(() -> shareService.recordShareTransaction(transactionRequest, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active members");
    }

    @Test
    void recordShareTransaction_shouldRejectWhenProductInactive() {
        savedProduct.setStatus("INACTIVE");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));
        when(shareProductRepository.findById(1L)).thenReturn(Optional.of(savedProduct));

        assertThatThrownBy(() -> shareService.recordShareTransaction(transactionRequest, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void recordShareTransaction_shouldRejectWhenBelowMinimumShares() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));
        when(shareProductRepository.findById(1L)).thenReturn(Optional.of(savedProduct));

        transactionRequest.setQuantity(5);
        transactionRequest.setAmount(new BigDecimal("25000.00"));

        assertThatThrownBy(() -> shareService.recordShareTransaction(transactionRequest, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Minimum");
    }

    @Test
    void recordShareTransaction_shouldRejectAmountMismatch() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));
        when(shareProductRepository.findById(1L)).thenReturn(Optional.of(savedProduct));

        transactionRequest.setAmount(new BigDecimal("99999.00"));

        assertThatThrownBy(() -> shareService.recordShareTransaction(transactionRequest, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("price per share");
    }

    @Test
    void recordShareTransaction_shouldRecordPricedPendingTransaction() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));
        when(shareProductRepository.findById(1L)).thenReturn(Optional.of(savedProduct));
        when(shareTransactionRepository.save(any(ShareTransaction.class))).thenAnswer(inv -> {
            ShareTransaction tx = inv.getArgument(0);
            tx.setTransactionId(1L);
            return tx;
        });

        ShareTransactionResponse response = shareService.recordShareTransaction(transactionRequest, 1L);

        assertThat(response.getStatus()).isEqualTo(ShareTransaction.ShareTransactionStatus.PENDING);
        assertThat(response.getUnitPrice()).isEqualByComparingTo("5000.00");
        assertThat(response.getAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.getReference()).isEqualTo("REF-001");
    }

    @Test
    void approveShareTransaction_shouldChangeStatusToCompleted() {
        when(shareTransactionRepository.findById(1L)).thenReturn(Optional.of(savedTransaction));
        when(shareTransactionRepository.save(any(ShareTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        ShareTransactionResponse response = shareService.approveShareTransaction(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(ShareTransaction.ShareTransactionStatus.COMPLETED);
        assertThat(response.getProcessedBy()).isEqualTo(1L);
        assertThat(response.getProcessedAt()).isNotNull();
    }

    @Test
    void approveShareTransaction_shouldRejectNonPendingTransaction() {
        savedTransaction.setStatus(ShareTransaction.ShareTransactionStatus.COMPLETED);
        when(shareTransactionRepository.findById(1L)).thenReturn(Optional.of(savedTransaction));

        assertThatThrownBy(() -> shareService.approveShareTransaction(1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void getMemberShareTransactions_shouldReturnTransactions() {
        when(shareTransactionRepository.findByMemberId(1L)).thenReturn(List.of(savedTransaction));

        List<ShareTransactionResponse> result = shareService.getMemberShareTransactions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualTo(20);
    }

    @Test
    void getMemberSummary_shouldAggregateCompletedHoldings() {
        savedTransaction.setStatus(ShareTransaction.ShareTransactionStatus.COMPLETED);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));
        when(shareTransactionRepository.findByMemberIdAndStatus(
                1L, ShareTransaction.ShareTransactionStatus.COMPLETED)).thenReturn(List.of(savedTransaction));
        when(shareProductRepository.findAll()).thenReturn(List.of(savedProduct));

        ShareSummaryResponse summary = shareService.getMemberSummary(1L);

        assertThat(summary.getTotalShares()).isEqualTo(20);
        assertThat(summary.getTotalValue()).isEqualByComparingTo("100000.00");
        assertThat(summary.isMinimumSharesMet()).isTrue();
        assertThat(summary.getHoldings()).hasSize(1);
    }

    @Test
    void getPortfolioSummary_shouldAggregateAcrossMembers() {
        savedTransaction.setStatus(ShareTransaction.ShareTransactionStatus.COMPLETED);
        when(memberRepository.findAll()).thenReturn(List.of(activeMember));
        when(shareTransactionRepository.findByMemberIdAndStatus(
                1L, ShareTransaction.ShareTransactionStatus.COMPLETED)).thenReturn(List.of(savedTransaction));
        when(shareProductRepository.findAll()).thenReturn(List.of(savedProduct));

        SharePortfolioSummaryResponse portfolio = shareService.getPortfolioSummary();

        assertThat(portfolio.getTotalMembersWithShares()).isEqualTo(1);
        assertThat(portfolio.getTotalShares()).isEqualTo(20);
        assertThat(portfolio.getTotalValue()).isEqualByComparingTo("100000.00");
    }

    @Test
    void calculateDividends_shouldApplyRateToShareValue() {
        savedTransaction.setStatus(ShareTransaction.ShareTransactionStatus.COMPLETED);
        when(memberRepository.findAll()).thenReturn(List.of(activeMember));
        when(shareTransactionRepository.findByMemberIdAndStatus(
                1L, ShareTransaction.ShareTransactionStatus.COMPLETED)).thenReturn(List.of(savedTransaction));
        when(shareProductRepository.findAll()).thenReturn(List.of(savedProduct));

        DividendCalculationResponse dividends = shareService.calculateDividends(new BigDecimal("15"));

        assertThat(dividends.getTotalShareValue()).isEqualByComparingTo("100000.00");
        assertThat(dividends.getTotalDividend()).isEqualByComparingTo("15000.00");
        assertThat(dividends.getMembers()).hasSize(1);
    }

    @Test
    void calculateDividends_shouldRejectInvalidRate() {
        assertThatThrownBy(() -> shareService.calculateDividends(new BigDecimal("150")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 0 and 100");
    }
}
