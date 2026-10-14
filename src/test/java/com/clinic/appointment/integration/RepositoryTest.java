package com.clinic.appointment.integration;

import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.model.Doctor;
import com.clinic.appointment.model.Patient;
import com.clinic.appointment.outbox.OutboxRepository;
import com.clinic.appointment.outbox.OutboxStatus;
import com.clinic.appointment.repository.AppointmentRepository;
import com.clinic.appointment.repository.DoctorRepository;
import com.clinic.appointment.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTest extends IntegrationTestBase {

    private static final Sort BY_START = Sort.by("startTime", "id");

    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired TransactionTemplate tx;

    private Patient savePatient(String first, String last) {
        return patientRepository.save(Patient.builder().firstName(first).lastName(last)
                .dateOfBirth(LocalDate.of(1990, 1, 1)).email("p@example.com").phone("9876543210").build());
    }

    private Appointment saveAppointment(Patient patient, long doctorId, LocalDateTime start, AppointmentStatus status) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        return appointmentRepository.saveAndFlush(Appointment.builder().patient(patient).doctor(doctor)
                .startTime(start).status(status).build());
    }

    @Test
    void doctors_areSeededByMigration() {
        // given the migrations have run

        // when
        List<Doctor> doctors = doctorRepository.findAll(Sort.by("id"));

        // then
        assertThat(doctors).extracting(Doctor::getId).containsExactly(201L, 202L, 203L, 204L);
    }

    @Test
    void searchByName_matchesFirstLastAndFullNameIgnoringCase() {
        // given
        savePatient("Asha", "Menon");
        savePatient("Ravi", "Ashwin");
        savePatient("Meera", "Iyer");

        // when
        var byFirst = patientRepository.searchByName("ASHA", PageRequest.of(0, 10));
        var byLast = patientRepository.searchByName("iyer", PageRequest.of(0, 10));
        var byFull = patientRepository.searchByName("asha menon", PageRequest.of(0, 10));
        var partial = patientRepository.searchByName("ash", PageRequest.of(0, 10));

        // then
        assertThat(byFirst.getContent()).extracting(Patient::getFirstName).containsExactly("Asha");
        assertThat(byLast.getContent()).extracting(Patient::getFirstName).containsExactly("Meera");
        assertThat(byFull.getContent()).extracting(Patient::getLastName).containsExactly("Menon");
        assertThat(partial.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchByName_isPaginated() {
        // given
        for (int i = 0; i < 5; i++) savePatient("Asha" + i, "Menon");

        // when
        var page = patientRepository.searchByName("asha", PageRequest.of(1, 2, Sort.by("firstName")));

        // then
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Patient::getFirstName).containsExactly("Asha2", "Asha3");
    }

    @Test
    void appointmentFilters_returnMatchesSortedByStartTime() {
        // given
        Patient patient = savePatient("Asha", "Menon");
        LocalDateTime base = slotInDays(3, 9, 0);
        saveAppointment(patient, 201, base.plusHours(2), AppointmentStatus.BOOKED);
        saveAppointment(patient, 201, base, AppointmentStatus.CANCELLED);
        saveAppointment(patient, 202, base.plusHours(1), AppointmentStatus.BOOKED);

        // when
        var all = appointmentRepository.findAll(BY_START);
        var byDoctor = appointmentRepository.findByDoctorId(201L, BY_START);
        var byStatus = appointmentRepository.findByStatus(AppointmentStatus.BOOKED, BY_START);
        var byBoth = appointmentRepository.findByDoctorIdAndStatus(201L, AppointmentStatus.BOOKED, BY_START);

        // then
        assertThat(all).extracting(Appointment::getStartTime).isSorted();
        assertThat(byDoctor).hasSize(2);
        assertThat(byStatus).hasSize(2);
        assertThat(byBoth).hasSize(1);
        assertThat(byBoth.get(0).getPatient().getFirstName()).isEqualTo("Asha");   // loaded together with the appointment
    }

    @Test
    void lockNextBatch_returnsOnlyPendingEventsOldestFirst() {
        // given
        jdbc.update("""
                insert into outbox_events (event_id, topic, event_key, payload, status, created_at, attempts)
                values (gen_random_uuid(), 't', '1', '{}', 'PUBLISHED', now(), 0),
                       (gen_random_uuid(), 't', '2', '{}', 'PENDING', now(), 0),
                       (gen_random_uuid(), 't', '3', '{}', 'PENDING', now(), 0)""");

        // when
        var batch = tx.execute(status -> outboxRepository.lockNextBatch());

        // then
        assertThat(batch).extracting("eventKey").containsExactly("2", "3");
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(2);
    }
}
