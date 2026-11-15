package com.sacco.account.repository;

import com.sacco.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByStatus(Account.AccountStatus status);
    List<Account> findByAccountType(Account.AccountType accountType);
    boolean existsByAccountNumber(String accountNumber);
}
