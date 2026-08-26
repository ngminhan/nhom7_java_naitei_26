package com.nhom7.coworkingspace.mapper;

import com.nhom7.coworkingspace.dto.response.PaymentResponse;
import com.nhom7.coworkingspace.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "bookingId", source = "booking.id")
    PaymentResponse toPaymentResponse(Payment payment);
}
