package com.sacco.account.repository;

import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.entity.AccountHolderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountHolderRepository extends JpaRepository<AccountHolder, AccountHolderId> {
    List<AccountHolder> findByMemberId(Long memberId);
    List<AccountHolder> findByAccountId(Long accountId);
}
