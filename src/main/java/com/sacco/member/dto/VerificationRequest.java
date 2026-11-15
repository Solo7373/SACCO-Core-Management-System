package com.sacco.member.dto;

import com.sacco.member.entity.IdentityDocument;
import com.sacco.member.entity.VerificationRecord;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerificationRequest {
    @NotNull(message = "Document ID is required")
    private Long documentId;

    @NotNull(message = "Verification method is required")
    private VerificationRecord.VerificationMethod method;

    @NotNull(message = "Outcome is required")
    private IdentityDocument.VerificationStatus outcome;

    private Long reviewerId;
    private String evidenceReference;
}
