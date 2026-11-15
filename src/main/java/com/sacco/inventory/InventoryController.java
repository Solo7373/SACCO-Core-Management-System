package com.sacco.inventory;

import com.sacco.inventory.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.getProducts(actorId));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createProduct(request, actorId));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.updateProduct(productId, request, actorId));
    }

    @PostMapping("/products/{productId}/toggle")
    public ResponseEntity<ProductResponse> toggleProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.toggleProduct(productId, actorId));
    }

    @PostMapping("/products/{productId}/stock")
    public ResponseEntity<ProductResponse> adjustStock(
            @PathVariable Long productId,
            @RequestParam int delta,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.adjustStock(productId, delta, reason, actorId));
    }

    @GetMapping("/shipping-methods")
    public ResponseEntity<List<ShippingMethodResponse>> getShippingMethods(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.getShippingMethods(actorId));
    }

    @GetMapping("/imports")
    public ResponseEntity<List<ImportResponse>> getImports(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.getImports(actorId));
    }

    @PostMapping("/imports/preview")
    public ResponseEntity<ImportPreviewResponse> previewImport(
            @Valid @RequestBody ImportRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.previewImport(request, actorId));
    }

    @PostMapping("/imports")
    public ResponseEntity<ImportResponse> createImport(
            @Valid @RequestBody ImportRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createImport(request, actorId));
    }

    @PostMapping("/imports/{importId}/in-transit")
    public ResponseEntity<ImportResponse> markInTransit(
            @PathVariable Long importId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.markInTransit(importId, actorId));
    }

    @PostMapping("/imports/{importId}/receive")
    public ResponseEntity<ImportResponse> receiveImport(
            @PathVariable Long importId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.receiveImport(importId, actorId));
    }

    @PostMapping("/imports/{importId}/cancel")
    public ResponseEntity<ImportResponse> cancelImport(
            @PathVariable Long importId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(inventoryService.cancelImport(importId, actorId));
    }
}
