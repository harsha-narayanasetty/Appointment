package com.clinic.appointment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * FOR UPDATE SKIP LOCKED: if several instances run, each publisher locks a different batch
     * instead of publishing the same rows twice. Must run inside a transaction.
     */
    @Query(value = """
            select * from outbox_events
            where status = 'PENDING'
            order by id
            limit 50
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch();

    long countByStatus(OutboxStatus status);
}
