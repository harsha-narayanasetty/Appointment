package com.clinic.appointment.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxStatusTest {

    @Test
    void outboxStatus_hasAllLifecycleStates() {
        // given / when
        OutboxStatus[] values = OutboxStatus.values();

        // then
        assertThat(values).containsExactly(OutboxStatus.PENDING, OutboxStatus.PUBLISHED, OutboxStatus.FAILED);
    }
}
