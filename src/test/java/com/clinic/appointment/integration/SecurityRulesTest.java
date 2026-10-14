package com.clinic.appointment.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The URL rules in SecurityConfig, checked against the real filter chain. */
class SecurityRulesTest extends IntegrationTestBase {

    @Test
    void missingRole_returns401WithJsonBody() throws Exception {
        // given a request without the X-Role header
        // when / then
        mvc.perform(get("/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void receptionistRoleIsRejectedOutsideItsAreas_with403Body() throws Exception {
        // given a RECEPTIONIST
        // when / then
        mvc.perform(post("/doctors").header("X-Role", "RECEPTIONIST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void receptionistMayReadPatientsDoctorsAndAppointments() throws Exception {
        // given a RECEPTIONIST
        // when / then
        mvc.perform(get("/patients").header("X-Role", "RECEPTIONIST")).andExpect(status().isOk());
        mvc.perform(get("/doctors").header("X-Role", "RECEPTIONIST")).andExpect(status().isOk());
        mvc.perform(get("/appointments").header("X-Role", "RECEPTIONIST")).andExpect(status().isOk());
    }

    @Test
    void adminPassesTheRoleCheckOnAnyPath() throws Exception {
        // given an ADMIN
        // when / then (the path does not exist, so 404 proves it was not blocked)
        mvc.perform(get("/anything-else").header("X-Role", "ADMIN")).andExpect(status().isNotFound());
    }
}
