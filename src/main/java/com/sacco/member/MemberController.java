package com.sacco.member;

import com.sacco.dashboard.dto.MonthlySavingsPoint;
import com.sacco.dashboard.DashboardService;
import com.sacco.member.dto.*;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberProfileService memberProfileService;
    private final PermissionService permissionService;
    private final DashboardService dashboardService;

    @PostMapping
    public ResponseEntity<MemberResponse> registerMember(
            @Valid @RequestBody MemberRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.registerMember(request, actorId));
    }

    @PostMapping("/with-documents")
    public ResponseEntity<MemberResponse> registerMemberWithDocuments(
            @Valid @RequestBody MemberRegistrationRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.registerMemberWithDocuments(request, actorId));
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> getAllMembers() {
        return ResponseEntity.ok(memberService.getAllMembers());
    }

    @GetMapping("/archived")
    public ResponseEntity<List<MemberResponse>> getArchivedMembers(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_RESTORE);
        return ResponseEntity.ok(memberService.getArchivedMembers());
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<MemberResponse> getMember(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(memberService.getMember(memberId));
    }

    @PostMapping("/{memberId}/documents")
    public ResponseEntity<IdentityDocumentResponse> addIdentityDocument(
            @PathVariable Long memberId, 
            @Valid @RequestBody IdentityDocumentRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.addIdentityDocument(memberId, request, actorId));
    }

    @GetMapping("/{memberId}/documents")
    public ResponseEntity<List<IdentityDocumentResponse>> getMemberDocuments(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(memberService.getMemberDocuments(memberId));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<MemberResponse> updateMember(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberUpdateRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_EDIT);
        return ResponseEntity.ok(memberService.updateMember(memberId, request, actorId));
    }

    @GetMapping("/{memberId}/user")
    public ResponseEntity<MemberUserResponse> getMemberUser(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        return ResponseEntity.ok(memberService.getMemberUser(memberId));
    }

    @PostMapping("/{memberId}/user")
    public ResponseEntity<MemberUserResponse> upsertMemberUser(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberCredentialsRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        return ResponseEntity.ok(memberService.upsertMemberUser(memberId, request, actorId));
    }

    @GetMapping("/{memberId}/profile")
    public ResponseEntity<MemberProfileResponse> getMemberProfile(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(memberProfileService.getProfile(memberId));
    }

    @GetMapping("/{memberId}/savings/monthly")
    public ResponseEntity<List<MonthlySavingsPoint>> getMemberMonthlySavings(
            @PathVariable Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String granularity,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(dashboardService.getMemberMonthlySavings(memberId, fromDate, toDate, granularity));
    }

    @PostMapping(value = "/{memberId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MemberResponse> uploadMemberPhoto(
            @PathVariable Long memberId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_MANAGE);
        return ResponseEntity.ok(memberProfileService.uploadPhoto(memberId, file, actorId));
    }

    @PostMapping(value = "/{memberId}/documents/{documentId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdentityDocumentResponse> uploadDocumentFile(
            @PathVariable Long memberId,
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_MANAGE);
        return ResponseEntity.ok(memberProfileService.uploadDocumentFile(memberId, documentId, file, actorId));
    }

    @PostMapping("/verify-document")
    public ResponseEntity<IdentityDocumentResponse> submitVerification(
            @Valid @RequestBody VerificationRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_EDIT);
        return ResponseEntity.ok(memberService.submitVerification(request, actorId));
    }

    @PostMapping("/{memberId}/reject")
    public ResponseEntity<MemberResponse> rejectMember(
            @PathVariable Long memberId, 
            @RequestParam String reason, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_EDIT);
        return ResponseEntity.ok(memberService.rejectMember(memberId, reason, actorId));
    }

    @PostMapping("/{memberId}/close")
    public ResponseEntity<MemberResponse> closeMember(
            @PathVariable Long memberId, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_ARCHIVE);
        return ResponseEntity.ok(memberService.closeMember(memberId, actorId));
    }

    @PostMapping("/{memberId}/archive")
    public ResponseEntity<MemberResponse> archiveMember(
            @PathVariable Long memberId,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_ARCHIVE);
        return ResponseEntity.ok(memberService.archiveMember(memberId, reason, actorId));
    }

    @PostMapping("/{memberId}/restore")
    public ResponseEntity<MemberResponse> restoreMember(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.MEMBER_RESTORE);
        return ResponseEntity.ok(memberService.restoreMember(memberId, actorId));
    }
}