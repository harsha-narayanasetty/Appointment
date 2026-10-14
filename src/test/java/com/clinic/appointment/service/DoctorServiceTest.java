package com.clinic.appointment.service;

import com.clinic.appointment.dto.DoctorResponse;
import com.clinic.appointment.model.Doctor;
import com.clinic.appointment.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock DoctorRepository doctorRepository;
    @InjectMocks DoctorService service;

    @Test
    void findAll_returnsDoctorsOrderedById() {
        // given
        when(doctorRepository.findAll(Sort.by("id"))).thenReturn(List.of(
                Doctor.builder().id(201L).name("Dr. Asha Rao").specialization("General Medicine").build(),
                Doctor.builder().id(202L).name("Dr. Vikram Shah").specialization("Cardiology").build()));

        // when
        List<DoctorResponse> result = service.findAll();

        // then
        assertThat(result).extracting(DoctorResponse::id).containsExactly(201L, 202L);
        assertThat(result.get(1).specialization()).isEqualTo("Cardiology");
        verify(doctorRepository).findAll(Sort.by("id"));
    }

    @Test
    void findAll_noDoctors_returnsEmptyList() {
        // given
        when(doctorRepository.findAll(Sort.by("id"))).thenReturn(List.of());

        // when / then
        assertThat(service.findAll()).isEmpty();
    }
}
