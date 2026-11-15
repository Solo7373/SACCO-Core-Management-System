package com.sacco.inventory.repository;

import com.sacco.inventory.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();
    List<Product> findByIsActiveTrueOrderByNameAsc();
    boolean existsByCode(String code);
}
