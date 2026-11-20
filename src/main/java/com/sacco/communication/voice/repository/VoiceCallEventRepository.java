package com.sacco.communication.voice.repository;

import com.sacco.communication.voice.entity.VoiceCallEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoiceCallEventRepository extends JpaRepository<VoiceCallEvent, Long> {
    List<VoiceCallEvent> findAllByCallIdOrderByEventIdAsc(Long callId);
}
