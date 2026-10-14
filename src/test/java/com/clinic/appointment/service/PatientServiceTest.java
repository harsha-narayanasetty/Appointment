package com.clinic.appointment.service;

import com.clinic.appointment.dto.PageResponse;
import com.clinic.appointment.dto.PatientRequest;
import com.clinic.appointment.dto.PatientResponse;
import com.clinic.appointment.model.Patient;
import com.clinic.appointment.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock PatientRepository patientRepository;
    @InjectMocks PatientService service;

    private final Patient saved = Patient.builder().id(1L).firstName("Asha").lastName("Menon")
            .dateOfBirth(LocalDate.of(1990, 5, 17)).email("asha@example.com").phone("9876543210").build();

    @Test
    void create_trimsFieldsAndReturnsSavedPatient() {
        // given
        when(patientRepository.save(any(Patient.class))).thenReturn(saved);
        PatientRequest request = new PatientRequest("  Asha ", " Menon  ", LocalDate.of(1990, 5, 17),
                " asha@example.com ", " 9876543210 ");

        // when
        PatientResponse response = service.create(request);

        // then
        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Asha");
        assertThat(captor.getValue().getLastName()).isEqualTo("Menon");
        assertThat(captor.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(captor.getValue().getPhone()).isEqualTo("9876543210");
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void search_withoutName_listsEveryPatient() {
        // given
        when(patientRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(saved)));

        // when
        PageResponse<PatientResponse> result = service.search(null, 0, 20);

        // then
        assertThat(result.content()).hasSize(1);
        verify(patientRepository, never()).searchByName(any(), any());
    }

    @Test
    void search_blankName_listsEveryPatient() {
        // given
        when(patientRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        // when
        service.search("   ", 0, 20);

        // then
        verify(patientRepository).findAll(any(Pageable.class));
    }

    @Test
    void search_withName_searchesUsingTrimmedName() {
        // given
        when(patientRepository.searchByName(eq("asha"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(saved)));

        // when
        PageResponse<PatientResponse> result = service.search("  asha ", 0, 20);

        // then
        assertThat(result.content()).extracting(PatientResponse::firstName).containsExactly("Asha");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void search_negativePageAndTooSmallSize_areCorrected() {
        // given
        when(patientRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        // when
        service.search(null, -5, 0);

        // then
        verify(patientRepository).findAll(pageable(0, 1));
    }

    @Test
    void search_tooLargeSize_isCappedAt100() {
        // given
        when(patientRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        // when
        service.search(null, 2, 1000);

        // then
        verify(patientRepository).findAll(pageable(2, 100));
    }

    private static Pageable pageable(int page, int size) {
        return org.mockito.ArgumentMatchers.argThat(p -> p.getPageNumber() == page && p.getPageSize() == size);
    }
}
