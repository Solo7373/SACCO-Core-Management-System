package com.sacco.communication;

import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Small transactional boundary around the outbox claim query. Keeping this in
 * its own bean guarantees SELECT ... FOR UPDATE SKIP LOCKED runs inside a
 * transaction before the dispatcher starts network calls.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppOutboxClaimer {

    private final WhatsAppMessageRepository repository;

    @Transactional
    public List<Long> claim(int limit) {
        List<WhatsAppMessage> batch = repository.claimBatch(LocalDateTime.now(), limit);
        LocalDateTime now = LocalDateTime.now();
        for (WhatsAppMessage message : batch) {
            message.setStatus(WhatsAppMessage.WhatsAppStatus.SENDING);
            message.setLastAttemptAt(now);
        }
        repository.saveAll(batch);
        return batch.stream().map(WhatsAppMessage::getMessageId).toList();
    }

    @Transactional
    public int resetStale(long staleSeconds) {
        return repository.resetStaleSending(
                WhatsAppMessage.WhatsAppStatus.SENDING,
                WhatsAppMessage.WhatsAppStatus.QUEUED,
                LocalDateTime.now(),
                LocalDateTime.now().minusSeconds(staleSeconds));
    }
}
