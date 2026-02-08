package com.wtfrepo.backend.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new TestController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .addFilters(new RequestIdFilter())
                    .build();

    @Test
    void shouldReturnStructuredValidationError() throws Exception {
        mockMvc.perform(
                        post("/test/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void shouldReturnStructuredApiException() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("conflict happened"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @RestController
    @Validated
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {}

        @RequestMapping("/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "conflict happened");
        }
    }

    record TestRequest(@NotBlank String name) {}
}
