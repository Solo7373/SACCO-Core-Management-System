package com.sacco.inventory.repository;

import com.sacco.inventory.entity.ProductImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImportRepository extends JpaRepository<ProductImport, Long> {
    List<ProductImport> findAllByOrderByCreatedAtDesc();
    List<ProductImport> findByProductIdOrderByCreatedAtDesc(Long productId);
}
