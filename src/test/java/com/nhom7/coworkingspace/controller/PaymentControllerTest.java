package com.nhom7.coworkingspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom7.coworkingspace.config.JwtProperties;
import com.nhom7.coworkingspace.controller.api.PaymentController;
import com.nhom7.coworkingspace.dto.response.PaymentResponse;
import com.nhom7.coworkingspace.security.CustomUserDetailsService;
import com.nhom7.coworkingspace.security.JwtAuthenticationFilter;
import com.nhom7.coworkingspace.security.JwtTokenProvider;
import com.nhom7.coworkingspace.service.BookingService;
import com.nhom7.coworkingspace.service.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({JwtAuthenticationFilter.class, JwtProperties.class})
@DisplayName("PaymentController - WebMvc & Security Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private MessageSource messageSource;

    @Test
    @WithMockUser(username = "user@test.com", roles = {"USER"})
    @DisplayName("Authenticated USER -> POST /api/payments/mock/bookings/{id}/pay returns 200 OK")
    void givenUserRole_whenPayBooking_thenReturn200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(10L)
                .bookingId(1L)
                .amount(new BigDecimal("150000.00"))
                .paymentMethod("MOCK")
                .status("COMPLETED")
                .paidAt(LocalDateTime.now())
                .transactionId("MOCK-1")
                .build();

        given(bookingService.payBooking(eq(1L), eq("user@test.com")))
                .willReturn(response);

        mockMvc.perform(post("/api/payments/mock/bookings/1/pay")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.bookingId").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Unauthenticated request -> POST /api/payments/mock/bookings/{id}/pay returns 401 Unauthorized")
    void givenUnauthenticated_whenPayBooking_thenReturn401() throws Exception {
        mockMvc.perform(post("/api/payments/mock/bookings/1/pay")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
