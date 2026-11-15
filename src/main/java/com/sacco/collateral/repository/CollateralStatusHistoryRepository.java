package com.sacco.collateral.repository;

import com.sacco.collateral.entity.CollateralStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollateralStatusHistoryRepository extends JpaRepository<CollateralStatusHistory, Long> {

    List<CollateralStatusHistory> findByCollateralIdOrderByCreatedAtDesc(Long collateralId);
}
