package com.clinic.appointment.integration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runs the real application (real Flyway migrations, real PostgreSQL rules) against a throw-away embedded
 * PostgreSQL started by the test itself, so no Docker or local database is needed. Kafka is pointed at a
 * port nobody listens on, which doubles as the "Kafka is down" scenario.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    protected static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");
    protected static final long DOCTOR_ID = 201L;

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // JVM is exiting
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("spring.kafka.admin.fail-fast", () -> "false");
        registry.add("spring.kafka.admin.properties.default.api.timeout.ms", () -> "1000");
        registry.add("spring.kafka.admin.properties.request.timeout.ms", () -> "1000");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        // Doctors are seeded by migration and stay; everything the tests create is removed.
        jdbc.execute("TRUNCATE appointments, outbox_events, patients RESTART IDENTITY CASCADE");
    }

    protected long createPatient() throws Exception {
        String body = """
                {"firstName":"Test","lastName":"Patient","dateOfBirth":"1990-05-17",
                 "email":"test.patient@example.com","phone":"9876543210"}""";
        String response = mvc.perform(post("/patients")
                        .header("X-Role", "RECEPTIONIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    protected ResultActions book(String role, long patientId, long doctorId, LocalDateTime start) throws Exception {
        return mvc.perform(post("/appointments")
                .header("X-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":%d,\"doctorId\":%d,\"startTime\":\"%s\"}"
                        .formatted(patientId, doctorId, start)));
    }

    protected ResultActions cancel(String role, long appointmentId) throws Exception {
        return mvc.perform(post("/appointments/{id}/cancel", appointmentId).header("X-Role", role));
    }

    protected long bookAndGetId(long patientId, LocalDateTime start) throws Exception {
        String response = book("RECEPTIONIST", patientId, DOCTOR_ID, start)
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    /** A valid slot (30-minute boundary) a few days from now. */
    protected static LocalDateTime slotInDays(int days, int hour, int minute) {
        return LocalDateTime.now(CLINIC_ZONE).plusDays(days).truncatedTo(ChronoUnit.DAYS).plusHours(hour).plusMinutes(minute);
    }

    /** A valid slot that starts roughly 1-1.5 hours from now, so it is inside the 24-hour cancellation window. */
    protected static LocalDateTime slotWithin24Hours() {
        LocalDateTime t = LocalDateTime.now(CLINIC_ZONE).plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        return t.withMinute(t.getMinute() < 30 ? 0 : 30);
    }
}
