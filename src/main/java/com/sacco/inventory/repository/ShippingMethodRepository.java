package com.sacco.inventory.repository;

import com.sacco.inventory.entity.ShippingMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {
    List<ShippingMethod> findByIsActiveTrueOrderByNameAsc();
    List<ShippingMethod> findAllByOrderByNameAsc();
}
