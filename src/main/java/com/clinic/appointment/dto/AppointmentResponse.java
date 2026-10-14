package com.clinic.appointment.dto;

import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        AppointmentStatus status
) {
    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getPatient().getId(),
                a.getPatient().getFirstName() + " " + a.getPatient().getLastName(),
                a.getDoctor().getId(),
                a.getDoctor().getName(),
                a.getStartTime(),
                a.getStartTime().plusMinutes(Appointment.DURATION_MINUTES),
                a.getStatus());
    }
}
