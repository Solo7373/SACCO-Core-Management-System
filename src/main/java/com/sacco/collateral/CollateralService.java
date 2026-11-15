package com.sacco.collateral;

import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.collateral.dto.*;
import com.sacco.collateral.entity.CollateralAsset;
import com.sacco.collateral.entity.CollateralDocument;
import com.sacco.collateral.entity.CollateralStatusHistory;
import com.sacco.collateral.repository.CollateralDocumentRepository;
import com.sacco.collateral.repository.CollateralRepository;
import com.sacco.collateral.repository.CollateralStatusHistoryRepository;
import com.sacco.common.FileStorageService;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollateralService {

    private final CollateralRepository collateralRepository;
    private final CollateralDocumentRepository documentRepository;
    private final CollateralStatusHistoryRepository historyRepository;
    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    private static final DateTimeFormatter CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional(readOnly = true)
    public List<CollateralResponse> getAll() {
        List<CollateralAsset> assets = collateralRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return toResponses(assets);
    }

    @Transactional(readOnly = true)
    public List<CollateralResponse> getByMember(Long memberId) {
        return toResponses(collateralRepository.findByMemberIdOrderByCreatedAtDesc(memberId));
    }

    @Transactional(readOnly = true)
    public CollateralResponse getById(Long collateralId) {
        CollateralAsset asset = findAsset(collateralId);
        CollateralResponse response = toResponse(
                asset,
                findMember(asset.getMemberId()),
                findAccount(asset.getLoanAccountId()));

        response.setDocuments(documentRepository.findByCollateralIdOrderByUploadedAtDesc(collateralId).stream()
                .map(CollateralDocumentResponse::fromEntity)
                .toList());
        response.setHistory(historyRepository.findByCollateralIdOrderByCreatedAtDesc(collateralId).stream()
                .map(CollateralStatusHistoryResponse::fromEntity)
                .toList());
        return response;
    }

    @Transactional
    public CollateralResponse create(CollateralRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = findMember(request.getMemberId());
        Account loanAccount = validateLoanAccount(request.getLoanAccountId(), member.getMemberId());
        validateAmounts(request);
        validateUniqueTitle(request.getAssetType(), request.getTitleNumber(), null);

        CollateralAsset asset = CollateralAsset.builder()
                .assetCode(generateAssetCode())
                .memberId(member.getMemberId())
                .loanAccountId(loanAccount == null ? null : loanAccount.getAccountId())
                .assetType(request.getAssetType())
                .titleNumber(request.getTitleNumber().trim())
                .description(trimToNull(request.getDescription()))
                .estimatedValue(request.getEstimatedValue())
                .forcedSaleValue(request.getForcedSaleValue())
                .valuationDate(request.getValuationDate())
                .valuerName(trimToNull(request.getValuerName()))
                .registeredOwnerName(trimToNull(request.getRegisteredOwnerName()))
                .custodianLocation(trimToNull(request.getCustodianLocation()))
                .status(CollateralAsset.CollateralStatus.HELD_IN_CUSTODY)
                .registeredBy(actorId)
                .build();

        CollateralAsset saved = collateralRepository.save(asset);
        recordHistory(saved, null, saved.getStatus(), "REGISTERED",
                "Collateral registered: " + saved.getAssetCode(), actorId, null, null);
        auditService.logEvent(actorId, "COLLATERAL_REGISTERED", "COLLATERAL", saved.getCollateralId(),
                "Asset: " + saved.getAssetCode() + ", Type: " + saved.getAssetType());

        return CollateralResponse.fromEntity(saved, member, loanAccount);
    }

    @Transactional
    public CollateralResponse update(Long collateralId, CollateralRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        CollateralAsset asset = findAsset(collateralId);
        if (asset.getStatus() == CollateralAsset.CollateralStatus.RELEASED) {
            throw new IllegalStateException("Released collateral records cannot be edited");
        }

        Member member = findMember(request.getMemberId());
        Account loanAccount = validateLoanAccount(request.getLoanAccountId(), member.getMemberId());
        validateAmounts(request);
        validateUniqueTitle(request.getAssetType(), request.getTitleNumber(), collateralId);

        asset.setMemberId(member.getMemberId());
        asset.setLoanAccountId(loanAccount == null ? null : loanAccount.getAccountId());
        asset.setAssetType(request.getAssetType());
        asset.setTitleNumber(request.getTitleNumber().trim());
        asset.setDescription(trimToNull(request.getDescription()));
        asset.setEstimatedValue(request.getEstimatedValue());
        asset.setForcedSaleValue(request.getForcedSaleValue());
        asset.setValuationDate(request.getValuationDate());
        asset.setValuerName(trimToNull(request.getValuerName()));
        asset.setRegisteredOwnerName(trimToNull(request.getRegisteredOwnerName()));
        asset.setCustodianLocation(trimToNull(request.getCustodianLocation()));

        CollateralAsset saved = collateralRepository.save(asset);
        recordHistory(saved, saved.getStatus(), saved.getStatus(), "UPDATED",
                "Collateral details updated", actorId, null, null);
        auditService.logEvent(actorId, "COLLATERAL_UPDATED", "COLLATERAL", collateralId,
                "Asset: " + saved.getAssetCode());

        return CollateralResponse.fromEntity(saved, member, loanAccount);
    }

    @Transactional
    public CollateralResponse release(Long collateralId, CollateralReleaseRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        CollateralAsset asset = findAsset(collateralId);
        if (asset.getStatus() != CollateralAsset.CollateralStatus.HELD_IN_CUSTODY) {
            throw new IllegalStateException("Only collateral currently held in custody can be released");
        }

        Member member = findMember(asset.getMemberId());
        String releasedTo = trimToNull(request.getReleasedTo());
        if (releasedTo == null) {
            releasedTo = (member.getFirstName() + " " + member.getLastName()).trim();
        }

        CollateralAsset.CollateralStatus from = asset.getStatus();
        asset.setStatus(CollateralAsset.CollateralStatus.RELEASED);
        asset.setReleasedTo(releasedTo);
        asset.setReleaseDate(request.getReleaseDate() == null ? LocalDate.now() : request.getReleaseDate());
        asset.setReleaseAuthorizedBy(request.getAuthorizedBy().trim());
        asset.setReleaseAuthorizedAt(LocalDateTime.now());
        asset.setReleaseSignature(request.getSignature().trim());
        asset.setReleaseNotes(trimToNull(request.getNotes()));

        CollateralAsset saved = collateralRepository.save(asset);
        String historyNotes = trimToNull(request.getNotes());
        recordHistory(saved, from, saved.getStatus(), "RELEASED",
                historyNotes == null ? "Released to " + releasedTo : historyNotes,
                actorId, saved.getReleaseAuthorizedBy(), saved.getReleaseSignature());
        auditService.logEvent(actorId, "COLLATERAL_RELEASED", "COLLATERAL", collateralId,
                "Released to " + releasedTo + ", authorized by " + saved.getReleaseAuthorizedBy());

        return CollateralResponse.fromEntity(saved, member, findAccount(saved.getLoanAccountId()));
    }

    @Transactional
    public CollateralResponse markUnderDefault(Long collateralId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        CollateralAsset asset = findAsset(collateralId);
        if (asset.getStatus() != CollateralAsset.CollateralStatus.HELD_IN_CUSTODY) {
            throw new IllegalStateException("Only collateral currently held in custody can be marked under default");
        }

        CollateralAsset.CollateralStatus from = asset.getStatus();
        asset.setStatus(CollateralAsset.CollateralStatus.UNDER_DEFAULT_AUCTION);
        CollateralAsset saved = collateralRepository.save(asset);
        recordHistory(saved, from, saved.getStatus(), "MARKED_UNDER_DEFAULT", reason, actorId, null, null);
        auditService.logEvent(actorId, "COLLATERAL_MARKED_DEFAULT", "COLLATERAL", collateralId, reason);

        return CollateralResponse.fromEntity(saved, findMember(saved.getMemberId()),
                findAccount(saved.getLoanAccountId()));
    }

    @Transactional
    public CollateralDocumentResponse uploadDocument(Long collateralId, MultipartFile file,
            CollateralDocument.DocumentType documentType, String documentName, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        CollateralAsset asset = findAsset(collateralId);
        if (asset.getStatus() == CollateralAsset.CollateralStatus.RELEASED) {
            throw new IllegalStateException("Documents cannot be added to released collateral");
        }

        FileStorageService.StoredFile stored = fileStorageService.store(file, "collateral/" + collateralId + "/documents");
        String name = trimToNull(documentName);
        if (name == null) {
            name = stored.originalName();
        }

        CollateralDocument document = CollateralDocument.builder()
                .collateralId(collateralId)
                .documentType(documentType)
                .documentName(name)
                .filePath(stored.path())
                .fileName(stored.originalName())
                .fileType(stored.contentType())
                .fileSize(stored.size())
                .uploadedBy(actorId)
                .build();

        CollateralDocument saved = documentRepository.save(document);
        recordHistory(asset, asset.getStatus(), asset.getStatus(), "DOCUMENT_UPLOADED",
                documentType + ": " + name, actorId, null, null);
        auditService.logEvent(actorId, "COLLATERAL_DOCUMENT_UPLOADED", "COLLATERAL", collateralId,
                stored.originalName());

        return CollateralDocumentResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteDocument(Long collateralId, Long documentId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        CollateralAsset asset = findAsset(collateralId);
        if (asset.getStatus() == CollateralAsset.CollateralStatus.RELEASED) {
            throw new IllegalStateException("Documents cannot be removed from released collateral");
        }

        CollateralDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Collateral document not found: " + documentId));
        if (!document.getCollateralId().equals(collateralId)) {
            throw new IllegalArgumentException("Document does not belong to collateral " + collateralId);
        }

        fileStorageService.delete(document.getFilePath());
        documentRepository.delete(document);
        recordHistory(asset, asset.getStatus(), asset.getStatus(), "DOCUMENT_DELETED",
                document.getDocumentType() + ": " + document.getDocumentName(), actorId, null, null);
        auditService.logEvent(actorId, "COLLATERAL_DOCUMENT_DELETED", "COLLATERAL", collateralId,
                document.getDocumentName());
    }

    private CollateralAsset findAsset(Long collateralId) {
        return collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("Collateral not found: " + collateralId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
    }

    private Account findAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId).orElse(null);
    }

    private Account validateLoanAccount(Long loanAccountId, Long memberId) {
        if (loanAccountId == null) {
            return null;
        }
        Account account = accountRepository.findById(loanAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found: " + loanAccountId));
        if (account.getAccountType() != Account.AccountType.LOAN_DISBURSEMENT) {
            throw new IllegalStateException("The linked account must be a loan disbursement account");
        }
        boolean belongsToMember = accountHolderRepository.findByAccountId(loanAccountId).stream()
                .map(AccountHolder::getMemberId)
                .anyMatch(id -> id.equals(memberId));
        if (!belongsToMember) {
            throw new IllegalStateException("The selected loan account does not belong to the selected member");
        }
        return account;
    }

    private void validateAmounts(CollateralRequest request) {
        if (request.getForcedSaleValue() != null
                && request.getEstimatedValue() != null
                && request.getForcedSaleValue().compareTo(request.getEstimatedValue()) > 0) {
            throw new IllegalStateException("Forced sale value cannot exceed the estimated / market value");
        }
    }

    private void validateUniqueTitle(CollateralAsset.AssetType assetType, String titleNumber, Long collateralId) {
        String normalized = titleNumber == null ? "" : titleNumber.trim();
        boolean exists = collateralId == null
                ? collateralRepository.existsByAssetTypeAndTitleNumber(assetType, normalized)
                : collateralRepository.existsByAssetTypeAndTitleNumberAndCollateralIdNot(assetType, normalized, collateralId);
        if (exists) {
            throw new IllegalStateException("An asset with this title / registration number already exists for the selected asset type");
        }
    }

    private String generateAssetCode() {
        return "COL-" + LocalDateTime.now().format(CODE_DATE_FORMAT) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void recordHistory(CollateralAsset asset, CollateralAsset.CollateralStatus from,
            CollateralAsset.CollateralStatus to, String action, String notes,
            Long actorId, String authorizedBy, String signature) {
        historyRepository.save(CollateralStatusHistory.builder()
                .collateralId(asset.getCollateralId())
                .fromStatus(from)
                .toStatus(to)
                .action(action)
                .notes(notes)
                .actorId(actorId)
                .authorizedBy(authorizedBy)
                .signature(signature)
                .build());
    }

    private List<CollateralResponse> toResponses(List<CollateralAsset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }

        Set<Long> memberIds = assets.stream().map(CollateralAsset::getMemberId).collect(Collectors.toSet());
        Set<Long> accountIds = assets.stream()
                .map(CollateralAsset::getLoanAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, member -> member));
        Map<Long, Account> accounts = accountIds.isEmpty()
                ? Map.of()
                : accountRepository.findAllById(accountIds).stream()
                        .collect(Collectors.toMap(Account::getAccountId, account -> account));

        return assets.stream()
                .map(asset -> toResponse(asset, members.get(asset.getMemberId()),
                        accounts.get(asset.getLoanAccountId())))
                .toList();
    }

    private CollateralResponse toResponse(CollateralAsset asset, Member member, Account account) {
        return CollateralResponse.fromEntity(asset, member, account);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
