package com.clinic.appointment.service;

import com.clinic.appointment.dto.DoctorResponse;
import com.clinic.appointment.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;

    @Transactional(readOnly = true)
    public List<DoctorResponse> findAll() {
        return doctorRepository.findAll(Sort.by("id")).stream().map(DoctorResponse::from).toList();
    }
}
