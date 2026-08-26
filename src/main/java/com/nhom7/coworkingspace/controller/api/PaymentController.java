package com.nhom7.coworkingspace.controller.api;

import com.nhom7.coworkingspace.dto.response.ApiResponse;
import com.nhom7.coworkingspace.dto.response.PaymentResponse;
import com.nhom7.coworkingspace.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment API", description = "Endpoints for Payment Management")
@SecurityRequirement(name = "BearerAuth")
public class PaymentController {

    private final BookingService bookingService;
    private final MessageSource messageSource;

    /**
     * Process mock payment for an APPROVED booking.
     *
     * @param id booking ID to pay
     * @param authentication security context authentication
     * @return payment details wrapped in ApiResponse
     */
    @PostMapping("/mock/bookings/{id}/pay")
    @PreAuthorize("hasAnyRole('USER', 'HOST', 'MODERATOR', 'ADMIN')")
    @Operation(
            summary = "Mock Pay Booking",
            description = "Allows the booking owner to pay for a booking in APPROVED status."
    )
    public ResponseEntity<ApiResponse<PaymentResponse>> payBooking(
            @PathVariable Long id,
            Authentication authentication
    ) {
        PaymentResponse response = bookingService.payBooking(id, authentication.getName());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("booking.payment.success", null, locale);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), message, response));
    }
}
