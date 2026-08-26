package com.nhom7.coworkingspace.service;

import com.nhom7.coworkingspace.dto.request.BookingHistoryRequest;
import com.nhom7.coworkingspace.dto.request.BookingRequest;
import com.nhom7.coworkingspace.dto.request.BookingSearchRequest;
import com.nhom7.coworkingspace.dto.response.BookingResponse;
import com.nhom7.coworkingspace.dto.response.PageResponse;
import com.nhom7.coworkingspace.dto.response.PaymentResponse;
import com.nhom7.coworkingspace.entity.Booking;
import com.nhom7.coworkingspace.entity.Payment;
import com.nhom7.coworkingspace.entity.Space;
import com.nhom7.coworkingspace.entity.User;
import com.nhom7.coworkingspace.enums.SpaceStatus;
import com.nhom7.coworkingspace.enums.BookingStatus;
import com.nhom7.coworkingspace.exception.AppException;
import com.nhom7.coworkingspace.exception.BookingNotFoundException;
import com.nhom7.coworkingspace.mapper.BookingMapper;
import com.nhom7.coworkingspace.mapper.PaymentMapper;
import com.nhom7.coworkingspace.repository.BookingRepository;
import com.nhom7.coworkingspace.repository.PaymentRepository;
import com.nhom7.coworkingspace.repository.SpaceRepository;
import com.nhom7.coworkingspace.repository.UserRepository;
import com.nhom7.coworkingspace.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingServiceImpl Unit Tests")
class BookingServiceImplTest {

        @Mock
        private BookingRepository bookingRepository;

        @Mock
        private SpaceRepository spaceRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private BookingMapper bookingMapper;

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private PaymentMapper paymentMapper;

        @Mock
        private EmailService emailService;

        @Mock
        private EmailTemplateService emailTemplateService;

        @Mock
        private MessageSource messageSource;

        private Clock clock;
        private BookingServiceImpl bookingService;

        @BeforeEach
        void setUp() {
                Instant fixedInstant = Instant.parse("2026-08-20T00:00:00Z");
                clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
                bookingService = new BookingServiceImpl(
                                bookingRepository,
                                spaceRepository,
                                userRepository,
                                paymentRepository,
                                bookingMapper,
                                paymentMapper,
                                emailService,
                                emailTemplateService,
                                messageSource,
                                clock);
        }

        @Nested
        @DisplayName("Create Booking Tests")
        class CreateBookingTests {

                @Test
                @DisplayName("Should create booking successfully with valid inputs")
                void createBooking_Success() {
                        String email = "customer@coworking.test";
                        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(9).withMinute(0);
                        LocalDateTime end = start.plusHours(2);

                        User user = User.builder().id(1L).email(email).build();
                        Space space = Space.builder()
                                        .id(10L)
                                        .name("Desk 1")
                                        .status(SpaceStatus.ACTIVE)
                                        .price(new BigDecimal("100000.00"))
                                        .priceUnit("HOUR")
                                        .build();

                        BookingRequest request = BookingRequest.builder()
                                        .spaceId(10L)
                                        .startTime(start)
                                        .endTime(end)
                                        .build();

                        Booking savedBooking = Booking.builder()
                                        .id(100L)
                                        .user(user)
                                        .space(space)
                                        .startTime(start)
                                        .endTime(end)
                                        .totalPrice(new BigDecimal("200000.00"))
                                        .status(BookingStatus.PENDING)
                                        .build();

                        BookingResponse expectedResponse = BookingResponse.builder()
                                        .id(100L)
                                        .userEmail(email)
                                        .spaceId(10L)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("200000.00"))
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(spaceRepository.findByIdForUpdate(10L)).willReturn(Optional.of(space));
                        given(bookingRepository.existsActiveOverlap(10L, start, end)).willReturn(false);
                        given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);
                        given(bookingMapper.toBookingResponse(savedBooking)).willReturn(expectedResponse);

                        BookingResponse response = bookingService.createBooking(request, email);

                        assertThat(response).isNotNull();
                        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
                        assertThat(response.getTotalPrice()).isEqualByComparingTo("200000.00");
                        verify(bookingRepository).save(any(Booking.class));
                }

                @Test
                @DisplayName("Should throw AppException when active booking overlap exists (PENDING/APPROVED)")
                void createBooking_OverlapError() {
                        String email = "customer@coworking.test";
                        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(9).withMinute(0);
                        LocalDateTime end = start.plusHours(2);

                        User user = User.builder().id(1L).email(email).build();
                        Space space = Space.builder().id(10L).status(SpaceStatus.ACTIVE).build();

                        BookingRequest request = BookingRequest.builder()
                                        .spaceId(10L)
                                        .startTime(start)
                                        .endTime(end)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(spaceRepository.findByIdForUpdate(10L)).willReturn(Optional.of(space));
                        given(bookingRepository.existsActiveOverlap(10L, start, end)).willReturn(true);

                        assertThatThrownBy(() -> bookingService.createBooking(request, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.overlap.error")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw AppException when the Space is INACTIVE (e.g. its venue was blocked/deleted)")
                void createBooking_InactiveSpace_NotAvailable() {
                        String email = "customer@coworking.test";
                        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
                        LocalDateTime end = start.plusHours(2);

                        User user = User.builder().id(1L).email(email).build();
                        Space space = Space.builder().id(10L).status(SpaceStatus.INACTIVE).build();

                        BookingRequest request = BookingRequest.builder()
                                        .spaceId(10L)
                                        .startTime(start)
                                        .endTime(end)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(spaceRepository.findById(10L)).willReturn(Optional.of(space));

                        assertThatThrownBy(() -> bookingService.createBooking(request, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("space.not.available")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw AppException when startTime is after endTime")
                void createBooking_InvalidTimeRange() {
                        String email = "customer@coworking.test";
                        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(11).withMinute(0);
                        LocalDateTime end = start.minusHours(2);

                        BookingRequest request = BookingRequest.builder()
                                        .spaceId(10L)
                                        .startTime(start)
                                        .endTime(end)
                                        .build();

                        assertThatThrownBy(() -> bookingService.createBooking(request, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.time.invalid")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw AppException when startTime is in the past")
                void createBooking_PastTime() {
                        String email = "customer@coworking.test";
                        LocalDateTime start = LocalDateTime.now(clock).minusDays(1);
                        LocalDateTime end = start.plusHours(2);

                        BookingRequest request = BookingRequest.builder()
                                        .spaceId(10L)
                                        .startTime(start)
                                        .endTime(end)
                                        .build();

                        assertThatThrownBy(() -> bookingService.createBooking(request, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.time.past")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw AppException when booking outside space operating hours")
                void createBooking_OutsideOperatingHours() {
                        String email = "customer@coworking.test";
                        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(7).withMinute(0);
                        LocalDateTime end = start.plusHours(2);

                        User user = User.builder().id(1L).email(email).build();
                        Space space = Space.builder()
                                        .id(10L)
                                        .status(SpaceStatus.ACTIVE)
                                        .openTime(LocalTime.of(8, 0)) // Opens at 08:00
                                        .closeTime(LocalTime.of(20, 0))
                                        .build();

                        BookingRequest request = BookingRequest.builder()
                                        .spaceId(10L)
                                        .startTime(start)
                                        .endTime(end)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(spaceRepository.findByIdForUpdate(10L)).willReturn(Optional.of(space));

                        assertThatThrownBy(() -> bookingService.createBooking(request, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.operating.hours.invalid")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }
        }

        @Nested
        @DisplayName("Search Bookings Tests")
        class SearchBookingsTests {

                @Test
                @DisplayName("searchBookings should return paged booking responses")
                void searchBookings_Success() {
                        BookingSearchRequest request = BookingSearchRequest.builder()
                                        .keyword("test")
                                        .status(BookingStatus.PENDING)
                                        .page(0)
                                        .size(10)
                                        .sortBy("id")
                                        .sortDir("DESC")
                                        .build();

                        Booking booking = Booking.builder()
                                        .id(1L)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("100000.00"))
                                        .build();

                        BookingResponse bookingResponse = BookingResponse.builder()
                                        .id(1L)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("100000.00"))
                                        .build();

                        Page<Booking> bookingPage = new PageImpl<>(List.of(booking));

                        given(bookingRepository.findAll(ArgumentMatchers.<Specification<Booking>>any(), any(Pageable.class)))
                                        .willReturn(bookingPage);
                        given(bookingMapper.toBookingResponse(booking)).willReturn(bookingResponse);

                        PageResponse<BookingResponse> result = bookingService.searchBookings(request);

                        assertThat(result).isNotNull();
                        assertThat(result.getContent()).hasSize(1);
                        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
                        assertThat(result.getContent().get(0).getStatus()).isEqualTo(BookingStatus.PENDING);
                }

                @Test
                @DisplayName("searchBookings with null request should use default values and succeed")
                void searchBookings_NullRequest_Success() {
                        Booking booking = Booking.builder()
                                        .id(1L)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("100000.00"))
                                        .build();

                        BookingResponse bookingResponse = BookingResponse.builder()
                                        .id(1L)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("100000.00"))
                                        .build();

                        Page<Booking> bookingPage = new PageImpl<>(List.of(booking));

                        given(bookingRepository.findAll(ArgumentMatchers.<Specification<Booking>>any(), any(Pageable.class)))
                                        .willReturn(bookingPage);
                        given(bookingMapper.toBookingResponse(booking)).willReturn(bookingResponse);

                        PageResponse<BookingResponse> result = bookingService.searchBookings(null);

                        assertThat(result).isNotNull();
                        assertThat(result.getContent()).hasSize(1);
                }

                @Test
                @DisplayName("searchBookings with fromDate after toDate should throw AppException")
                void searchBookings_InvalidDateRange_ThrowsAppException() {
                        BookingSearchRequest request = BookingSearchRequest.builder()
                                        .fromDate(LocalDateTime.of(2026, 8, 30, 0, 0))
                                        .toDate(LocalDateTime.of(2026, 8, 1, 0, 0))
                                        .build();

                        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.searchBookings(request))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.time.invalid");
                }
        }


        @Nested
        @DisplayName("Get Booking By ID Tests")
        class GetBookingByIdTests {

                @Test
                @DisplayName("Should return BookingResponse when booking exists")
                void getBookingById_Success() {
                        Long bookingId = 1L;
                        Booking booking = Booking.builder()
                                        .id(bookingId)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("100000.00"))
                                        .build();

                        BookingResponse expectedResponse = BookingResponse.builder()
                                        .id(bookingId)
                                        .status(BookingStatus.PENDING)
                                        .totalPrice(new BigDecimal("100000.00"))
                                        .build();

                        given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking));
                        given(bookingMapper.toBookingResponse(booking)).willReturn(expectedResponse);

                        BookingResponse response = bookingService.getBookingById(bookingId);

                        assertThat(response).isNotNull();
                        assertThat(response.getId()).isEqualTo(bookingId);
                        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
                }

                @Test
                @DisplayName("Should throw BookingNotFoundException when booking does not exist")
                void getBookingById_NotFound() {
                        Long bookingId = 999L;
                        given(bookingRepository.findById(bookingId)).willReturn(Optional.empty());

                        assertThatThrownBy(() -> bookingService.getBookingById(bookingId))
                                        .isInstanceOf(com.nhom7.coworkingspace.exception.BookingNotFoundException.class)
                                        .hasMessage("booking.not.found");
                }
        }

        @Nested
        @DisplayName("Price Calculation Tests")
        class PriceCalculationTests {


                @Test
                @DisplayName("Calculate price by HOUR: 2 hours at 100,000 = 200,000.00")
                void calculatePrice_Hour() {
                        Space space = Space.builder()
                                        .price(new BigDecimal("100000.00"))
                                        .priceUnit("HOUR")
                                        .build();

                        LocalDateTime start = LocalDateTime.of(2026, 8, 25, 9, 0);
                        LocalDateTime end = LocalDateTime.of(2026, 8, 25, 11, 0);

                        BigDecimal totalPrice = bookingService.calculateTotalPrice(space, start, end);

                        assertThat(totalPrice).isEqualByComparingTo("200000.00");
                }

                @Test
                @DisplayName("Calculate price by HOUR: 2.5 hours (150 mins) at 100,000 = 250,000.00")
                void calculatePrice_HourFractional() {
                        Space space = Space.builder()
                                        .price(new BigDecimal("100000.00"))
                                        .priceUnit("HOUR")
                                        .build();

                        LocalDateTime start = LocalDateTime.of(2026, 8, 25, 9, 0);
                        LocalDateTime end = LocalDateTime.of(2026, 8, 25, 11, 30);

                        BigDecimal totalPrice = bookingService.calculateTotalPrice(space, start, end);

                        assertThat(totalPrice).isEqualByComparingTo("250000.00");
                }

                @Test
                @DisplayName("Calculate price by DAY: 1.5 days at 500,000 -> 2 days = 1,000,000.00")
                void calculatePrice_Day() {
                        Space space = Space.builder()
                                        .price(new BigDecimal("500000.00"))
                                        .priceUnit("DAY")
                                        .build();

                        LocalDateTime start = LocalDateTime.of(2026, 8, 25, 9, 0);
                        LocalDateTime end = LocalDateTime.of(2026, 8, 26, 15, 0);

                        BigDecimal totalPrice = bookingService.calculateTotalPrice(space, start, end);

                        assertThat(totalPrice).isEqualByComparingTo("1000000.00");
                }

                @Test
                @DisplayName("Calculate price by MONTH: 45 days at 3,000,000 -> 2 months = 6,000,000.00")
                void calculatePrice_Month() {
                        Space space = Space.builder()
                                        .price(new BigDecimal("3000000.00"))
                                        .priceUnit("MONTH")
                                        .build();

                        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 9, 0);
                        LocalDateTime end = LocalDateTime.of(2026, 9, 15, 9, 0);

                        BigDecimal totalPrice = bookingService.calculateTotalPrice(space, start, end);

                        assertThat(totalPrice).isEqualByComparingTo("6000000.00");
                }
        }


        @Test
        @DisplayName("changeStatus should persist and send booking status email")
        void changeStatusShouldPersistAndSendBookingStatusEmail() {
                User user = User.builder()
                                .name("Nguyen Van A")
                                .email("customer@coworking.test")
                                .language("vi")
                                .build();
                Space space = Space.builder().name("Meeting Room A").build();
                Booking booking = Booking.builder()
                                .id(42L)
                                .user(user)
                                .space(space)
                                .startTime(LocalDateTime.of(2026, 8, 22, 9, 0))
                                .endTime(LocalDateTime.of(2026, 8, 22, 11, 0))
                                .totalPrice(new BigDecimal("250000.00"))
                                .status(BookingStatus.PENDING)
                                .build();
                given(bookingRepository.findById(42L)).willReturn(Optional.of(booking));
                given(bookingRepository.saveAndFlush(booking)).willReturn(booking);
                Locale locale = Locale.forLanguageTag("vi");
                given(emailTemplateService.renderBookingStatusChanged(booking, "PENDING", locale))
                                .willReturn("<p>Booking updated</p>");
                given(messageSource.getMessage(
                                "email.booking.status.subject", new Object[] { 42L }, locale))
                                .willReturn("Trạng thái đặt chỗ #42 đã được cập nhật");

                Booking updated = bookingService.changeStatus(42L, " approved ");

                assertThat(updated.getStatus()).isEqualTo(BookingStatus.APPROVED);
                verify(bookingRepository).saveAndFlush(booking);
                verify(emailTemplateService).renderBookingStatusChanged(booking, "PENDING", locale);
                verify(emailService).sendHtmlEmail(
                                "customer@coworking.test",
                                "Trạng thái đặt chỗ #42 đã được cập nhật",
                                "<p>Booking updated</p>");
        }

        @Test
        @DisplayName("changeStatus should not persist or send email when status is unchanged")
        void changeStatusShouldNotPersistOrSendEmailWhenStatusIsUnchanged() {
                Booking booking = Booking.builder().id(42L).status(BookingStatus.APPROVED).build();
                given(bookingRepository.findById(42L)).willReturn(Optional.of(booking));

                Booking unchanged = bookingService.changeStatus(42L, " approved ");

                assertThat(unchanged).isSameAs(booking);
                verifyNoInteractions(emailService, emailTemplateService);
                verify(bookingRepository, org.mockito.Mockito.never()).saveAndFlush(booking);
        }

        @Test
        @DisplayName("changeStatus should persist and send email when changing status to COMPLETED")
        void changeStatusShouldUpdateStatusToCompleted() {
                User user = User.builder().name("Nguyen Van B").email("user2@coworking.test").language("en").build();
                Space space = Space.builder().name("Desk 1").build();
                Booking booking = Booking.builder()
                                .id(43L)
                                .user(user)
                                .space(space)
                                .status(BookingStatus.CONFIRMED)
                                .build();
                given(bookingRepository.findById(43L)).willReturn(Optional.of(booking));
                given(bookingRepository.saveAndFlush(booking)).willReturn(booking);
                Locale locale = Locale.ENGLISH;
                given(emailTemplateService.renderBookingStatusChanged(booking, "CONFIRMED", locale))
                                .willReturn("<p>Completed</p>");
                given(messageSource.getMessage("email.booking.status.subject", new Object[] { 43L }, locale))
                                .willReturn("Booking #43 status updated");

                Booking updated = bookingService.changeStatus(43L, "COMPLETED");

                assertThat(updated.getStatus()).isEqualTo(BookingStatus.COMPLETED);
                verify(bookingRepository).saveAndFlush(booking);
                verify(emailService).sendHtmlEmail("user2@coworking.test", "Booking #43 status updated", "<p>Completed</p>");
        }

        @Test
        @DisplayName("changeStatus should persist and send email when changing status to REJECTED")
        void changeStatusShouldUpdateStatusToRejected() {
                User user = User.builder().name("Nguyen Van C").email("user3@coworking.test").language("en").build();
                Space space = Space.builder().name("Desk 2").build();
                Booking booking = Booking.builder()
                                .id(44L)
                                .user(user)
                                .space(space)
                                .status(BookingStatus.PENDING)
                                .build();
                given(bookingRepository.findById(44L)).willReturn(Optional.of(booking));
                given(bookingRepository.saveAndFlush(booking)).willReturn(booking);
                Locale locale = Locale.ENGLISH;
                given(emailTemplateService.renderBookingStatusChanged(booking, "PENDING", locale))
                                .willReturn("<p>Rejected</p>");
                given(messageSource.getMessage("email.booking.status.subject", new Object[] { 44L }, locale))
                                .willReturn("Booking #44 status updated");

                Booking updated = bookingService.changeStatus(44L, "REJECTED");

                assertThat(updated.getStatus()).isEqualTo(BookingStatus.REJECTED);
                verify(bookingRepository).saveAndFlush(booking);
                verify(emailService).sendHtmlEmail("user3@coworking.test", "Booking #44 status updated", "<p>Rejected</p>");
        }

        @Nested
        @DisplayName("cancelBooking Tests")
        class CancelBookingTests {

                @Test
                @DisplayName("Should cancel PENDING booking successfully when requested by owner")
                void cancelBooking_Success_PendingStatus() {
                        String email = "owner@test.com";
                        User user = User.builder().id(1L).email(email).build();
                        Space space = Space.builder().id(10L).name("Space A").build();
                        Booking booking = Booking.builder()
                                        .id(100L)
                                        .user(user)
                                        .space(space)
                                        .status(BookingStatus.PENDING)
                                        .build();

                        Booking cancelledBooking = Booking.builder()
                                        .id(100L)
                                        .user(user)
                                        .space(space)
                                        .status(BookingStatus.CANCELLED)
                                        .build();

                        BookingResponse expectedResponse = BookingResponse.builder()
                                        .id(100L)
                                        .userEmail(email)
                                        .spaceId(10L)
                                        .status(BookingStatus.CANCELLED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(100L)).willReturn(Optional.of(booking));
                        given(bookingRepository.save(booking)).willReturn(cancelledBooking);
                        given(bookingMapper.toBookingResponse(cancelledBooking)).willReturn(expectedResponse);

                        BookingResponse response = bookingService.cancelBooking(100L, email);

                        assertThat(response).isNotNull();
                        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
                        verify(bookingRepository).save(booking);
                        verifyNoInteractions(emailService, emailTemplateService);
                }

                @Test
                @DisplayName("Should cancel APPROVED booking successfully when requested by owner")
                void cancelBooking_Success_ApprovedStatus() {
                        String email = "owner@test.com";
                        User user = User.builder().id(1L).email(email).build();
                        Space space = Space.builder().id(10L).name("Space A").build();
                        Booking booking = Booking.builder()
                                        .id(101L)
                                        .user(user)
                                        .space(space)
                                        .status(BookingStatus.APPROVED)
                                        .build();

                        Booking cancelledBooking = Booking.builder()
                                        .id(101L)
                                        .user(user)
                                        .space(space)
                                        .status(BookingStatus.CANCELLED)
                                        .build();

                        BookingResponse expectedResponse = BookingResponse.builder()
                                        .id(101L)
                                        .userEmail(email)
                                        .spaceId(10L)
                                        .status(BookingStatus.CANCELLED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(101L)).willReturn(Optional.of(booking));
                        given(bookingRepository.save(booking)).willReturn(cancelledBooking);
                        given(bookingMapper.toBookingResponse(cancelledBooking)).willReturn(expectedResponse);

                        BookingResponse response = bookingService.cancelBooking(101L, email);

                        assertThat(response).isNotNull();
                        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
                        verify(bookingRepository).save(booking);
                }

                @Test
                @DisplayName("Should throw 403 Forbidden when user is not the owner of the booking")
                void cancelBooking_Forbidden_NotOwner() {
                        String currentUserEmail = "userB@test.com";
                        User owner = User.builder().id(1L).email("owner@test.com").build();
                        User currentUser = User.builder().id(2L).email(currentUserEmail).build();
                        Booking booking = Booking.builder()
                                        .id(102L)
                                        .user(owner)
                                        .status(BookingStatus.PENDING)
                                        .build();

                        given(userRepository.findByEmail(currentUserEmail)).willReturn(Optional.of(currentUser));
                        given(bookingRepository.findById(102L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(102L, currentUserEmail))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.cannot.cancel.not.owner")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.FORBIDDEN);
                }

                @Test
                @DisplayName("Should throw 400 Bad Request when booking status is CONFIRMED (PAID)")
                void cancelBooking_BadRequest_ConfirmedPaid() {
                        String email = "owner@test.com";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(103L)
                                        .user(user)
                                        .status(BookingStatus.CONFIRMED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(103L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(103L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.cannot.cancel.invalid.status")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw 400 Bad Request when booking status is already CANCELLED")
                void cancelBooking_BadRequest_AlreadyCancelled() {
                        String email = "owner@test.com";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(104L)
                                        .user(user)
                                        .status(BookingStatus.CANCELLED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(104L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(104L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.cannot.cancel.invalid.status")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw 400 Bad Request when booking status is COMPLETED")
                void cancelBooking_BadRequest_CompletedStatus() {
                        String email = "owner@test.com";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(105L)
                                        .user(user)
                                        .status(BookingStatus.COMPLETED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(105L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(105L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.cannot.cancel.invalid.status")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw 400 Bad Request when booking status is REJECTED")
                void cancelBooking_BadRequest_RejectedStatus() {
                        String email = "owner@test.com";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(106L)
                                        .user(user)
                                        .status(BookingStatus.REJECTED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(106L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(106L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.cannot.cancel.invalid.status")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }
        }

        @Nested
        @DisplayName("Pay Booking Tests")
        class PayBookingTests {

                @Test
                @DisplayName("Should successfully pay for an APPROVED booking and change status to PAID")
                void payBooking_Success() {
                        String email = "customer@coworking.test";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(200L)
                                        .user(user)
                                        .totalPrice(new BigDecimal("150000.00"))
                                        .status(BookingStatus.APPROVED)
                                        .build();

                        Payment payment = Payment.builder()
                                        .id(10L)
                                        .booking(booking)
                                        .amount(new BigDecimal("150000.00"))
                                        .paymentMethod("MOCK")
                                        .status("COMPLETED")
                                        .paidAt(LocalDateTime.now(clock))
                                        .transactionId("MOCK-200")
                                        .build();

                        PaymentResponse expectedResponse = PaymentResponse.builder()
                                        .id(10L)
                                        .bookingId(200L)
                                        .amount(new BigDecimal("150000.00"))
                                        .paymentMethod("MOCK")
                                        .status("COMPLETED")
                                        .paidAt(LocalDateTime.now(clock))
                                        .transactionId("MOCK-200")
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(200L)).willReturn(Optional.of(booking));
                        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));
                        given(paymentRepository.save(any(Payment.class))).willReturn(payment);
                        given(paymentMapper.toPaymentResponse(payment)).willReturn(expectedResponse);

                        PaymentResponse result = bookingService.payBooking(200L, email);

                        assertThat(result).isNotNull();
                        assertThat(result.getBookingId()).isEqualTo(200L);
                        assertThat(result.getStatus()).isEqualTo("COMPLETED");
                        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);

                        verify(bookingRepository).save(booking);
                        verify(paymentRepository).save(any(Payment.class));
                }

                @Test
                @DisplayName("Should throw BookingNotFoundException when booking does not exist")
                void payBooking_NotFound_ThrowsException() {
                        String email = "customer@coworking.test";
                        User user = User.builder().id(1L).email(email).build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(999L)).willReturn(Optional.empty());

                        assertThatThrownBy(() -> bookingService.payBooking(999L, email))
                                        .isInstanceOf(BookingNotFoundException.class);
                }

                @Test
                @DisplayName("Should throw 403 Forbidden when current user is not the booking owner")
                void payBooking_NotOwner_ThrowsForbidden() {
                        String email = "hacker@coworking.test";
                        User currentOwner = User.builder().id(1L).email("owner@coworking.test").build();
                        User callerUser = User.builder().id(2L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(200L)
                                        .user(currentOwner)
                                        .status(BookingStatus.APPROVED)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(callerUser));
                        given(bookingRepository.findById(200L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.payBooking(200L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.payment.not.owner")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.FORBIDDEN);
                }

                @Test
                @DisplayName("Should throw 400 Bad Request when booking is already PAID")
                void payBooking_AlreadyPaid_ThrowsBadRequest() {
                        String email = "owner@coworking.test";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(200L)
                                        .user(user)
                                        .status(BookingStatus.PAID)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(200L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.payBooking(200L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.already.paid")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }

                @Test
                @DisplayName("Should throw 400 Bad Request when booking is not in APPROVED status")
                void payBooking_NotApproved_ThrowsBadRequest() {
                        String email = "owner@coworking.test";
                        User user = User.builder().id(1L).email(email).build();
                        Booking booking = Booking.builder()
                                        .id(200L)
                                        .user(user)
                                        .status(BookingStatus.PENDING)
                                        .build();

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findById(200L)).willReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.payBooking(200L, email))
                                        .isInstanceOf(AppException.class)
                                        .hasMessage("booking.payment.not.approved")
                                        .extracting("status")
                                        .isEqualTo(HttpStatus.BAD_REQUEST);
                }
        }

        @Nested
        @DisplayName("Get My Booking History Tests")
        class GetMyBookingHistoryTests {

                @Test
                @DisplayName("Should fetch my booking history and force current userId in filter")
                void getMyBookingHistory_Success() {
                        String email = "user@test.com";
                        User user = User.builder().id(5L).email(email).build();
                        BookingSearchRequest request = BookingSearchRequest.builder().build();

                        Booking booking = Booking.builder().id(100L).user(user).build();
                        BookingResponse bookingResponse = BookingResponse.builder().id(100L).userId(5L).build();
                        Page<Booking> page = new PageImpl<>(List.of(booking));

                        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
                        given(bookingRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);
                        given(bookingMapper.toBookingResponse(booking)).willReturn(bookingResponse);

                        PageResponse<BookingResponse> result = bookingService.getMyBookingHistory(request, email);

                        assertThat(result).isNotNull();
                        assertThat(result.getContent()).hasSize(1);
                        assertThat(result.getContent().get(0).getUserId()).isEqualTo(5L);
                        assertThat(request.getUserId()).isEqualTo(5L);
                }
        }
}
