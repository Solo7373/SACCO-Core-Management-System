package com.sacco.collateral.repository;

import com.sacco.collateral.entity.CollateralAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CollateralRepository extends JpaRepository<CollateralAsset, Long> {

    List<CollateralAsset> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<CollateralAsset> findByLoanAccountId(Long loanAccountId);

    long countByStatus(CollateralAsset.CollateralStatus status);

    long countByStatusNot(CollateralAsset.CollateralStatus status);

    boolean existsByAssetTypeAndTitleNumber(CollateralAsset.AssetType assetType, String titleNumber);

    boolean existsByAssetTypeAndTitleNumberAndCollateralIdNot(
            CollateralAsset.AssetType assetType,
            String titleNumber,
            Long collateralId);

    @Query("SELECT SUM(c.estimatedValue) FROM CollateralAsset c WHERE c.status = :status")
    BigDecimal sumEstimatedValueByStatus(@Param("status") CollateralAsset.CollateralStatus status);

    @Query("SELECT SUM(c.estimatedValue) FROM CollateralAsset c WHERE c.status <> :status")
    BigDecimal sumEstimatedValueExcludingStatus(@Param("status") CollateralAsset.CollateralStatus status);
}
