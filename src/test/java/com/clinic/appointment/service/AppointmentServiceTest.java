package com.clinic.appointment.service;

import com.clinic.appointment.dto.AppointmentRequest;
import com.clinic.appointment.dto.AppointmentResponse;
import com.clinic.appointment.exception.BadRequestException;
import com.clinic.appointment.exception.ConflictException;
import com.clinic.appointment.exception.ForbiddenException;
import com.clinic.appointment.exception.NotFoundException;
import com.clinic.appointment.model.Appointment;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.model.Doctor;
import com.clinic.appointment.model.Patient;
import com.clinic.appointment.outbox.OutboxService;
import com.clinic.appointment.repository.AppointmentRepository;
import com.clinic.appointment.repository.DoctorRepository;
import com.clinic.appointment.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Mock AppointmentRepository appointmentRepository;
    @Mock PatientRepository patientRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock OutboxService outboxService;

    private AppointmentService service;
    private final Patient patient = Patient.builder().id(101L).firstName("Asha").lastName("Menon").build();
    private final Doctor doctor = Doctor.builder().id(201L).name("Dr. Asha Rao").build();

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new AppointmentService(appointmentRepository, patientRepository, doctorRepository, fixedClock, outboxService);
    }

    private Appointment appointment(long id, LocalDateTime start, AppointmentStatus status) {
        return Appointment.builder().id(id).patient(patient).doctor(doctor).startTime(start).status(status).build();
    }

    // ---- book ----

    @Test
    void book_validRequest_savesBookedAppointmentAndRecordsEvent() {
        // given
        LocalDateTime start = NOW.plusDays(2).withMinute(30);
        when(patientRepository.findById(101L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(201L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(inv -> {
                    Appointment a = inv.getArgument(0);
                    a.setId(1L);
                    return a;
                });

        // when
        AppointmentResponse response = service.book(new AppointmentRequest(101L, 201L, start));

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.patientName()).isEqualTo("Asha Menon");
        assertThat(response.doctorName()).isEqualTo("Dr. Asha Rao");
        assertThat(response.endTime()).isEqualTo(start.plusMinutes(30));
        verify(outboxService).recordAppointmentBooked(any(Appointment.class));
    }

    @Test
    void book_startNotOnThirtyMinuteBoundary_throwsBadRequest() {
        // given
        AppointmentRequest request = new AppointmentRequest(101L, 201L, NOW.plusDays(1).withMinute(15));

        // when / then
        assertThatThrownBy(() -> service.book(request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(patientRepository, doctorRepository, appointmentRepository, outboxService);
    }

    @Test
    void book_startHasSeconds_throwsBadRequest() {
        // given
        AppointmentRequest request = new AppointmentRequest(101L, 201L, NOW.plusDays(1).withSecond(10));

        // when / then
        assertThatThrownBy(() -> service.book(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void book_startInThePast_throwsBadRequest() {
        // given
        AppointmentRequest request = new AppointmentRequest(101L, 201L, NOW.minusHours(2));

        // when / then
        assertThatThrownBy(() -> service.book(request)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
        verifyNoInteractions(appointmentRepository, outboxService);
    }

    @Test
    void book_startEqualsNow_throwsBadRequest() {
        // given
        AppointmentRequest request = new AppointmentRequest(101L, 201L, NOW);

        // when / then
        assertThatThrownBy(() -> service.book(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void book_unknownPatient_throwsNotFound() {
        // given
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.book(new AppointmentRequest(999L, 201L, NOW.plusDays(1))))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Patient");
        verifyNoInteractions(appointmentRepository, outboxService);
    }

    @Test
    void book_unknownDoctor_throwsNotFound() {
        // given
        when(patientRepository.findById(101L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.book(new AppointmentRequest(101L, 999L, NOW.plusDays(1))))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Doctor");
        verifyNoInteractions(appointmentRepository, outboxService);
    }

    @Test
    void book_slotAlreadyTaken_throwsConflictAndRecordsNoEvent() {
        // given
        when(patientRepository.findById(101L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(201L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("no_overlapping_bookings"));

        // when / then
        assertThatThrownBy(() -> service.book(new AppointmentRequest(101L, 201L, NOW.plusDays(1))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already booked");
        verify(outboxService, never()).recordAppointmentBooked(any());
    }

    // ---- find ----

    @Test
    void find_noFilters_returnsAllSortedByStart() {
        // given
        when(appointmentRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(appointment(1, NOW.plusDays(1), AppointmentStatus.BOOKED)));

        // when
        List<AppointmentResponse> result = service.find(null, null);

        // then
        assertThat(result).hasSize(1);
        verify(appointmentRepository).findAll(Sort.by("startTime", "id"));
    }

    @Test
    void find_byDoctorOnly_usesDoctorFilter() {
        // given
        when(appointmentRepository.findByDoctorId(eq(201L), any(Sort.class))).thenReturn(List.of());

        // when
        service.find(201L, null);

        // then
        verify(appointmentRepository).findByDoctorId(201L, Sort.by("startTime", "id"));
    }

    @Test
    void find_byStatusOnly_usesStatusFilter() {
        // given
        when(appointmentRepository.findByStatus(eq(AppointmentStatus.CANCELLED), any(Sort.class))).thenReturn(List.of());

        // when
        service.find(null, AppointmentStatus.CANCELLED);

        // then
        verify(appointmentRepository).findByStatus(AppointmentStatus.CANCELLED, Sort.by("startTime", "id"));
    }

    @Test
    void find_byDoctorAndStatus_usesBothFilters() {
        // given
        when(appointmentRepository.findByDoctorIdAndStatus(eq(201L), eq(AppointmentStatus.BOOKED), any(Sort.class)))
                .thenReturn(List.of());

        // when
        service.find(201L, AppointmentStatus.BOOKED);

        // then
        verify(appointmentRepository).findByDoctorIdAndStatus(201L, AppointmentStatus.BOOKED, Sort.by("startTime", "id"));
    }

    // ---- cancel ----

    @Test
    void cancel_unknownAppointment_throwsNotFound() {
        // given
        when(appointmentRepository.findById(9L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.cancel(9L, false)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancel_receptionistMoreThan24HoursAway_cancels() {
        // given
        Appointment a = appointment(1, NOW.plusHours(25), AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        // when
        AppointmentResponse response = service.cancel(1L, false);

        // then
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_receptionistExactly24HoursAway_cancels() {
        // given
        Appointment a = appointment(1, NOW.plusHours(24), AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        // when
        AppointmentResponse response = service.cancel(1L, false);

        // then
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_receptionistWithin24Hours_isForbiddenAndStaysBooked() {
        // given
        Appointment a = appointment(1, NOW.plusHours(23), AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        // when / then
        assertThatThrownBy(() -> service.cancel(1L, false)).isInstanceOf(ForbiddenException.class);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void cancel_adminWithin24Hours_cancels() {
        // given
        Appointment a = appointment(1, NOW.plusHours(1), AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        // when
        AppointmentResponse response = service.cancel(1L, true);

        // then
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_returnsCurrentStateEvenForReceptionistWithin24Hours() {
        // given
        Appointment a = appointment(1, NOW.plusHours(1), AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        // when
        AppointmentResponse response = service.cancel(1L, false);

        // then
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }
}
