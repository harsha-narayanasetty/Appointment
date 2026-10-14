package com.clinic.appointment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClinicConfig {

    /** The single clinic time zone. A Clock bean makes "now" easy to control in tests. */
    @Bean
    public Clock clinicClock(@Value("${clinic.time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
