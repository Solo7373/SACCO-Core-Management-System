package com.sacco.ledger.repository;

import com.sacco.ledger.entity.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, String> {
    List<ChartOfAccounts> findByCategory(ChartOfAccounts.AccountCategory category);
    List<ChartOfAccounts> findByStatus(String status);
}
