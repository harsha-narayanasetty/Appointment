package com.clinic.appointment.config;

import com.clinic.appointment.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * MOCK authentication: trusts an "X-Role: ADMIN | RECEPTIONIST" header.
 * Fine for the exercise; replace with a signed JWT for anything real.
 * Roles are still ENFORCED server-side by SecurityConfig and the service layer.
 */
public class HeaderAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null) {
            try {
                Role role = Role.valueOf(header.trim().toUpperCase());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                role.name().toLowerCase(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
            } catch (IllegalArgumentException ignored) {
                // unknown role -> stays unauthenticated -> 401
            }
        }
        chain.doFilter(request, response);
    }
}
