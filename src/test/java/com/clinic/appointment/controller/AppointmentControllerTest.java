package com.clinic.appointment.controller;

import com.clinic.appointment.dto.AppointmentRequest;
import com.clinic.appointment.dto.AppointmentResponse;
import com.clinic.appointment.exception.ConflictException;
import com.clinic.appointment.exception.GlobalExceptionHandler;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
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
class AppointmentControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock AppointmentService appointmentService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointmentService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private AppointmentResponse response(long id, AppointmentStatus status) {
        return new AppointmentResponse(id, 101L, "Asha Menon", 201L, "Dr. Asha Rao", START, START.plusMinutes(30), status);
    }

    @Test
    void book_validRequest_returns201WithLocationAndBody() throws Exception {
        // given
        when(appointmentService.book(new AppointmentRequest(101L, 201L, START))).thenReturn(response(7, AppointmentStatus.BOOKED));

        // when / then
        mvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":101,\"doctorId\":201,\"startTime\":\"2026-11-02T10:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/appointments/7")))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.patientName").value("Asha Menon"))
                .andExpect(jsonPath("$.doctorName").value("Dr. Asha Rao"));
    }

    @Test
    void book_missingFields_returns400AndNeverCallsService() throws Exception {
        // given an empty body
        // when / then
        mvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(3));
        verify(appointmentService, never()).book(any());
    }

    @Test
    void book_slotTaken_returns409() throws Exception {
        // given
        when(appointmentService.book(any())).thenThrow(new ConflictException("already booked"));

        // when / then
        mvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":101,\"doctorId\":201,\"startTime\":\"2026-11-02T10:00:00\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void list_withoutFilters_passesNullFilters() throws Exception {
        // given
        when(appointmentService.find(null, null)).thenReturn(List.of(response(1, AppointmentStatus.BOOKED)));

        // when / then
        mvc.perform(get("/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void list_withDoctorAndStatus_passesFiltersToService() throws Exception {
        // given
        when(appointmentService.find(201L, AppointmentStatus.CANCELLED)).thenReturn(List.of());

        // when / then
        mvc.perform(get("/appointments").param("doctorId", "201").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_unknownStatus_returns400() throws Exception {
        // given / when / then
        mvc.perform(get("/appointments").param("status", "DONE")).andExpect(status().isBadRequest());
    }

    @Test
    void cancel_asAdmin_passesAdminFlagTrue() throws Exception {
        // given
        when(appointmentService.cancel(5L, true)).thenReturn(response(5, AppointmentStatus.CANCELLED));
        var admin = new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when / then
        mvc.perform(post("/appointments/5/cancel").principal(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_asReceptionist_passesAdminFlagFalse() throws Exception {
        // given
        when(appointmentService.cancel(5L, false)).thenReturn(response(5, AppointmentStatus.CANCELLED));
        var receptionist = new UsernamePasswordAuthenticationToken("receptionist", null,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));

        // when / then
        mvc.perform(post("/appointments/5/cancel").principal(receptionist)).andExpect(status().isOk());
        verify(appointmentService).cancel(5L, false);
    }
}
