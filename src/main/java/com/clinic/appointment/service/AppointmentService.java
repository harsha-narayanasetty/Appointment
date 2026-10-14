package com.clinic.appointment.service;

import com.clinic.appointment.dto.AppointmentRequest;
import com.clinic.appointment.dto.AppointmentResponse;
import com.clinic.appointment.exception.BadRequestException;
import com.clinic.appointment.exception.ConflictException;
import com.clinic.appointment.exception.ForbiddenException;
import com.clinic.appointment.exception.NotFoundException;
import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.model.Doctor;
import com.clinic.appointment.model.Patient;
import com.clinic.appointment.outbox.OutboxService;
import com.clinic.appointment.repository.AppointmentRepository;
import com.clinic.appointment.repository.DoctorRepository;
import com.clinic.appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final Sort BY_START = Sort.by("startTime", "id");

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final Clock clinicClock;
    private final OutboxService outboxService;

    @Transactional
    public AppointmentResponse book(AppointmentRequest req) {
        // Cheks for the slot validity (30-minute boundary, future time) before checking for patient/doctor existence.
        validateSlot(req.startTime());
        Patient patient = patientRepository.findById(req.patientId())
                .orElseThrow(() -> new NotFoundException("Patient with the requested Id " + req.patientId() + " does not exist."));
        Doctor doctor = doctorRepository.findById(req.doctorId())
                .orElseThrow(() -> new NotFoundException("Doctor with the requested Id " + req.doctorId() + " does not exist."));

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .startTime(req.startTime())
                .status(AppointmentStatus.BOOKED)
                .build();
        try {
            // flush now so the unique-index violation surfaces here, not at commit time
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                     doctor.getName() + " with Id " +
                    doctor.getId() + " is already booked for the requested time slot: " + req.startTime().toString().split("T")[1]);
        }
        outboxService.recordAppointmentBooked(appointment);
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> find(Long doctorId, AppointmentStatus status) {
        List<Appointment> result;
        if (doctorId != null && status != null) {
            result = appointmentRepository.findByDoctorIdAndStatus(doctorId, status, BY_START);
        } else if (doctorId != null) {
            result = appointmentRepository.findByDoctorId(doctorId, BY_START);
        } else if (status != null) {
            result = appointmentRepository.findByStatus(status, BY_START);
        } else {
            result = appointmentRepository.findAll(BY_START);
        }
        return result.stream().map(AppointmentResponse::from).toList();
    }

    @Transactional
    public AppointmentResponse cancel(Long id, boolean isAdmin) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment " + id + " does not exist"));

        // Cancelling twice is harmless: return the current state.
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return AppointmentResponse.from(appointment);
        }

        LocalDateTime now = LocalDateTime.now(clinicClock);
        if (!isAdmin && appointment.getStartTime().isBefore(now.plusHours(24))) {
            throw new ForbiddenException(
                    "You are not allowed to cancel the appointment. Please contact Admin.");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return AppointmentResponse.from(appointment);
    }

    private void validateSlot(LocalDateTime start) {
        if (start.getMinute() % Appointment.DURATION_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw new BadRequestException("Please check the Date and Time of the appointment. It must be on a 30-minute boundary (e.g. 10:00 or 10:30)");
        }
        if (!start.isAfter(LocalDateTime.now(clinicClock))) {
            throw new BadRequestException("Please check the Date and Time of the appointment. It must be in the future.");
        }
    }
}
