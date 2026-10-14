package com.clinic.appointment.outbox;

import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.model.Doctor;
import com.clinic.appointment.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T08:00:00Z");

    @Mock OutboxRepository outboxRepository;
    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(outboxRepository, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "topic", "appointment.booked");
    }

    @Test
    void recordAppointmentBooked_savesPendingEventWithAppointmentDetails() {
        // given
        Appointment appointment = Appointment.builder().id(7L)
                .patient(Patient.builder().id(101L).build())
                .doctor(Doctor.builder().id(201L).build())
                .startTime(LocalDateTime.of(2026, 11, 2, 10, 0))
                .status(AppointmentStatus.BOOKED).build();

        // when
        service.recordAppointmentBooked(appointment);

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getTopic()).isEqualTo("appointment.booked");
        assertThat(saved.getEventKey()).isEqualTo("7");
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getPayload())
                .contains("\"eventType\":\"AppointmentBooked\"")
                .contains("\"eventId\":\"" + saved.getEventId() + "\"")
                .contains("\"appointmentId\":7")
                .contains("\"patientId\":101")
                .contains("\"doctorId\":201")
                .contains("2026-11-02T10:00");
    }

    @Test
    void recordAppointmentBooked_mustRunInsideTheBookingTransaction() throws Exception {
        // given
        var method = OutboxService.class.getMethod("recordAppointmentBooked", Appointment.class);

        // when
        Transactional transactional = method.getAnnotation(Transactional.class);

        // then
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
