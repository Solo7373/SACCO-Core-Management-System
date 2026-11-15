package com.sacco.collateral;

import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.collateral.dto.CollateralDocumentResponse;
import com.sacco.collateral.dto.CollateralReleaseRequest;
import com.sacco.collateral.dto.CollateralRequest;
import com.sacco.collateral.dto.CollateralResponse;
import com.sacco.collateral.entity.CollateralAsset;
import com.sacco.collateral.entity.CollateralDocument;
import com.sacco.collateral.entity.CollateralStatusHistory;
import com.sacco.collateral.repository.CollateralDocumentRepository;
import com.sacco.collateral.repository.CollateralRepository;
import com.sacco.collateral.repository.CollateralStatusHistoryRepository;
import com.sacco.common.FileStorageService;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollateralServiceTest {

    @Mock private CollateralRepository collateralRepository;
    @Mock private CollateralDocumentRepository documentRepository;
    @Mock private CollateralStatusHistoryRepository historyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountHolderRepository accountHolderRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditService auditService;

    @InjectMocks private CollateralService collateralService;

    private Member member;
    private Account loanAccount;
    private CollateralAsset heldAsset;
    private CollateralRequest request;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .memberId(1L)
                .membershipNo("SCM-20261103-ABCD")
                .firstName("Jane")
                .lastName("Doe")
                .status(Member.MemberStatus.ACTIVE)
                .build();

        loanAccount = Account.builder()
                .accountId(10L)
                .accountNumber("LN-20261103-ABC123")
                .accountType(Account.AccountType.LOAN_DISBURSEMENT)
                .status(Account.AccountStatus.ACTIVE)
                .currency("UGX")
                .build();

        heldAsset = CollateralAsset.builder()
                .collateralId(100L)
                .assetCode("COL-20261103-ABC123")
                .memberId(1L)
                .loanAccountId(10L)
                .assetType(CollateralAsset.AssetType.LAND_TITLE)
                .titleNumber("BLOCK 12 PLOT 34")
                .estimatedValue(new BigDecimal("50000000"))
                .forcedSaleValue(new BigDecimal("40000000"))
                .status(CollateralAsset.CollateralStatus.HELD_IN_CUSTODY)
                .registeredAt(LocalDateTime.now())
                .build();

        request = new CollateralRequest();
        request.setMemberId(1L);
        request.setLoanAccountId(10L);
        request.setAssetType(CollateralAsset.AssetType.LAND_TITLE);
        request.setTitleNumber("BLOCK 12 PLOT 34");
        request.setEstimatedValue(new BigDecimal("50000000"));
        request.setForcedSaleValue(new BigDecimal("40000000"));
        request.setValuationDate(LocalDate.now());
        request.setValuerName("Prime Valuers");
        request.setRegisteredOwnerName("Jane Doe");
        request.setCustodianLocation("SACCO Main Safe - Locker 3");
    }

    @Test
    void create_shouldRegisterHeldCollateralWithHistoryAndAudit() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(loanAccount));
        when(accountHolderRepository.findByAccountId(10L)).thenReturn(List.of(
                AccountHolder.builder().accountId(10L).memberId(1L).build()));
        when(collateralRepository.existsByAssetTypeAndTitleNumber(any(), any())).thenReturn(false);
        when(collateralRepository.save(any(CollateralAsset.class))).thenAnswer(invocation -> {
            CollateralAsset asset = invocation.getArgument(0);
            asset.setCollateralId(100L);
            asset.setRegisteredAt(LocalDateTime.now());
            return asset;
        });
        when(historyRepository.save(any(CollateralStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollateralResponse response = collateralService.create(request, 1L);

        assertThat(response.getAssetCode()).startsWith("COL-");
        assertThat(response.getStatus()).isEqualTo(CollateralAsset.CollateralStatus.HELD_IN_CUSTODY);
        assertThat(response.getMemberName()).isEqualTo("Jane Doe");
        assertThat(response.getLoanAccountNumber()).isEqualTo("LN-20261103-ABC123");
        assertThat(response.getEstimatedValue()).isEqualByComparingTo("50000000");
        verify(historyRepository).save(any(CollateralStatusHistory.class));
        verify(auditService).logEvent(eq(1L), eq("COLLATERAL_REGISTERED"), eq("COLLATERAL"), eq(100L), anyString());
    }

    @Test
    void create_shouldRejectNonLoanAccount() {
        Account savingsAccount = Account.builder()
                .accountId(10L)
                .accountNumber("SAV-20261103-ABC123")
                .accountType(Account.AccountType.SAVINGS)
                .status(Account.AccountStatus.ACTIVE)
                .build();

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(savingsAccount));

        assertThatThrownBy(() -> collateralService.create(request, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loan disbursement");
    }

    @Test
    void create_shouldRejectDuplicateTitle() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(loanAccount));
        when(accountHolderRepository.findByAccountId(10L)).thenReturn(List.of(
                AccountHolder.builder().accountId(10L).memberId(1L).build()));
        when(collateralRepository.existsByAssetTypeAndTitleNumber(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> collateralService.create(request, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void release_shouldChangeStatusAndRecordSignOff() {
        when(collateralRepository.findById(100L)).thenReturn(Optional.of(heldAsset));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(loanAccount));
        when(collateralRepository.save(any(CollateralAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(CollateralStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollateralReleaseRequest release = new CollateralReleaseRequest();
        release.setReleasedTo("Jane Doe");
        release.setReleaseDate(LocalDate.now());
        release.setAuthorizedBy("Manager Alice");
        release.setSignature("Alice Nakato");
        release.setNotes("Loan cleared in full");

        CollateralResponse response = collateralService.release(100L, release, 1L);

        assertThat(response.getStatus()).isEqualTo(CollateralAsset.CollateralStatus.RELEASED);
        assertThat(response.getReleasedTo()).isEqualTo("Jane Doe");
        assertThat(response.getReleaseAuthorizedBy()).isEqualTo("Manager Alice");
        assertThat(response.getReleaseSignature()).isEqualTo("Alice Nakato");
        assertThat(response.getReleaseDate()).isEqualTo(LocalDate.now());
        verify(historyRepository).save(any(CollateralStatusHistory.class));
        verify(auditService).logEvent(eq(1L), eq("COLLATERAL_RELEASED"), eq("COLLATERAL"), eq(100L), anyString());
    }

    @Test
    void release_shouldRejectAssetNotHeldInCustody() {
        heldAsset.setStatus(CollateralAsset.CollateralStatus.RELEASED);
        when(collateralRepository.findById(100L)).thenReturn(Optional.of(heldAsset));

        CollateralReleaseRequest release = new CollateralReleaseRequest();
        release.setAuthorizedBy("Manager Alice");
        release.setSignature("Alice Nakato");

        assertThatThrownBy(() -> collateralService.release(100L, release, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("held in custody");
    }

    @Test
    void markUnderDefault_shouldChangeStatus() {
        when(collateralRepository.findById(100L)).thenReturn(Optional.of(heldAsset));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(loanAccount));
        when(collateralRepository.save(any(CollateralAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(CollateralStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollateralResponse response = collateralService.markUnderDefault(100L, "Loan arrears", 1L);

        assertThat(response.getStatus()).isEqualTo(CollateralAsset.CollateralStatus.UNDER_DEFAULT_AUCTION);
        verify(auditService).logEvent(eq(1L), eq("COLLATERAL_MARKED_DEFAULT"), eq("COLLATERAL"), eq(100L), anyString());
    }

    @Test
    void uploadDocument_shouldStoreFileAndRecordHistory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "title-deed.pdf", "application/pdf", "sample".getBytes());

        when(collateralRepository.findById(100L)).thenReturn(Optional.of(heldAsset));
        when(fileStorageService.store(any(), eq("collateral/100/documents")))
                .thenReturn(new FileStorageService.StoredFile(
                        "collateral/100/documents/title-deed.pdf",
                        "title-deed.pdf",
                        "application/pdf",
                        1024L));
        when(documentRepository.save(any(CollateralDocument.class))).thenAnswer(invocation -> {
            CollateralDocument document = invocation.getArgument(0);
            document.setDocumentId(500L);
            document.setUploadedAt(LocalDateTime.now());
            return document;
        });
        when(historyRepository.save(any(CollateralStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollateralDocumentResponse response = collateralService.uploadDocument(
                100L, file, CollateralDocument.DocumentType.TITLE_DEED, "Title Deed", 1L);

        assertThat(response.getDocumentId()).isEqualTo(500L);
        assertThat(response.getDocumentName()).isEqualTo("Title Deed");
        assertThat(response.getFilePath()).isEqualTo("collateral/100/documents/title-deed.pdf");
        verify(auditService).logEvent(eq(1L), eq("COLLATERAL_DOCUMENT_UPLOADED"),
                eq("COLLATERAL"), eq(100L), eq("title-deed.pdf"));
    }
}
