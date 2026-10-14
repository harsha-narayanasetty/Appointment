package com.clinic.appointment.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-01T08:00:00Z");

    @Mock OutboxRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OutboxEvent pending(String key) {
        return OutboxEvent.builder().eventId(UUID.randomUUID()).topic("appointment.booked").eventKey(key)
                .payload("{}").status(OutboxStatus.PENDING).attempts(0).build();
    }

    @Test
    void publishPending_marksEventsPublishedWhenKafkaAccepts() {
        // given
        OutboxEvent first = pending("1");
        OutboxEvent second = pending("2");
        when(outboxRepository.lockNextBatch()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(CompletableFuture.completedFuture(null));

        // when
        publisher.publishPending();

        // then
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void publishPending_nothingPending_doesNotCallKafka() {
        // given
        when(outboxRepository.lockNextBatch()).thenReturn(List.of());

        // when
        publisher.publishPending();

        // then
        verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void publishPending_kafkaDown_keepsEventPendingRecordsErrorAndStopsTheBatch() {
        // given
        OutboxEvent first = pending("1");
        OutboxEvent second = pending("2");
        when(outboxRepository.lockNextBatch()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send("appointment.booked", "1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        // when
        publisher.publishPending();

        // then
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker down");
        assertThat(second.getAttempts()).as("order is kept: later events are not attempted").isZero();
        verify(kafkaTemplate, never()).send("appointment.booked", "2", "{}");
    }

    @Test
    void publishPending_veryLongError_isTruncatedTo500Characters() {
        // given
        OutboxEvent event = pending("1");
        when(outboxRepository.lockNextBatch()).thenReturn(List.of(event));
        when(kafkaTemplate.send("appointment.booked", "1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("x".repeat(2000))));

        // when
        publisher.publishPending();

        // then
        assertThat(event.getLastError()).hasSize(500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_interrupted_stopsWithoutRecordingAFailure() throws Exception {
        // given
        OutboxEvent event = pending("1");
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(outboxRepository.lockNextBatch()).thenReturn(List.of(event));
        when(kafkaTemplate.send("appointment.booked", "1", "{}")).thenReturn(future);

        // when
        publisher.publishPending();

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(Thread.interrupted()).as("interrupt flag is restored").isTrue();
    }
}
