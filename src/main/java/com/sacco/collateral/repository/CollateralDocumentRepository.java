package com.sacco.collateral.repository;

import com.sacco.collateral.entity.CollateralDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollateralDocumentRepository extends JpaRepository<CollateralDocument, Long> {

    List<CollateralDocument> findByCollateralIdOrderByUploadedAtDesc(Long collateralId);

    long countByCollateralId(Long collateralId);
}
