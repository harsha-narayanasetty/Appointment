package com.clinic.appointment.controller;

import com.clinic.appointment.dto.DoctorResponse;
import com.clinic.appointment.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DoctorControllerTest {

    @Mock DoctorService doctorService;

    @Test
    void list_returnsDoctors() throws Exception {
        // given
        when(doctorService.findAll()).thenReturn(List.of(new DoctorResponse(201L, "Dr. Asha Rao", "General Medicine")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DoctorController(doctorService)).build();

        // when / then
        mvc.perform(get("/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(201))
                .andExpect(jsonPath("$[0].name").value("Dr. Asha Rao"))
                .andExpect(jsonPath("$[0].specialization").value("General Medicine"));
    }
}
