package com.sacco.finance.repository;

import com.sacco.finance.entity.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeeConfigRepository extends JpaRepository<FeeConfig, Long> {
    List<FeeConfig> findAllByOrderByTransactionTypeAscChannelAsc();
    List<FeeConfig> findByIsActiveTrueOrderByTransactionTypeAscChannelAsc();
    Optional<FeeConfig> findFirstByTransactionTypeAndChannelAndIsActiveTrue(String transactionType, String channel);
}
