package com.clinic.appointment.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void appointment_lastsThirtyMinutes() {
        // given / when / then
        assertThat(Appointment.DURATION_MINUTES).isEqualTo(30);
    }

    @Test
    void appointment_builderKeepsAllFields() {
        // given
        Patient patient = Patient.builder().id(1L).build();
        Doctor doctor = Doctor.builder().id(201L).build();
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);

        // when
        Appointment appointment = Appointment.builder().id(5L).patient(patient).doctor(doctor)
                .startTime(start).status(AppointmentStatus.BOOKED).build();

        // then
        assertThat(appointment.getId()).isEqualTo(5L);
        assertThat(appointment.getPatient()).isSameAs(patient);
        assertThat(appointment.getDoctor()).isSameAs(doctor);
        assertThat(appointment.getStartTime()).isEqualTo(start);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void patientAndDoctor_builderKeepsAllFields() {
        // given / when
        Patient patient = Patient.builder().id(1L).firstName("Asha").lastName("Menon")
                .dateOfBirth(LocalDate.of(1990, 5, 17)).email("asha@example.com").phone("9876543210").build();
        Doctor doctor = Doctor.builder().id(201L).name("Dr. Asha Rao").specialization("General Medicine").build();

        // then
        assertThat(patient.getLastName()).isEqualTo("Menon");
        assertThat(patient.getPhone()).isEqualTo("9876543210");
        assertThat(doctor.getSpecialization()).isEqualTo("General Medicine");
    }

    @Test
    void enums_containTheExpectedValues() {
        // given / when / then
        assertThat(AppointmentStatus.values()).containsExactly(AppointmentStatus.BOOKED, AppointmentStatus.CANCELLED);
        assertThat(Role.values()).containsExactly(Role.RECEPTIONIST, Role.ADMIN);
    }
}
