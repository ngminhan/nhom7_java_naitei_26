package com.nhom7.coworkingspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom7.coworkingspace.config.JwtProperties;
import com.nhom7.coworkingspace.controller.api.BookingController;
import com.nhom7.coworkingspace.dto.request.BookingHistoryRequest;
import com.nhom7.coworkingspace.dto.request.BookingRequest;
import com.nhom7.coworkingspace.dto.response.BookingResponse;
import com.nhom7.coworkingspace.dto.response.PageResponse;
import com.nhom7.coworkingspace.enums.BookingStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import({JwtAuthenticationFilter.class, JwtProperties.class})
@DisplayName("BookingController - WebMvc & Security Tests")
class BookingControllerTest {

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
    @DisplayName("Authenticated USER -> POST /api/bookings returns 201 Created")
    void givenUserRole_whenCreateBooking_thenReturn201() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        LocalDateTime end = start.plusHours(2);

        BookingRequest request = BookingRequest.builder()
                .spaceId(10L)
                .startTime(start)
                .endTime(end)
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .userEmail("user@test.com")
                .spaceId(10L)
                .spaceName("Desk 101")
                .startTime(start)
                .endTime(end)
                .totalPrice(new BigDecimal("200000.00"))
                .status(BookingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();


        given(bookingService.createBooking(any(BookingRequest.class), eq("user@test.com")))
                .willReturn(response);

        mockMvc.perform(post("/api/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalPrice").value(200000.00));
    }

    @Test
    @DisplayName("Unauthenticated request -> POST /api/bookings returns 401 Unauthorized")
    void givenUnauthenticated_whenCreateBooking_thenReturn401() throws Exception {
        BookingRequest request = BookingRequest.builder()
                .spaceId(10L)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();

        mockMvc.perform(post("/api/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"USER"})
    @DisplayName("Authenticated USER -> GET /api/bookings/my-history returns 200 OK")
    void givenUserRole_whenGetMyBookingHistory_thenReturn200() throws Exception {
        BookingResponse booking = BookingResponse.builder()
                .id(1L)
                .userEmail("user@test.com")
                .spaceId(10L)
                .status(BookingStatus.APPROVED)
                .build();

        PageResponse<BookingResponse> pageResponse = PageResponse.fromPage(
                new org.springframework.data.domain.PageImpl<>(List.of(booking)));

        given(bookingService.getMyBookingHistory(any(com.nhom7.coworkingspace.dto.request.BookingSearchRequest.class), eq("user@test.com")))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/bookings/my-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("Unauthenticated request -> GET /api/bookings/my-history returns 401 Unauthorized")
    void givenUnauthenticated_whenGetMyBookingHistory_thenReturn401() throws Exception {
        mockMvc.perform(get("/api/bookings/my-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"USER"})
    @DisplayName("Authenticated USER -> PUT /api/bookings/{id}/cancel returns 200 OK")
    void givenUserRole_whenCancelBooking_thenReturn200() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .userEmail("user@test.com")
                .spaceId(10L)
                .status(BookingStatus.CANCELLED)
                .build();

        given(bookingService.cancelBooking(eq(1L), eq("user@test.com")))
                .willReturn(response);

        mockMvc.perform(put("/api/bookings/1/cancel")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
