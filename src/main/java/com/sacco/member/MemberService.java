package com.sacco.member;

import com.sacco.audit.AuditService;
import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanGuarantor;
import com.sacco.loan.repository.LoanGuarantorRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.member.dto.*;
import com.sacco.member.entity.IdentityDocument;
import com.sacco.member.entity.Member;
import com.sacco.member.entity.VerificationRecord;
import com.sacco.member.repository.IdentityDocumentRepository;
import com.sacco.member.repository.MemberRepository;
import com.sacco.member.repository.VerificationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final IdentityDocumentRepository documentRepository;
    private final VerificationRecordRepository verificationRecordRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoanRepository loanRepository;
    private final LoanGuarantorRepository loanGuarantorRepository;

    private static final DateTimeFormatter MEMBERSHIP_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<Loan.LoanStatus> ACTIVE_LOAN_STATUSES =
            List.of(Loan.LoanStatus.DISBURSED, Loan.LoanStatus.IN_ARREARS);

    @Transactional
    public MemberResponse registerMember(MemberRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        String membershipNo = "SCM-" + LocalDateTime.now().format(MEMBERSHIP_NO_FORMAT) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        Member member = Member.builder()
                .membershipNo(membershipNo)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .gender(request.getGender())
                .maritalStatus(request.getMaritalStatus())
                .nextOfKinName(request.getNextOfKinName())
                .nextOfKinRelationship(request.getNextOfKinRelationship())
                .nextOfKinPhone(request.getNextOfKinPhone())
                .nextOfKinAddress(request.getNextOfKinAddress())
                .status(Member.MemberStatus.PENDING)
                .build();

        Member saved = memberRepository.save(member);
        auditService.logEvent(actorId, "MEMBER_REGISTERED", "MEMBER", saved.getMemberId(), "Membership No: " + membershipNo);
        return MemberResponse.fromEntity(saved);
    }

    @Transactional
    public MemberResponse registerMemberWithDocuments(MemberRegistrationRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        if (request.getIdentityDocuments() == null || request.getIdentityDocuments().isEmpty()) {
            throw new IllegalStateException("At least one identity document is required");
        }

        String membershipNo = "SCM-" + LocalDateTime.now().format(MEMBERSHIP_NO_FORMAT) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        Member member = Member.builder()
                .membershipNo(membershipNo)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .gender(request.getGender())
                .maritalStatus(request.getMaritalStatus())
                .nextOfKinName(request.getNextOfKinName())
                .nextOfKinRelationship(request.getNextOfKinRelationship())
                .nextOfKinPhone(request.getNextOfKinPhone())
                .nextOfKinAddress(request.getNextOfKinAddress())
                .status(Member.MemberStatus.PENDING)
                .build();

        Member saved = memberRepository.save(member);

        for (IdentityDocumentRequest docReq : request.getIdentityDocuments()) {
            boolean duplicate = documentRepository.existsByDocumentTypeAndDocumentNumber(docReq.getDocumentType(), docReq.getDocumentNumber());
            if (duplicate) {
                throw new IllegalStateException("A " + docReq.getDocumentType() + " document with number " + docReq.getDocumentNumber() + " already exists");
            }

            IdentityDocument document = IdentityDocument.builder()
                    .memberId(saved.getMemberId())
                    .documentType(docReq.getDocumentType())
                    .documentNumber(docReq.getDocumentNumber())
                    .issuer(docReq.getIssuer())
                    .issueDate(docReq.getIssueDate())
                    .expiryDate(docReq.getExpiryDate())
                    .status(IdentityDocument.VerificationStatus.PENDING)
                    .build();
            documentRepository.save(document);
        }

        auditService.logEvent(actorId, "MEMBER_REGISTERED", "MEMBER", saved.getMemberId(), 
                "Membership No: " + membershipNo + ", Documents: " + request.getIdentityDocuments().size());
        return MemberResponse.fromEntity(saved);
    }

    @Transactional
    public IdentityDocumentResponse addIdentityDocument(Long memberId, IdentityDocumentRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        requireNotArchived(member);

        boolean duplicate = documentRepository.existsByDocumentTypeAndDocumentNumber(request.getDocumentType(), request.getDocumentNumber());
        if (duplicate) {
            throw new IllegalStateException("A " + request.getDocumentType() + " document with number " + request.getDocumentNumber() + " already exists");
        }

        IdentityDocument document = IdentityDocument.builder()
                .memberId(memberId)
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .issuer(request.getIssuer())
                .issueDate(request.getIssueDate())
                .expiryDate(request.getExpiryDate())
                .status(IdentityDocument.VerificationStatus.PENDING)
                .build();

        IdentityDocument saved = documentRepository.save(document);
        auditService.logEvent(actorId, "IDENTITY_DOCUMENT_ADDED", "IDENTITY_DOCUMENT", saved.getDocumentId(), 
                "Type: " + request.getDocumentType() + ", Member: " + memberId);
        return IdentityDocumentResponse.fromEntity(saved);
    }

    @Transactional
    public IdentityDocumentResponse submitVerification(VerificationRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        IdentityDocument document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + request.getDocumentId()));
        Member verificationMember = memberRepository.findById(document.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        requireNotArchived(verificationMember);

        VerificationRecord record = VerificationRecord.builder()
                .documentId(request.getDocumentId())
                .method(request.getMethod())
                .outcome(request.getOutcome())
                .reviewerId(request.getReviewerId())
                .evidenceReference(request.getEvidenceReference())
                .build();
        verificationRecordRepository.save(record);

        document.setStatus(request.getOutcome());
        documentRepository.save(document);

        if (request.getOutcome() == IdentityDocument.VerificationStatus.VERIFIED) {
            List<IdentityDocument> memberDocs = documentRepository.findByMemberId(document.getMemberId());
            boolean allVerified = memberDocs.stream().allMatch(d -> d.getStatus() == IdentityDocument.VerificationStatus.VERIFIED);
            if (allVerified) {
                Member member = memberRepository.findById(document.getMemberId())
                        .orElseThrow(() -> new IllegalArgumentException("Member not found"));
                member.setStatus(Member.MemberStatus.ACTIVE);
                memberRepository.save(member);
            }
        }

        auditService.logEvent(actorId, "VERIFICATION_SUBMITTED", "IDENTITY_DOCUMENT", request.getDocumentId(), 
                "Outcome: " + request.getOutcome());
        return IdentityDocumentResponse.fromEntity(document);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getAllMembers() {
        return memberRepository.findByIsDeletedFalse().stream()
                .map(MemberResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getArchivedMembers() {
        return memberRepository.findByIsDeletedTrue().stream()
                .map(MemberResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        return MemberResponse.fromEntity(member);
    }

    @Transactional(readOnly = true)
    public List<IdentityDocumentResponse> getMemberDocuments(Long memberId) {
        return documentRepository.findByMemberId(memberId).stream()
                .map(IdentityDocumentResponse::fromEntity)
                .toList();
    }

    @Transactional
    public MemberResponse updateMember(Long memberId, MemberUpdateRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        requireNotArchived(member);

        if (request.getFirstName() != null) {
            if (request.getFirstName().trim().isEmpty()) {
                throw new IllegalStateException("First name cannot be empty");
            }
            member.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            if (request.getLastName().trim().isEmpty()) {
                throw new IllegalStateException("Last name cannot be empty");
            }
            member.setLastName(request.getLastName().trim());
        }
        if (request.getDateOfBirth() != null) {
            member.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getPhone() != null) {
            member.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            member.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getAddress() != null) {
            member.setAddress(trimToNull(request.getAddress()));
        }
        if (request.getGender() != null) {
            member.setGender(trimToNull(request.getGender()));
        }
        if (request.getMaritalStatus() != null) {
            member.setMaritalStatus(trimToNull(request.getMaritalStatus()));
        }
        if (request.getNextOfKinName() != null) {
            member.setNextOfKinName(trimToNull(request.getNextOfKinName()));
        }
        if (request.getNextOfKinRelationship() != null) {
            member.setNextOfKinRelationship(trimToNull(request.getNextOfKinRelationship()));
        }
        if (request.getNextOfKinPhone() != null) {
            member.setNextOfKinPhone(trimToNull(request.getNextOfKinPhone()));
        }
        if (request.getNextOfKinAddress() != null) {
            member.setNextOfKinAddress(trimToNull(request.getNextOfKinAddress()));
        }

        Member saved = memberRepository.save(member);
        auditService.logEvent(actorId, "MEMBER_PROFILE_UPDATED", "MEMBER", memberId,
                "Personal and contact details updated by staff");
        return MemberResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public MemberUserResponse getMemberUser(Long memberId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        return userRepository.findByMemberId(memberId)
                .map(MemberUserResponse::fromEntity)
                .orElse(null);
    }

    @Transactional
    public MemberUserResponse upsertMemberUser(Long memberId, MemberCredentialsRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        requireNotArchived(member);

        String username = request.getUsername().trim();
        User user = userRepository.findByMemberId(memberId).orElse(null);

        if (user == null) {
            if (request.getPassword() == null || request.getPassword().length() < 8) {
                throw new IllegalStateException("A password of at least 8 characters is required to create a login");
            }
            if (userRepository.existsByUsername(username)) {
                throw new IllegalStateException("Username already exists");
            }
            user = User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .email(request.getEmail() == null ? member.getEmail() : trimToNull(request.getEmail()))
                    .memberId(memberId)
                    .role("MEMBER")
                    .isActive(request.getIsActive() == null ? true : request.getIsActive())
                    .build();
            User saved = userRepository.save(user);
            auditService.logEvent(actorId, "MEMBER_LOGIN_CREATED", "USER", saved.getUserId(),
                    "Login created for member " + member.getMembershipNo());
            return MemberUserResponse.fromEntity(saved);
        }

        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username already exists");
        }
        user.setUsername(username);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 8) {
                throw new IllegalStateException("Password must be at least 8 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setPasswordResetRequired(false);
        }
        if (request.getEmail() != null) {
            user.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
        User saved = userRepository.save(user);
        auditService.logEvent(actorId, "MEMBER_LOGIN_UPDATED", "USER", saved.getUserId(),
                "Login credentials updated for member " + member.getMembershipNo());
        return MemberUserResponse.fromEntity(saved);
    }

    @Transactional
    public MemberResponse rejectMember(Long memberId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        requireNotArchived(member);
        member.setStatus(Member.MemberStatus.REJECTED);
        member.setRejectionReason(reason);
        memberRepository.save(member);
        auditService.logEvent(actorId, "MEMBER_REJECTED", "MEMBER", memberId, reason);
        return MemberResponse.fromEntity(member);
    }

    @Transactional
    public MemberResponse closeMember(Long memberId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        requireNotArchived(member);
        assertClosureAllowed(member);
        member.setStatus(Member.MemberStatus.CLOSED);
        memberRepository.save(member);
        auditService.logEvent(actorId, "MEMBER_CLOSED", "MEMBER", memberId, "Member closed");
        return MemberResponse.fromEntity(member);
    }

    /**
     * Soft-delete protocol. The member row is never removed: it is flagged,
     * closed and its login is sealed with an anonymized username and a random
     * password hash. All financial history and foreign keys remain intact.
     */
    @Transactional
    public MemberResponse archiveMember(Long memberId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        if (Boolean.TRUE.equals(member.getIsDeleted())) {
            throw new IllegalStateException("Member is already archived");
        }
        assertClosureAllowed(member);

        member.setIsDeleted(true);
        member.setDeletedAt(LocalDateTime.now());
        member.setDeletionReason(reason == null || reason.isBlank()
                ? "Archived by staff"
                : reason.trim());
        member.setStatus(Member.MemberStatus.INACTIVE);
        memberRepository.save(member);

        userRepository.findByMemberId(memberId).ifPresent(user -> {
            if (user.getArchivedUsername() == null) {
                user.setArchivedUsername(user.getUsername());
            }
            user.setUsername("archived_" + memberId + "_"
                    + UUID.randomUUID().toString().substring(0, 8));
            user.setEmail(null);
            user.setIsActive(false);
            user.setPasswordResetRequired(true);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            userRepository.save(user);
        });

        auditService.logEvent(actorId, "MEMBER_ARCHIVED", "MEMBER", memberId,
                member.getDeletionReason());
        return MemberResponse.fromEntity(member);
    }

    /**
     * One-click restore. Financial history is already intact; the linked login
     * is re-enabled with its original username and a mandatory password reset.
     */
    @Transactional
    public MemberResponse restoreMember(Long memberId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        if (!Boolean.TRUE.equals(member.getIsDeleted())) {
            throw new IllegalStateException("Member is not archived");
        }

        member.setIsDeleted(false);
        member.setDeletedAt(null);
        member.setDeletionReason(null);
        member.setStatus(Member.MemberStatus.ACTIVE);
        memberRepository.save(member);

        userRepository.findByMemberId(memberId).ifPresent(user -> {
            String original = user.getArchivedUsername();
            if (original != null && (!userRepository.existsByUsername(original)
                    || original.equals(user.getUsername()))) {
                user.setUsername(original);
            }
            user.setArchivedUsername(null);
            user.setIsActive(true);
            user.setPasswordResetRequired(true);
            // The previous hash was randomized at archive time. Keep a fresh
            // random hash so the member must have an administrator set a new
            // password before the account can be used.
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            userRepository.save(user);
        });

        auditService.logEvent(actorId, "MEMBER_RESTORED", "MEMBER", memberId,
                "Archived member restored; password reset required");
        return MemberResponse.fromEntity(member);
    }

    private void requireNotArchived(Member member) {
        if (Boolean.TRUE.equals(member.getIsDeleted())) {
            throw new IllegalStateException("This member is archived. Restore the account before editing.");
        }
    }

    /**
     * Account closure checklist: no active loan balance and no live guarantee
     * for another member's active loan.
     */
    private void assertClosureAllowed(Member member) {
        List<Loan> activeLoans = loanRepository.findByMemberIdAndStatusIn(
                member.getMemberId(), ACTIVE_LOAN_STATUSES);
        BigDecimal outstanding = activeLoans.stream()
                .map(loan -> nullSafe(loan.getOutstandingPrincipal())
                        .add(nullSafe(loan.getOutstandingInterest())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "Cannot archive or close this member: active loan balance of UGX "
                            + outstanding.setScale(2).toPlainString() + " remains.");
        }

        List<LoanGuarantor> guarantees = loanGuarantorRepository.findActiveGuaranteesByMemberId(
                member.getMemberId(), ACTIVE_LOAN_STATUSES);
        if (!guarantees.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot archive or close this member: they guarantee "
                            + guarantees.size() + " active loan(s) for other members.");
        }
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}