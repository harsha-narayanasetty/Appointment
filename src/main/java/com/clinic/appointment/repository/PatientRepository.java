package com.clinic.appointment.repository;

import com.clinic.appointment.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("""
            select p from Patient p
            where lower(p.firstName) like lower(concat('%', :name, '%'))
               or lower(p.lastName)  like lower(concat('%', :name, '%'))
               or lower(concat(p.firstName, ' ', p.lastName)) like lower(concat('%', :name, '%'))
            """)
    Page<Patient> searchByName(@Param("name") String name, Pageable pageable);
}
