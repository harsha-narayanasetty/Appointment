package com.clinic.appointment.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HeaderAuthFilterTest {

    private final HeaderAuthFilter filter = new HeaderAuthFilter();
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validRole_authenticatesWithMatchingAuthority() throws Exception {
        // given
        request.addHeader(HeaderAuthFilter.HEADER, "ADMIN");

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        verify(chain).doFilter(request, response);
    }

    @Test
    void roleIsCaseInsensitiveAndTrimmed() throws Exception {
        // given
        request.addHeader(HeaderAuthFilter.HEADER, "  receptionist ");

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_RECEPTIONIST");
    }

    @Test
    void unknownRole_staysUnauthenticatedButRequestContinues() throws Exception {
        // given
        request.addHeader(HeaderAuthFilter.HEADER, "JANITOR");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingHeader_staysUnauthenticatedButRequestContinues() throws Exception {
        // given a request without the X-Role header

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
