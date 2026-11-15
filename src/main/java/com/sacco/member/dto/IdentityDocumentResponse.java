package com.sacco.member.dto;

import com.sacco.member.entity.IdentityDocument;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class IdentityDocumentResponse {
    private Long documentId;
    private Long memberId;
    private IdentityDocument.DocumentType documentType;
    private String documentNumber;
    private String issuer;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String filePath;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private IdentityDocument.VerificationStatus status;
    private LocalDateTime createdAt;

    public static IdentityDocumentResponse fromEntity(IdentityDocument doc) {
        return IdentityDocumentResponse.builder()
                .documentId(doc.getDocumentId())
                .memberId(doc.getMemberId())
                .documentType(doc.getDocumentType())
                .documentNumber(doc.getDocumentNumber())
                .issuer(doc.getIssuer())
                .issueDate(doc.getIssueDate())
                .expiryDate(doc.getExpiryDate())
                .filePath(doc.getFilePath())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
