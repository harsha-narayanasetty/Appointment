package com.clinic.appointment.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentBookingTest extends IntegrationTestBase {

    @Test
    @DisplayName("Many parallel bookings for the same doctor and time produce exactly one success")
    void parallelBookingsForSameSlotProduceExactlyOneSuccess() throws Exception {
        int attempts = 20;
        long patientId = createPatient();
        LocalDateTime slot = slotInDays(3, 10, 0);

        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Integer> request = () -> {
                ready.countDown();
                go.await();
                return book("RECEPTIONIST", patientId, DOCTOR_ID, slot).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(request));
        }
        ready.await();
        go.countDown();

        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 201) created++;
            else if (code == 409) conflicts++;
        }
        pool.shutdown();

        assertThat(created).as("successful bookings").isEqualTo(1);
        assertThat(conflicts).as("409 conflicts").isEqualTo(attempts - 1);
        assertThat(jdbc.queryForObject(
                "select count(*) from appointments where doctor_id = ? and status = 'BOOKED'",
                Long.class, DOCTOR_ID)).isEqualTo(1L);
        // Only the winning booking may produce an event.
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("A cancelled slot can be booked again")
    void cancelledSlotCanBeRebooked() throws Exception {
        long patientId = createPatient();
        LocalDateTime slot = slotInDays(5, 11, 30);

        long firstId = bookAndGetId(patientId, slot);
        book("RECEPTIONIST", patientId, DOCTOR_ID, slot).andExpect(status().isConflict());

        cancel("RECEPTIONIST", firstId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        book("RECEPTIONIST", patientId, DOCTOR_ID, slot)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"));
        assertThat(jdbc.queryForObject(
                "select count(*) from appointments where doctor_id = ? and start_time = ? and status = 'BOOKED'",
                Long.class, DOCTOR_ID, slot)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Overlapping slot for the same doctor is rejected, a different doctor is fine")
    void sameDoctorOverlapRejectedOtherDoctorAllowed() throws Exception {
        long patientId = createPatient();
        LocalDateTime slot = slotInDays(4, 9, 0);

        book("RECEPTIONIST", patientId, 201, slot).andExpect(status().isCreated());
        book("RECEPTIONIST", patientId, 201, slot).andExpect(status().isConflict());
        book("RECEPTIONIST", patientId, 202, slot).andExpect(status().isCreated());
        // The adjacent slot is a different 30-minute window and must still be free.
        book("RECEPTIONIST", patientId, 201, slot.plusMinutes(30)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Booking returns patient name, doctor name, time and status, and is listed with filters")
    void bookedAppointmentIsListed() throws Exception {
        long patientId = createPatient();
        LocalDateTime slot = slotInDays(2, 15, 0);
        book("RECEPTIONIST", patientId, DOCTOR_ID, slot).andExpect(status().isCreated());

        mvc.perform(get("/appointments").param("doctorId", "201").param("status", "BOOKED").header("X-Role", "RECEPTIONIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Test Patient"))
                .andExpect(jsonPath("$[0].doctorName").value("Dr. Asha Rao"))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
        mvc.perform(get("/appointments").param("status", "CANCELLED").header("X-Role", "RECEPTIONIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Invalid requests get meaningful 400 / 404 responses")
    void invalidRequestsAreRejected() throws Exception {
        long patientId = createPatient();

        book("RECEPTIONIST", patientId, DOCTOR_ID, slotInDays(2, 10, 15)).andExpect(status().isBadRequest());   // not on a 30-minute boundary
        book("RECEPTIONIST", patientId, DOCTOR_ID, LocalDateTime.now(CLINIC_ZONE).minusDays(1).withMinute(0).withSecond(0).withNano(0))
                .andExpect(status().isBadRequest());                                                              // in the past
        book("RECEPTIONIST", 999_999, DOCTOR_ID, slotInDays(2, 10, 0))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Patient")));
        book("RECEPTIONIST", patientId, 999_999, slotInDays(2, 10, 0))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Doctor")));
        assertThat(jdbc.queryForObject("select count(*) from appointments", Long.class)).isZero();
    }

    @Test
    @DisplayName("Booking still succeeds while Kafka is down; the event waits in the outbox as PENDING")
    void bookingWorksWhenKafkaIsDown() throws Exception {
        long patientId = createPatient();

        // Kafka points at an unreachable port in this test setup, so nothing can be published.
        book("RECEPTIONIST", patientId, DOCTOR_ID, slotInDays(6, 14, 0)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where status = 'PENDING' and topic = 'appointment.booked'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where status = 'PUBLISHED'", Long.class)).isZero();
    }
}
