package com.clinic.appointment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * startTime is stored as a local date-time in the single clinic time zone.
 * Double-booking is prevented by a partial unique index created:
 *   UNIQUE (doctor_id, start_time) WHERE status = 'BOOKED'
 */
@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appt_doctor_status", columnList = "doctor_id,status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Appointment {
    // Externalise the value in the properties file if you want to make it configurable. For now, it's a constant.
    public static final int DURATION_MINUTES = 30;

    @Id
    @Column(name = "appointment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;


    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;
}
