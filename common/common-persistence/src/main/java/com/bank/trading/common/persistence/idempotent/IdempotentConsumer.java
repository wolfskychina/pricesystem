package com.bank.trading.common.persistence.idempotent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class IdempotentConsumer {

    private final ProcessedEventMapper processedEventMapper;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdempotentConsumer.class);

    public IdempotentConsumer(ProcessedEventMapper processedEventMapper) {
        this.processedEventMapper = processedEventMapper;
    }

    public IdempotentConsumer(ProcessedEventMapper processedEventMapper) {
        this.processedEventMapper = processedEventMapper;
    }

    public IdempotentConsumer(ProcessedEventMapper processedEventMapper) {
        this.processedEventMapper = processedEventMapper;
    }

    @Transactional
    public <T> T consume(String eventId, Supplier<T> action) {
        int inserted = processedEventMapper.insert(eventId, LocalDateTime.now());
        if (inserted == 0) {
            log.warn("Duplicate event skipped: {}", eventId);
            return null;
        }
        return action.get();
    }

    @Transactional
    public void consume(String eventId, Runnable action) {
        int inserted = processedEventMapper.insert(eventId, LocalDateTime.now());
        if (inserted == 0) {
            log.warn("Duplicate event skipped: {}", eventId);
            return;
        }
        action.run();
    }

    public boolean isProcessed(String eventId) {
        return processedEventMapper.exists(eventId) > 0;
    }
}
