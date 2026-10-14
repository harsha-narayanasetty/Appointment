package com.clinic.appointment.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentBookedEventTest {

    @Test
    void json_hasTheFieldNamesTheNotificationServiceExpects() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        AppointmentBookedEvent event = new AppointmentBookedEvent(eventId, "AppointmentBooked",
                Instant.parse("2026-11-01T08:00:00Z"), 7L, 101L, 201L, LocalDateTime.of(2026, 11, 2, 10, 0));

        // when
        JsonNode json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(event));

        // then
        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "eventId", "eventType", "occurredAt", "appointmentId", "patientId", "doctorId", "startTime");
        assertThat(json.get("eventId").asString()).isEqualTo(eventId.toString());
        assertThat(json.get("eventType").asString()).isEqualTo("AppointmentBooked");
        assertThat(json.get("startTime").asString()).isEqualTo("2026-11-02T10:00:00");
    }
}
