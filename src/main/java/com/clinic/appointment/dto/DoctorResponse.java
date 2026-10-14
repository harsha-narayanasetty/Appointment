package com.clinic.appointment.dto;

import com.clinic.appointment.model.Doctor;

public record DoctorResponse(Long id, String name, String specialization) {
    public static DoctorResponse from(Doctor d) {
        return new DoctorResponse(d.getId(), d.getName(), d.getSpecialization());
    }
}
