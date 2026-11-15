package com.sacco.finance.repository;

import com.sacco.finance.entity.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
    List<TaxRate> findAllByOrderByCategoryAscNameAsc();
    List<TaxRate> findByIsActiveTrueOrderByCategoryAscNameAsc();
    List<TaxRate> findByCategoryOrderByNameAsc(String category);
    Optional<TaxRate> findFirstByCategoryAndIsActiveTrueOrderByRatePercentDesc(String category);
    Optional<TaxRate> findFirstByCode(String code);
    boolean existsByCode(String code);
}
