package com.sacco.member;

import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.collateral.CollateralService;
import com.sacco.common.FileStorageService;
import com.sacco.member.dto.*;
import com.sacco.member.entity.IdentityDocument;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.IdentityDocumentRepository;
import com.sacco.member.repository.MemberRepository;
import com.sacco.savings.SavingsService;
import com.sacco.share.dto.ShareTransactionResponse;
import com.sacco.share.entity.ShareTransaction;
import com.sacco.share.repository.ShareTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final IdentityDocumentRepository documentRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountRepository accountRepository;
    private final SavingsService savingsService;
    private final ShareTransactionRepository shareTransactionRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final CollateralService collateralService;

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        List<IdentityDocumentResponse> documents = documentRepository.findByMemberId(memberId).stream()
                .map(IdentityDocumentResponse::fromEntity)
                .toList();

        List<Long> accountIds = accountHolderRepository.findByMemberId(memberId).stream()
                .map(AccountHolder::getAccountId)
                .toList();

        List<Account> accounts = accountIds.isEmpty() ? List.of() : accountRepository.findAllById(accountIds);

        List<MemberAccountSummary> accountSummaries = accounts.stream()
                .map(account -> MemberAccountSummary.builder()
                        .accountId(account.getAccountId())
                        .accountNumber(account.getAccountNumber())
                        .accountType(account.getAccountType())
                        .currency(account.getCurrency())
                        .status(account.getStatus())
                        .balance(savingsService.calculateBalance(account.getAccountId()))
                        .build())
                .toList();

        BigDecimal totalSavings = accountSummaries.stream()
                .map(MemberAccountSummary::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ShareTransactionResponse> shareTransactions = shareTransactionRepository.findByMemberId(memberId).stream()
                .map(ShareTransactionResponse::fromEntity)
                .toList();

        BigDecimal totalShareCapital = shareTransactions.stream()
                .filter(tx -> tx.getStatus() == ShareTransaction.ShareTransactionStatus.COMPLETED)
                .map(this::signedShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return MemberProfileResponse.builder()
                .member(MemberResponse.fromEntity(member))
                .documents(documents)
                .accounts(accountSummaries)
                .shareTransactions(shareTransactions)
                .collateral(collateralService.getByMember(memberId))
                .totalSavings(totalSavings)
                .totalShareCapital(totalShareCapital)
                .build();
    }

    @Transactional
    public MemberResponse uploadPhoto(Long memberId, MultipartFile file, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        FileStorageService.StoredFile stored = fileStorageService.store(file, "members/" + memberId + "/photo");
        if (member.getPassportPhotoPath() != null) {
            fileStorageService.delete(member.getPassportPhotoPath());
        }
        member.setPassportPhotoPath(stored.path());
        memberRepository.save(member);

        auditService.logEvent(actorId, "MEMBER_PHOTO_UPLOADED", "MEMBER", memberId, stored.originalName());
        return MemberResponse.fromEntity(member);
    }

    @Transactional
    public IdentityDocumentResponse uploadDocumentFile(Long memberId, Long documentId, MultipartFile file, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        IdentityDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        if (!document.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException("Document does not belong to member " + memberId);
        }

        FileStorageService.StoredFile stored = fileStorageService.store(file, "members/" + memberId + "/documents");
        if (document.getFilePath() != null) {
            fileStorageService.delete(document.getFilePath());
        }

        document.setFilePath(stored.path());
        document.setFileName(stored.originalName());
        document.setFileType(stored.contentType());
        document.setFileSize(stored.size());
        documentRepository.save(document);

        auditService.logEvent(actorId, "IDENTITY_DOCUMENT_UPLOADED", "IDENTITY_DOCUMENT", documentId, stored.originalName());
        return IdentityDocumentResponse.fromEntity(document);
    }

    private BigDecimal signedShareAmount(ShareTransactionResponse tx) {
        return switch (tx.getTransactionType()) {
            case PURCHASE, TRANSFER_IN -> tx.getAmount();
            case TRANSFER_OUT, REDEMPTION -> tx.getAmount().negate();
        };
    }
}
