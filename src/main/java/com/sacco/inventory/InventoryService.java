package com.sacco.inventory;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.inventory.dto.*;
import com.sacco.inventory.entity.Product;
import com.sacco.inventory.entity.ProductImport;
import com.sacco.inventory.entity.ShippingMethod;
import com.sacco.inventory.repository.ProductImportRepository;
import com.sacco.inventory.repository.ProductRepository;
import com.sacco.inventory.repository.ShippingMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final ProductRepository productRepository;
    private final ProductImportRepository importRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_VIEW);
        return productRepository.findAllByOrderByNameAsc().stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsByCode(code)) {
            throw new IllegalStateException("A product with code " + code + " already exists");
        }
        Product product = Product.builder()
                .code(code)
                .name(request.getName().trim())
                .category(request.getCategory())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .stockQuantity(request.getStockQuantity() == null ? 0 : request.getStockQuantity())
                .reorderLevel(request.getReorderLevel() == null ? 5 : request.getReorderLevel())
                .isActive(request.getIsActive() == null ? true : request.getIsActive())
                .description(request.getDescription())
                .build();
        Product saved = productRepository.save(product);
        auditService.logEvent(actorId, "PRODUCT_CREATED", "PRODUCT", saved.getProductId(),
                saved.getCode() + " " + saved.getName());
        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        Product product = findProduct(productId);
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (!code.equals(product.getCode()) && productRepository.existsByCode(code)) {
            throw new IllegalStateException("A product with code " + code + " already exists");
        }
        product.setCode(code);
        product.setName(request.getName().trim());
        product.setCategory(request.getCategory());
        product.setUnitPrice(request.getUnitPrice());
        product.setCostPrice(request.getCostPrice());
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getReorderLevel() != null) {
            product.setReorderLevel(request.getReorderLevel());
        }
        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
        }
        product.setDescription(request.getDescription());
        Product saved = productRepository.save(product);
        auditService.logEvent(actorId, "PRODUCT_UPDATED", "PRODUCT", productId,
                saved.getCode() + " " + saved.getName());
        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    public ProductResponse toggleProduct(Long productId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        Product product = findProduct(productId);
        product.setIsActive(!Boolean.TRUE.equals(product.getIsActive()));
        Product saved = productRepository.save(product);
        auditService.logEvent(actorId, "PRODUCT_TOGGLED", "PRODUCT", productId,
                saved.getCode() + " active=" + saved.getIsActive());
        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    public ProductResponse adjustStock(Long productId, int quantityDelta, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        Product product = findProduct(productId);
        int newStock = product.getStockQuantity() + quantityDelta;
        if (newStock < 0) {
            throw new IllegalStateException("Stock adjustment would make inventory negative");
        }
        product.setStockQuantity(newStock);
        Product saved = productRepository.save(product);
        auditService.logEvent(actorId, "STOCK_ADJUSTED", "PRODUCT", productId,
                "Delta: " + quantityDelta + ", New stock: " + newStock
                        + (reason == null ? "" : ", Reason: " + reason));
        return ProductResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> getShippingMethods(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_VIEW);
        return shippingMethodRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(ShippingMethodResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImportResponse> getImports(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_VIEW);
        return importRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toImportResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse previewImport(ImportRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_VIEW);
        return calculate(request);
    }

    @Transactional
    public ImportResponse createImport(ImportRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        Product product = findProduct(request.getProductId());
        ImportPreviewResponse preview = calculate(request);

        ProductImport item = ProductImport.builder()
                .productId(product.getProductId())
                .supplierName(request.getSupplierName().trim())
                .supplierCountry(trimToNull(request.getSupplierCountry()))
                .quantity(request.getQuantity())
                .unitCost(request.getUnitCost())
                .shippingMethod(request.getShippingMethod().trim().toUpperCase(Locale.ROOT))
                .shippingFee(preview.getShippingFee())
                .customsDutyRate(preview.getCustomsDutyRate())
                .customsDutyAmount(preview.getCustomsDutyAmount())
                .vatRate(preview.getVatRate())
                .vatAmount(preview.getVatAmount())
                .otherTaxes(preview.getOtherTaxes())
                .totalLandedCost(preview.getTotalLandedCost())
                .status(ProductImport.ImportStatus.ORDERED)
                .orderDate(request.getOrderDate() == null ? LocalDate.now() : request.getOrderDate())
                .expectedDate(request.getExpectedDate())
                .notes(trimToNull(request.getNotes()))
                .createdBy(actorId)
                .build();
        ProductImport saved = importRepository.save(item);
        auditService.logEvent(actorId, "IMPORT_ORDER_CREATED", "PRODUCT_IMPORT", saved.getImportId(),
                product.getCode() + " x" + saved.getQuantity() + " from " + saved.getSupplierName()
                        + ", Landed: " + saved.getTotalLandedCost());
        return toImportResponse(saved);
    }

    @Transactional
    public ImportResponse markInTransit(Long importId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        ProductImport item = findImport(importId);
        if (item.getStatus() != ProductImport.ImportStatus.ORDERED) {
            throw new IllegalStateException("Only ordered imports can be marked in transit");
        }
        item.setStatus(ProductImport.ImportStatus.IN_TRANSIT);
        ProductImport saved = importRepository.save(item);
        auditService.logEvent(actorId, "IMPORT_IN_TRANSIT", "PRODUCT_IMPORT", importId, null);
        return toImportResponse(saved);
    }

    @Transactional
    public ImportResponse receiveImport(Long importId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        ProductImport item = findImport(importId);
        if (item.getStatus() == ProductImport.ImportStatus.RECEIVED) {
            throw new IllegalStateException("This import has already been received");
        }
        if (item.getStatus() == ProductImport.ImportStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled imports cannot be received");
        }
        Product product = findProduct(item.getProductId());
        product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        BigDecimal unitLanded = item.getTotalLandedCost()
                .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
        product.setCostPrice(unitLanded);
        productRepository.save(product);

        item.setStatus(ProductImport.ImportStatus.RECEIVED);
        item.setReceivedDate(LocalDate.now());
        ProductImport saved = importRepository.save(item);
        auditService.logEvent(actorId, "IMPORT_RECEIVED", "PRODUCT_IMPORT", importId,
                product.getCode() + " x" + item.getQuantity() + ", Unit landed cost: " + unitLanded);
        return toImportResponse(saved);
    }

    @Transactional
    public ImportResponse cancelImport(Long importId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.INVENTORY_MANAGE);
        ProductImport item = findImport(importId);
        if (item.getStatus() == ProductImport.ImportStatus.RECEIVED) {
            throw new IllegalStateException("Received imports cannot be cancelled");
        }
        item.setStatus(ProductImport.ImportStatus.CANCELLED);
        ProductImport saved = importRepository.save(item);
        auditService.logEvent(actorId, "IMPORT_CANCELLED", "PRODUCT_IMPORT", importId, null);
        return toImportResponse(saved);
    }

    private ImportPreviewResponse calculate(ImportRequest request) {
        BigDecimal baseCost = request.getUnitCost()
                .multiply(BigDecimal.valueOf(request.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingFee = request.getShippingFee();
        if (shippingFee == null) {
            shippingFee = shippingMethodRepository.findAllByOrderByNameAsc().stream()
                    .filter(method -> method.getCode().equalsIgnoreCase(request.getShippingMethod()))
                    .map(ShippingMethod::getBaseFee)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
        }
        shippingFee = shippingFee.setScale(2, RoundingMode.HALF_UP);

        BigDecimal customsRate = request.getCustomsDutyRate() == null
                ? BigDecimal.ZERO : request.getCustomsDutyRate();
        BigDecimal customsDuty = baseCost.multiply(customsRate)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal vatRate = request.getVatRate() == null ? BigDecimal.ZERO : request.getVatRate();
        BigDecimal vatAmount = baseCost.add(shippingFee).add(customsDuty)
                .multiply(vatRate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal otherTaxes = request.getOtherTaxes() == null
                ? BigDecimal.ZERO : request.getOtherTaxes().setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = baseCost.add(shippingFee).add(customsDuty)
                .add(vatAmount).add(otherTaxes).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitLanded = total.divide(BigDecimal.valueOf(request.getQuantity()), 2, RoundingMode.HALF_UP);

        return ImportPreviewResponse.builder()
                .baseCost(baseCost)
                .shippingFee(shippingFee)
                .customsDutyRate(customsRate)
                .customsDutyAmount(customsDuty)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .otherTaxes(otherTaxes)
                .totalLandedCost(total)
                .unitLandedCost(unitLanded)
                .build();
    }

    private ImportResponse toImportResponse(ProductImport item) {
        ImportResponse response = ImportResponse.fromEntity(item);
        productRepository.findById(item.getProductId()).ifPresent(product -> {
            response.setProductName(product.getName());
            response.setProductCode(product.getCode());
        });
        return response;
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    }

    private ProductImport findImport(Long importId) {
        return importRepository.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import order not found: " + importId));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
