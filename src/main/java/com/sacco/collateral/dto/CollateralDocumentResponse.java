package com.sacco.collateral.dto;

import com.sacco.collateral.entity.CollateralDocument;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CollateralDocumentResponse {
    private Long documentId;
    private Long collateralId;
    private CollateralDocument.DocumentType documentType;
    private String documentName;
    private String filePath;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;

    public static CollateralDocumentResponse fromEntity(CollateralDocument document) {
        return CollateralDocumentResponse.builder()
                .documentId(document.getDocumentId())
                .collateralId(document.getCollateralId())
                .documentType(document.getDocumentType())
                .documentName(document.getDocumentName())
                .filePath(document.getFilePath())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .uploadedBy(document.getUploadedBy())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
