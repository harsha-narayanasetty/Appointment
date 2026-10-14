package com.clinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** startTime is a local date-time in the clinic time zone, e.g. 2026-11-02T10:00:00 */
public record AppointmentRequest(
        @NotNull(message = "patientId is required") Long patientId,
        @NotNull(message = "doctorId is required") Long doctorId,
        @NotNull(message = "startTime is required") LocalDateTime startTime
) {
}
