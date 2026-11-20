package com.sacco.communication.voice.repository;

import com.sacco.communication.voice.entity.VoiceCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoiceCallRepository extends JpaRepository<VoiceCall, Long> {
    Page<VoiceCall> findAllByOrderByCallIdDesc(org.springframework.data.domain.Pageable pageable);

    Page<VoiceCall> findByStatusOrderByCallIdDesc(VoiceCall.VoiceCallStatus status,
                                                    org.springframework.data.domain.Pageable pageable);
}
