package com.clinic.appointment.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new HeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // RECEPTIONIST: patients + appointments. ADMIN: everything.
                        .requestMatchers("/patients", "/patients/**").hasAnyRole("RECEPTIONIST", "ADMIN")
                        .requestMatchers("/appointments", "/appointments/**").hasAnyRole("RECEPTIONIST", "ADMIN")
                        // Read-only doctor list: receptionists need it to pick a doctor when booking.
                        .requestMatchers(HttpMethod.GET, "/doctors").hasAnyRole("RECEPTIONIST", "ADMIN")
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(res, 401, "Unauthorized", "Missing or invalid X-Role header"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(res, 403, "Forbidden", "Your role is not allowed to perform this action")));
        return http.build();
    }

    private static void writeJson(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"details\":[]}");
    }
}
