package com.clinic.appointment.controller;

import com.clinic.appointment.dto.AppointmentRequest;
import com.clinic.appointment.dto.AppointmentResponse;
import com.clinic.appointment.model.AppointmentStatus;
import com.clinic.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse created = appointmentService.book(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public List<AppointmentResponse> list(@RequestParam(required = false) Long doctorId,
                                          @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.find(doctorId, status);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return appointmentService.cancel(id, isAdmin);
    }
}
