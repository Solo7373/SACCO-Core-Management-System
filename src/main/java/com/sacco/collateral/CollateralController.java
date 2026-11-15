package com.sacco.collateral;

import com.sacco.collateral.dto.*;
import com.sacco.collateral.entity.CollateralDocument;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/collateral")
@RequiredArgsConstructor
public class CollateralController {

    private final CollateralService collateralService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<CollateralResponse>> getAllCollateral() {
        return ResponseEntity.ok(collateralService.getAll());
    }

    @GetMapping("/{collateralId}")
    public ResponseEntity<CollateralResponse> getCollateral(@PathVariable Long collateralId) {
        return ResponseEntity.ok(collateralService.getById(collateralId));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<CollateralResponse>> getCollateralByMember(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(collateralService.getByMember(memberId));
    }

    @PostMapping
    public ResponseEntity<CollateralResponse> registerCollateral(
            @Valid @RequestBody CollateralRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COLLATERAL_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collateralService.create(request, actorId));
    }

    @PutMapping("/{collateralId}")
    public ResponseEntity<CollateralResponse> updateCollateral(
            @PathVariable Long collateralId,
            @Valid @RequestBody CollateralRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COLLATERAL_MANAGE);
        return ResponseEntity.ok(collateralService.update(collateralId, request, actorId));
    }

    @PostMapping("/{collateralId}/release")
    public ResponseEntity<CollateralResponse> releaseCollateral(
            @PathVariable Long collateralId,
            @Valid @RequestBody CollateralReleaseRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COLLATERAL_MANAGE);
        return ResponseEntity.ok(collateralService.release(collateralId, request, actorId));
    }

    @PostMapping("/{collateralId}/default")
    public ResponseEntity<CollateralResponse> markUnderDefault(
            @PathVariable Long collateralId,
            @Valid @RequestBody CollateralDefaultRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COLLATERAL_MANAGE);
        return ResponseEntity.ok(collateralService.markUnderDefault(collateralId, request.getReason(), actorId));
    }

    @PostMapping(value = "/{collateralId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CollateralDocumentResponse> uploadDocument(
            @PathVariable Long collateralId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") CollateralDocument.DocumentType documentType,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COLLATERAL_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collateralService.uploadDocument(collateralId, file, documentType, documentName, actorId));
    }

    @DeleteMapping("/{collateralId}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long collateralId,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COLLATERAL_MANAGE);
        collateralService.deleteDocument(collateralId, documentId, actorId);
        return ResponseEntity.noContent().build();
    }
}
