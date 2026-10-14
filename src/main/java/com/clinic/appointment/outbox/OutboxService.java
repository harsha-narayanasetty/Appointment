package com.clinic.appointment.outbox;

import com.clinic.appointment.event.AppointmentBookedEvent;
import com.clinic.appointment.model.Appointment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;   // Jackson 3 (Spring Boot 4)
    private final Clock clinicClock;

    @Value("${outbox.topic:appointment.booked}")
    private String topic;

    /**
     * MANDATORY: this must run inside the booking transaction. Calling it without one fails loudly,
     * which protects the guarantee "appointment saved <=> event saved".
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAppointmentBooked(Appointment appointment) {
        AppointmentBookedEvent event = new AppointmentBookedEvent(
                UUID.randomUUID(),
                "AppointmentBooked",
                Instant.now(clinicClock),
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctor().getId(),
                appointment.getStartTime());

        outboxRepository.save(OutboxEvent.builder()
                .eventId(event.eventId())
                .topic(topic)
                .eventKey(String.valueOf(appointment.getId()))
                .payload(toJson(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now(clinicClock))
                .attempts(0)
                .build());
    }

    private String toJson(AppointmentBookedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
