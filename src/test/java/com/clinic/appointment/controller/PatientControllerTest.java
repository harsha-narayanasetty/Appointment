package com.clinic.appointment.controller;

import com.clinic.appointment.dto.PageResponse;
import com.clinic.appointment.dto.PatientRequest;
import com.clinic.appointment.dto.PatientResponse;
import com.clinic.appointment.exception.GlobalExceptionHandler;
import com.clinic.appointment.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {

    private static final String VALID_BODY = """
            {"firstName":"Asha","lastName":"Menon","dateOfBirth":"1990-05-17",
             "email":"asha@example.com","phone":"9876543210"}""";

    @Mock PatientService patientService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PatientController(patientService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void create_validRequest_returns201WithLocation() throws Exception {
        // given
        when(patientService.create(any(PatientRequest.class))).thenReturn(
                new PatientResponse(1L, "Asha", "Menon", LocalDate.of(1990, 5, 17), "asha@example.com", "9876543210"));

        // when / then
        mvc.perform(post("/patients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/patients/1")))
                .andExpect(jsonPath("$.firstName").value("Asha"));
    }

    @Test
    void create_invalidRequest_returns400AndNeverCallsService() throws Exception {
        // given a body with an invalid email
        String body = VALID_BODY.replace("asha@example.com", "nope");

        // when / then
        mvc.perform(post("/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("email: email is not valid"));
        verify(patientService, never()).create(any());
    }

    @Test
    void search_usesDefaultPaging() throws Exception {
        // given
        when(patientService.search(null, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        // when / then
        mvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void search_passesNameAndPagingToService() throws Exception {
        // given
        when(patientService.search("asha", 2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 11, 3));

        // when / then
        mvc.perform(get("/patients").param("name", "asha").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(3));
    }
}
