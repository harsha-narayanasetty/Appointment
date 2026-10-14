package com.clinic.appointment.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationTest extends IntegrationTestBase {

    @Test
    @DisplayName("A RECEPTIONIST is rejected where only ADMIN is allowed (cancel within 24 hours)")
    void receptionistCannotCancelWithin24Hours() throws Exception {
        long patientId = createPatient();
        long id = bookAndGetId(patientId, slotWithin24Hours());

        cancel("RECEPTIONIST", id).andExpect(status().isForbidden());
        mvc.perform(get("/appointments").param("status", "BOOKED").header("X-Role", "RECEPTIONIST"))
                .andExpect(jsonPath("$.length()").value(1));   // still BOOKED

        cancel("ADMIN", id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("A RECEPTIONIST is rejected on endpoints that are ADMIN-only")
    void receptionistCannotUseAdminOnlyEndpoints() throws Exception {
        mvc.perform(post("/doctors").header("X-Role", "RECEPTIONIST").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/anything").header("X-Role", "RECEPTIONIST")).andExpect(status().isForbidden());
        // ADMIN passes the role check (the path simply does not exist, so 404 rather than 403).
        mvc.perform(get("/admin/anything").header("X-Role", "ADMIN")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A RECEPTIONIST may cancel an appointment that starts more than 24 hours away")
    void receptionistCanCancelFarAwayAppointment() throws Exception {
        long patientId = createPatient();
        long id = bookAndGetId(patientId, slotInDays(4, 10, 0));

        cancel("RECEPTIONIST", id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Cancelling twice is harmless, and cancelling an unknown appointment is 404")
    void cancellingTwiceIsHarmless() throws Exception {
        long patientId = createPatient();
        long id = bookAndGetId(patientId, slotInDays(4, 12, 0));

        cancel("RECEPTIONIST", id).andExpect(status().isOk());
        cancel("RECEPTIONIST", id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        cancel("RECEPTIONIST", 999_999).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Missing or unknown role is 401; RECEPTIONIST and ADMIN may use patients, doctors and appointments")
    void authenticationAndBaseRoleAccess() throws Exception {
        mvc.perform(get("/doctors")).andExpect(status().isUnauthorized());
        mvc.perform(get("/doctors").header("X-Role", "JANITOR")).andExpect(status().isUnauthorized());

        mvc.perform(get("/doctors").header("X-Role", "RECEPTIONIST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/patients").header("X-Role", "RECEPTIONIST")).andExpect(status().isOk());
        mvc.perform(get("/appointments").header("X-Role", "ADMIN")).andExpect(status().isOk());
    }
}
