package com.clinic.appointment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxConfigTest {

    @Test
    void appointmentBookedTopic_isCreatedWithConfiguredName() {
        // given
        OutboxConfig config = new OutboxConfig();

        // when
        var topic = config.appointmentBookedTopic("appointment.booked");

        // then
        assertThat(topic.name()).isEqualTo("appointment.booked");
        assertThat(topic.numPartitions()).isEqualTo(3);
    }
}
