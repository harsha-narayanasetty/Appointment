package com.clinic.appointment.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The contract between the two services. notification-service has its own copy of this record:
 * the services share NO code, only this JSON shape on the Kafka topic.
 */
public record AppointmentBookedEvent(
        UUID eventId,            // unique per event; the consumer de-duplicates on this
        String eventType,        // "AppointmentBooked"
        Instant occurredAt,
        Long appointmentId,
        Long patientId,
        Long doctorId,
        LocalDateTime startTime  // clinic-local time
) {
}
