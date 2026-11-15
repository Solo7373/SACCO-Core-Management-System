package com.sacco.share.repository;

import com.sacco.share.entity.ShareProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareProductRepository extends JpaRepository<ShareProduct, Long> {
    List<ShareProduct> findByStatus(String status);
}
