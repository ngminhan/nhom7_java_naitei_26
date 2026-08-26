package com.nhom7.coworkingspace.service.impl;

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
import com.nhom7.coworkingspace.enums.BookingStatus;
import com.nhom7.coworkingspace.enums.PriceUnit;
import com.nhom7.coworkingspace.enums.SpaceStatus;
import com.nhom7.coworkingspace.exception.AppException;
import com.nhom7.coworkingspace.exception.BookingNotFoundException;
import com.nhom7.coworkingspace.mapper.BookingMapper;
import com.nhom7.coworkingspace.mapper.PaymentMapper;
import com.nhom7.coworkingspace.repository.BookingRepository;
import com.nhom7.coworkingspace.repository.PaymentRepository;
import com.nhom7.coworkingspace.repository.SpaceRepository;
import com.nhom7.coworkingspace.repository.UserRepository;
import com.nhom7.coworkingspace.service.BookingService;
import com.nhom7.coworkingspace.service.EmailService;
import com.nhom7.coworkingspace.service.EmailTemplateService;
import com.nhom7.coworkingspace.specification.BookingSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "startTime", "endTime", "status", "totalPrice", "createdAt");

    private final BookingRepository bookingRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BookingMapper bookingMapper;
    private final PaymentMapper paymentMapper;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final MessageSource messageSource;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchBookings(BookingSearchRequest request) {
        if (request == null) {
            request = BookingSearchRequest.builder().build();
        }

        if (request.getFromDate() != null && request.getToDate() != null
                && request.getFromDate().isAfter(request.getToDate())) {
            throw new AppException("booking.time.invalid", HttpStatus.BAD_REQUEST);
        }

        log.debug("[BookingService] Searching bookings with params: keyword={}, status={}, userId={}, spaceId={}",
                request.getKeyword(), request.getStatus(), request.getUserId(), request.getSpaceId());

        Sort.Direction direction = "ASC".equalsIgnoreCase(request.getSortDir())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String rawSortBy = (request.getSortBy() != null) ? request.getSortBy().trim() : "id";
        String sortBy = ALLOWED_SORT_FIELDS.contains(rawSortBy) ? rawSortBy : "id";

        int page = Math.max(0, request.getPage());
        int size = Math.min(Math.max(1, request.getSize()), 100);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Specification<Booking> spec = BookingSpecification.buildSearchSpecification(request);
        Page<Booking> bookingPage = bookingRepository.findAll(spec, pageable);

        Page<BookingResponse> dtoPage = bookingPage.map(bookingMapper::toBookingResponse);
        return PageResponse.fromPage(dtoPage);
    }


    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId) {
        log.debug("[BookingService] Getting booking details for id={}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return bookingMapper.toBookingResponse(booking);
    }


    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request, String userEmail) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new AppException("booking.time.required", HttpStatus.BAD_REQUEST);
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new AppException("booking.time.invalid", HttpStatus.BAD_REQUEST);
        }
        if (request.getStartTime().isBefore(LocalDateTime.now(clock))) {
            throw new AppException("booking.time.past", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException("user.not.found", HttpStatus.NOT_FOUND));

        Space space = spaceRepository.findByIdForUpdate(request.getSpaceId())
                .or(() -> spaceRepository.findById(request.getSpaceId()))
                .orElseThrow(() -> new AppException("space.not.found", HttpStatus.NOT_FOUND));

        if (space.getStatus() != null && space.getStatus() != SpaceStatus.ACTIVE) {
            throw new AppException("space.not.available", HttpStatus.BAD_REQUEST);
        }

        // Operating hours validation if openTime & closeTime are defined
        if (space.getOpenTime() != null && space.getCloseTime() != null) {
            LocalTime startLocalTime = request.getStartTime().toLocalTime();
            LocalTime endLocalTime = request.getEndTime().toLocalTime();

            if (startLocalTime.isBefore(space.getOpenTime()) || endLocalTime.isAfter(space.getCloseTime())) {
                throw new AppException("booking.operating.hours.invalid", HttpStatus.BAD_REQUEST);
            }
        }

        // Check for active overlapping bookings (PENDING, APPROVED, etc.)
        boolean hasOverlap = bookingRepository.existsActiveOverlap(
                space.getId(), request.getStartTime(), request.getEndTime());
        if (hasOverlap) {
            throw new AppException("booking.overlap.error", HttpStatus.BAD_REQUEST);
        }

        BigDecimal totalPrice = calculateTotalPrice(space, request.getStartTime(), request.getEndTime());

        Booking booking = Booking.builder()
                .user(user)
                .space(space)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .createdAt(LocalDateTime.now(clock))
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        return bookingMapper.toBookingResponse(savedBooking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException("user.not.found", HttpStatus.NOT_FOUND));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!booking.getUser().getId().equals(user.getId())) {
            throw new AppException("booking.cannot.cancel.not.owner", HttpStatus.FORBIDDEN);
        }

        BookingStatus currentStatus = booking.getStatus();
        if (currentStatus == null ||
                (currentStatus != BookingStatus.PENDING
                        && currentStatus != BookingStatus.APPROVED)) {
            throw new AppException("booking.cannot.cancel.invalid.status", HttpStatus.BAD_REQUEST);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        return bookingMapper.toBookingResponse(savedBooking);
    }

    @Override
    @Transactional
    public PaymentResponse payBooking(Long bookingId, String userEmail) {
        log.debug("[BookingService] Processing payment for bookingId={}, userEmail={}", bookingId, userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException("user.not.found", HttpStatus.NOT_FOUND));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!booking.getUser().getId().equals(user.getId())) {
            throw new AppException("booking.payment.not.owner", HttpStatus.FORBIDDEN);
        }

        BookingStatus currentStatus = booking.getStatus();
        if (currentStatus == BookingStatus.PAID) {
            throw new AppException("booking.already.paid", HttpStatus.BAD_REQUEST);
        }

        if (currentStatus != BookingStatus.APPROVED) {
            throw new AppException("booking.payment.not.approved", HttpStatus.BAD_REQUEST);
        }

        booking.setStatus(BookingStatus.PAID);
        Booking savedBooking = bookingRepository.save(booking);

        Payment payment = Payment.builder()
                .booking(savedBooking)
                .amount(savedBooking.getTotalPrice())
                .paymentMethod("MOCK")
                .status("COMPLETED")
                .paidAt(LocalDateTime.now(clock))
                .transactionId("MOCK-" + System.currentTimeMillis() + "-" + savedBooking.getId())
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        return paymentMapper.toPaymentResponse(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookingHistory(BookingSearchRequest request, String userEmail) {
        log.debug("[BookingService] Getting booking history for userEmail={}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException("user.not.found", HttpStatus.NOT_FOUND));

        if (request == null) {
            request = BookingSearchRequest.builder().build();
        }

        request.setUserId(user.getId());

        return searchBookings(request);
    }

    @Override
    @Transactional
    public Booking changeStatus(Long bookingId, String newStatus) {
        BookingStatus normalizedStatus = normalizeStatus(newStatus);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingStatus previousStatus = booking.getStatus();

        if (normalizedStatus == previousStatus) {
            return booking;
        }

        booking.setStatus(normalizedStatus);
        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        Locale locale = toLocale(savedBooking.getUser().getLanguage());
        String html = emailTemplateService.renderBookingStatusChanged(
                savedBooking, previousStatus != null ? previousStatus.name() : null, locale);
        String subject = messageSource.getMessage(
                "email.booking.status.subject",
                new Object[]{savedBooking.getId()},
                locale);
        emailService.sendHtmlEmail(
                savedBooking.getUser().getEmail(),
                subject,
                html);
        return savedBooking;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookingHistory(String userEmail, BookingHistoryRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException("user.not.found", HttpStatus.NOT_FOUND));

        Sort.Direction direction = "ASC".equalsIgnoreCase(request.getSortDir())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String rawSortBy = (request.getSortBy() != null) ? request.getSortBy().trim() : "createdAt";
        String sortBy = ALLOWED_SORT_FIELDS.contains(rawSortBy) ? rawSortBy : "createdAt";

        int page = Math.max(0, request.getPage());
        int size = Math.min(Math.max(1, request.getSize()), 100);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Booking> bookingPage = bookingRepository.findByUserId(user.getId(), pageable);
        Page<BookingResponse> dtoPage = bookingPage.map(bookingMapper::toBookingResponse);
        return PageResponse.fromPage(dtoPage);
    }

    public BigDecimal calculateTotalPrice(Space space, LocalDateTime startTime, LocalDateTime endTime) {
        BigDecimal price = space.getPrice() != null ? space.getPrice() : BigDecimal.ZERO;
        PriceUnit priceUnit = PriceUnit.fromString(space.getPriceUnit());

        long minutes = Duration.between(startTime, endTime).toMinutes();

        switch (priceUnit) {
            case HOUR:
                BigDecimal hours = BigDecimal.valueOf(minutes)
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                return price.multiply(hours).setScale(2, RoundingMode.HALF_UP);

            case DAY:
                long days = (long) Math.ceil(minutes / (24.0 * 60.0));
                days = Math.max(1, days);
                return price.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);

            case MONTH:
                long months = (long) Math.ceil(minutes / (30.0 * 24.0 * 60.0));
                months = Math.max(1, months);
                return price.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);

            default:
                BigDecimal defaultHours = BigDecimal.valueOf(minutes)
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                return price.multiply(defaultHours).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private BookingStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new AppException("booking.status.required", HttpStatus.BAD_REQUEST);
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException("booking.status.invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private Locale toLocale(String language) {
        if (!StringUtils.hasText(language)) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(language.replace('_', '-'));
    }
}

