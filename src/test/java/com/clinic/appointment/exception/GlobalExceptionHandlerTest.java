package com.clinic.appointment.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    record Body(@NotBlank(message = "name is required") String name) {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/not-found") void notFound() { throw new NotFoundException("Patient 9 does not exist"); }
        @GetMapping("/conflict") void conflict() { throw new ConflictException("slot taken"); }
        @GetMapping("/forbidden") void forbidden() { throw new ForbiddenException("not allowed"); }
        @GetMapping("/bad-request") void badRequest() { throw new BadRequestException("bad slot"); }
        @GetMapping("/boom") void boom() { throw new IllegalStateException("secret internal detail"); }
        @GetMapping("/number/{n}") String number(@PathVariable Long n) { return "ok"; }
        @GetMapping("/param") String param(@RequestParam Long id) { return "ok"; }
        @PostMapping("/validated") String validated(@Valid @RequestBody Body body) { return "ok"; }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void notFoundException_returns404WithMessage() throws Exception {
        // given the endpoint throws NotFoundException

        // when / then
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Patient 9 does not exist"));
    }

    @Test
    void conflictException_returns409() throws Exception {
        // given / when / then
        mvc.perform(get("/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("slot taken"));
    }

    @Test
    void forbiddenException_returns403() throws Exception {
        // given / when / then
        mvc.perform(get("/forbidden")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("not allowed"));
    }

    @Test
    void badRequestException_returns400() throws Exception {
        // given / when / then
        mvc.perform(get("/bad-request")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("bad slot"));
    }

    @Test
    void validationFailure_returns400WithFieldDetails() throws Exception {
        // given an invalid body
        String body = "{\"name\":\"\"}";

        // when / then
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("name: name is required"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        // given / when / then
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Malformed request")));
    }

    @Test
    void wrongTypeInPathOrQuery_returns400() throws Exception {
        // given / when / then
        mvc.perform(get("/number/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/param").param("id", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        // given / when / then
        mvc.perform(post("/not-found")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unsupportedContentType_returns415() throws Exception {
        // given / when / then
        mvc.perform(post("/validated").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unexpectedError_returns500WithoutLeakingInternalDetails() throws Exception {
        // given / when / then
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("secret"))));
    }
}
