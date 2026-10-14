package com.clinic.appointment.dto;

import com.clinic.appointment.model.Patient;

import java.time.LocalDate;

public record PatientResponse(
        Long id, String firstName, String lastName, LocalDate dateOfBirth, String email, String phone
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getDateOfBirth(),
                p.getEmail(),
                p.getPhone()
        );
    }
}
