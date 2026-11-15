package com.sacco.member;

import com.sacco.audit.AuditService;
import com.sacco.member.dto.MemberRequest;
import com.sacco.member.dto.MemberResponse;
import com.sacco.member.entity.IdentityDocument;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.IdentityDocumentRepository;
import com.sacco.member.repository.MemberRepository;
import com.sacco.member.repository.VerificationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private IdentityDocumentRepository documentRepository;
    @Mock private VerificationRecordRepository verificationRecordRepository;
    @Mock private com.sacco.auth.repository.UserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks private MemberService memberService;

    private MemberRequest validRequest;
    private Member savedMember;

    @BeforeEach
    void setUp() {
        validRequest = new MemberRequest();
        validRequest.setFirstName("Jane");
        validRequest.setLastName("Doe");
        validRequest.setPhone("+256712345678");
        validRequest.setEmail("jane@example.com");
        validRequest.setAddress("Kampala, Uganda");

        savedMember = Member.builder()
                .memberId(1L)
                .membershipNo("SCM-20261016-ABCD")
                .firstName("Jane")
                .lastName("Doe")
                .phone("+256712345678")
                .email("jane@example.com")
                .status(Member.MemberStatus.PENDING)
                .build();
    }

    @Test
    void registerMember_shouldCreateMemberWithPendingStatus() {
        when(memberRepository.save(any(Member.class))).thenReturn(savedMember);

        MemberResponse response = memberService.registerMember(validRequest, 1L);

        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getMembershipNo()).startsWith("SCM-");
        assertThat(response.getStatus()).isEqualTo(Member.MemberStatus.PENDING);
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void addIdentityDocument_shouldRejectDuplicateDocument() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(savedMember));
        when(documentRepository.existsByDocumentTypeAndDocumentNumber(any(), any())).thenReturn(true);

        var request = new com.sacco.member.dto.IdentityDocumentRequest();
        request.setDocumentType(IdentityDocument.DocumentType.PASSPORT);
        request.setDocumentNumber("P12345678");

        assertThatThrownBy(() -> memberService.addIdentityDocument(1L, request, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }


    @Test
    void rejectMember_shouldSetStatusAndReason() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(savedMember));
        when(memberRepository.save(any(Member.class))).thenReturn(savedMember);

        MemberResponse response = memberService.rejectMember(1L, "Incomplete documents", 1L);

        assertThat(response.getStatus()).isEqualTo(Member.MemberStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Incomplete documents");
    }
}
