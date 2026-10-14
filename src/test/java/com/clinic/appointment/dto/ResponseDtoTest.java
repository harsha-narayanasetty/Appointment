package com.clinic.appointment.dto;

import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.model.Doctor;
import com.clinic.appointment.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseDtoTest {

    @Test
    void appointmentResponse_combinesNamesAndComputesEndTime() {
        // given
        Appointment appointment = Appointment.builder().id(5L)
                .patient(Patient.builder().id(101L).firstName("Asha").lastName("Menon").build())
                .doctor(Doctor.builder().id(201L).name("Dr. Asha Rao").build())
                .startTime(LocalDateTime.of(2026, 11, 2, 10, 0))
                .status(AppointmentStatus.BOOKED).build();

        // when
        AppointmentResponse response = AppointmentResponse.from(appointment);

        // then
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.patientId()).isEqualTo(101L);
        assertThat(response.patientName()).isEqualTo("Asha Menon");
        assertThat(response.doctorId()).isEqualTo(201L);
        assertThat(response.doctorName()).isEqualTo("Dr. Asha Rao");
        assertThat(response.startTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0));
        assertThat(response.endTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 30));
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void patientResponse_copiesAllFields() {
        // given
        Patient patient = Patient.builder().id(1L).firstName("Asha").lastName("Menon")
                .dateOfBirth(LocalDate.of(1990, 5, 17)).email("asha@example.com").phone("9876543210").build();

        // when
        PatientResponse response = PatientResponse.from(patient);

        // then
        assertThat(response).isEqualTo(new PatientResponse(1L, "Asha", "Menon", LocalDate.of(1990, 5, 17),
                "asha@example.com", "9876543210"));
    }

    @Test
    void doctorResponse_copiesAllFields() {
        // given
        Doctor doctor = Doctor.builder().id(201L).name("Dr. Asha Rao").specialization("General Medicine").build();

        // when
        DoctorResponse response = DoctorResponse.from(doctor);

        // then
        assertThat(response).isEqualTo(new DoctorResponse(201L, "Dr. Asha Rao", "General Medicine"));
    }

    @Test
    void pageResponse_mapsContentAndKeepsPagingInfo() {
        // given
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        // when
        PageResponse<String> response = PageResponse.from(page, n -> "n" + n);

        // then
        assertThat(response.content()).containsExactly("n1", "n2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void errorResponse_withoutDetails_hasEmptyDetails() {
        // given / when
        ErrorResponse response = ErrorResponse.of(404, "Not Found", "missing");

        // then
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.message()).isEqualTo("missing");
        assertThat(response.details()).isEmpty();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void errorResponse_withDetails_keepsThem() {
        // given / when
        ErrorResponse response = ErrorResponse.of(400, "Bad Request", "Validation failed", List.of("email: invalid"));

        // then
        assertThat(response.details()).containsExactly("email: invalid");
    }
}
