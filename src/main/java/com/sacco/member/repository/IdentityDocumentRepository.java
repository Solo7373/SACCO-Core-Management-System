package com.sacco.member.repository;

import com.sacco.member.entity.IdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdentityDocumentRepository extends JpaRepository<IdentityDocument, Long> {
    List<IdentityDocument> findByMemberId(Long memberId);
    List<IdentityDocument> findByStatus(IdentityDocument.VerificationStatus status);
    boolean existsByDocumentTypeAndDocumentNumber(IdentityDocument.DocumentType type, String number);
}
