package com.sacco.member.dto;

import com.sacco.member.entity.IdentityDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class IdentityDocumentRequest {
    @NotNull(message = "Document type is required")
    private IdentityDocument.DocumentType documentType;

    @NotBlank(message = "Document number is required")
    private String documentNumber;

    private String issuer;
    private LocalDate issueDate;
    private LocalDate expiryDate;
}
