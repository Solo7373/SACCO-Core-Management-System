package com.sacco.share.repository;

import com.sacco.share.entity.ShareTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareTransactionRepository extends JpaRepository<ShareTransaction, Long> {
    List<ShareTransaction> findByMemberId(Long memberId);
    List<ShareTransaction> findByMemberIdAndStatus(Long memberId, ShareTransaction.ShareTransactionStatus status);
    List<ShareTransaction> findByStatus(ShareTransaction.ShareTransactionStatus status);
}
