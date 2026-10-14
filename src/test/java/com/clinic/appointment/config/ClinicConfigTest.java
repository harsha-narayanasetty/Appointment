package com.clinic.appointment.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicConfigTest {

    @Test
    void clinicClock_usesConfiguredTimeZone() {
        // given
        ClinicConfig config = new ClinicConfig();

        // when
        Clock clock = config.clinicClock("Asia/Kolkata");

        // then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}
