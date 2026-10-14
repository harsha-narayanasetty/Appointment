package com.clinic.appointment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OutboxConfig {

    /** Created on startup if missing (only works while Kafka is reachable; the broker may also auto-create it). */
    @Bean
    public NewTopic appointmentBookedTopic(@Value("${outbox.topic:appointment.booked}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
