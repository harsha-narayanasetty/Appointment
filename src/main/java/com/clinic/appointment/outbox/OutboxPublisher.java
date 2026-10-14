package com.clinic.appointment.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and sends PENDING rows to Kafka, oldest first.
 * Delivery is AT LEAST ONCE: if the app dies after Kafka accepts a message but before the row is
 * marked PUBLISHED, the row is sent again. That is safe because the consumer de-duplicates by eventId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clinicClock;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.lockNextBatch();
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now(clinicClock));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Kafka is probably down. Record the failure and STOP the batch so order is kept;
                // the next poll tries again. Nothing is lost: the row stays PENDING.
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.toString()));
                log.warn("Kafka publish failed for outbox event {} (attempt {}): {}",
                        event.getEventId(), event.getAttempts(), e.toString());
                break;
            }
        }
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
