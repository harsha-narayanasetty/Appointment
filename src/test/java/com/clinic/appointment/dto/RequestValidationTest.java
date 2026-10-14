package com.clinic.appointment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static PatientRequest patient(String first, String last, LocalDate dob, String email, String phone) {
        return new PatientRequest(first, last, dob, email, phone);
    }

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void patientRequest_validData_hasNoViolations() {
        // given
        PatientRequest request = patient("Asha", "Menon", LocalDate.of(1990, 5, 17), "asha@example.com", "9876543210");

        // when / then
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void patientRequest_missingEverything_reportsEveryField() {
        // given
        PatientRequest request = patient(null, " ", null, "", null);

        // when / then
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("firstName", "lastName", "dateOfBirth", "email", "phone");
    }

    @Test
    void patientRequest_dateOfBirthInTheFuture_isRejected() {
        // given
        PatientRequest request = patient("Asha", "Menon", LocalDate.now().plusDays(1), "asha@example.com", "9876543210");

        // when / then
        assertThat(invalidFields(request)).containsExactly("dateOfBirth");
    }

    @Test
    void patientRequest_badEmail_isRejected() {
        // given
        PatientRequest request = patient("Asha", "Menon", LocalDate.of(1990, 5, 17), "not-an-email", "9876543210");

        // when / then
        assertThat(invalidFields(request)).containsExactly("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "98765abcde", "+91-98765-43210", "987654321012345"})
    void patientRequest_badPhone_isRejected(String phone) {
        // given
        PatientRequest request = patient("Asha", "Menon", LocalDate.of(1990, 5, 17), "asha@example.com", phone);

        // when / then
        assertThat(invalidFields(request)).containsExactly("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "+919876543210", "919876543210", "09876543210"})
    void patientRequest_acceptedPhoneFormats(String phone) {
        // given
        PatientRequest request = patient("Asha", "Menon", LocalDate.of(1990, 5, 17), "asha@example.com", phone);

        // when / then
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void patientRequest_nameLongerThan100Characters_isRejected() {
        // given
        PatientRequest request = patient("A".repeat(101), "Menon", LocalDate.of(1990, 5, 17), "asha@example.com", "9876543210");

        // when / then
        assertThat(invalidFields(request)).containsExactly("firstName");
    }

    @Test
    void appointmentRequest_validData_hasNoViolations() {
        // given
        AppointmentRequest request = new AppointmentRequest(1L, 201L, LocalDateTime.of(2026, 11, 2, 10, 0));

        // when / then
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void appointmentRequest_missingFields_reportsEveryField() {
        // given
        AppointmentRequest request = new AppointmentRequest(null, null, null);

        // when / then
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("patientId", "doctorId", "startTime");
    }
}
