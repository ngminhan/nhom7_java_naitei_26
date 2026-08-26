package com.nhom7.coworkingspace.controller.api;

import com.nhom7.coworkingspace.dto.request.BookingHistoryRequest;
import com.nhom7.coworkingspace.dto.request.BookingRequest;
import com.nhom7.coworkingspace.dto.request.BookingSearchRequest;
import com.nhom7.coworkingspace.dto.response.ApiResponse;
import com.nhom7.coworkingspace.dto.response.BookingResponse;
import com.nhom7.coworkingspace.dto.response.PageResponse;
import com.nhom7.coworkingspace.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking API", description = "Endpoints for Co-working Space booking management")
@SecurityRequirement(name = "BearerAuth")
public class BookingController {


    private final BookingService bookingService;
    private final MessageSource messageSource;

    /**
     * Request a co-working space booking.
     *
     * <p>Requires authenticated user.</p>
     *
     * @param request booking request parameters
     * @param authentication security context authentication
     * @return created booking details wrapped in ApiResponse
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'HOST', 'MODERATOR', 'ADMIN')")
    @Operation(
            summary = "Request Co-working Space Booking",
            description = "Allows authenticated users to request space bookings with status PENDING, validating against active overlapping bookings."
    )
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody BookingRequest request,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.createBooking(request, authentication.getName());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("booking.created", null, locale);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), message, response));
    }

    /**
     * Get the booking history of the currently authenticated user.
     *
     * <p>Requires authenticated user.</p>
     *
     * @param request pagination and sorting parameters
     * @param authentication security context authentication
     * @return paginated list of the current user's bookings wrapped in ApiResponse
     */
    /**
     * Get booking history for current authenticated user.
     *
     * @param request search and filter criteria
     * @param authentication security context authentication
     * @return paginated booking history wrapped in ApiResponse
     */
    @GetMapping("/my-history")
    @PreAuthorize("hasAnyRole('USER', 'HOST', 'MODERATOR', 'ADMIN')")
    @Operation(
            summary = "Get My Booking History",
            description = "Retrieves paginated booking history for the current authenticated user."
    )
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getMyBookingHistory(
            @org.springdoc.core.annotations.ParameterObject @ModelAttribute BookingSearchRequest request,
            Authentication authentication
    ) {
        PageResponse<BookingResponse> response = bookingService.getMyBookingHistory(request, authentication.getName());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("booking.my_history.fetched", null, locale);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), message, response));
    }

    /**
     * Cancel a co-working space booking.
     *
     * <p>Requires authenticated user who owns the booking in PENDING or APPROVED status.</p>
     *
     * @param id booking ID to cancel
     * @param authentication security context authentication
     * @return cancelled booking details wrapped in ApiResponse
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'HOST', 'MODERATOR', 'ADMIN')")
    @Operation(
            summary = "Cancel Co-working Space Booking",
            description = "Allows the booking owner to cancel a booking in PENDING or APPROVED status."
    )
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.cancelBooking(id, authentication.getName());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("booking.cancelled", null, locale);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), message, response));
    }
}
