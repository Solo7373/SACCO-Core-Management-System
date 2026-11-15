package com.sacco.communication;

import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.repository.SmsMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Small transactional boundary around the outbox claim query. Keeping this in
 * its own bean guarantees the SELECT ... FOR UPDATE SKIP LOCKED actually runs
 * inside a transaction before the dispatcher starts network calls.
 */
@Service
@RequiredArgsConstructor
public class SmsOutboxClaimer {

    private final SmsMessageRepository repository;

    @Transactional
    public List<Long> claim(int limit) {
        List<SmsMessage> batch = repository.claimBatch(LocalDateTime.now(), limit);
        for (SmsMessage message : batch) {
            message.setStatus(SmsMessage.SmsStatus.SENDING);
            message.setLastAttemptAt(LocalDateTime.now());
        }
        repository.saveAll(batch);
        return batch.stream().map(SmsMessage::getMessageId).toList();
    }

    @Transactional
    public int resetStale(long staleSeconds) {
        return repository.resetStaleSending(
                SmsMessage.SmsStatus.SENDING,
                SmsMessage.SmsStatus.QUEUED,
                LocalDateTime.now(),
                LocalDateTime.now().minusSeconds(staleSeconds));
    }
}
