package com.clinic.appointment.repository;

import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // The entity graph loads patient + doctor in one query (avoids N+1 when building responses).
    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findAll(Sort sort);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByDoctorId(Long doctorId, Sort sort);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByStatus(AppointmentStatus status, Sort sort);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, AppointmentStatus status, Sort sort);
}
