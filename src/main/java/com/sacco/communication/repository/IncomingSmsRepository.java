package com.sacco.communication.repository;

import com.sacco.communication.entity.IncomingSms;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IncomingSmsRepository extends JpaRepository<IncomingSms, Long> {

    Optional<IncomingSms> findByProviderMessageId(String providerMessageId);

    Page<IncomingSms> findAllByOrderByIncomingIdDesc(Pageable pageable);
}
