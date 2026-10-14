package com.clinic.appointment.service;

import com.clinic.appointment.dto.PageResponse;
import com.clinic.appointment.dto.PatientRequest;
import com.clinic.appointment.dto.PatientResponse;
import com.clinic.appointment.model.Patient;
import com.clinic.appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PatientRepository patientRepository;

    @Transactional
    public PatientResponse create(PatientRequest req) {
        Patient saved = patientRepository.save(Patient.builder()
                .firstName(req.firstName().trim())
                .lastName(req.lastName().trim())
                .dateOfBirth(req.dateOfBirth())
                .email(req.email().trim())
                .phone(req.phone().trim())
                .build());
        return PatientResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> search(String name, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("lastName", "firstName", "id"));

        Page<Patient> result = (name == null || name.isBlank())
                ? patientRepository.findAll(pageable)
                : patientRepository.searchByName(name.trim(), pageable);

        return PageResponse.from(result, PatientResponse::from);
    }
}
