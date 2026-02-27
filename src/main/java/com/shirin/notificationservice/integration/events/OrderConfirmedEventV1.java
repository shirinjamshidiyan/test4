package com.shirin.notificationservice.integration.events;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderConfirmedEventV1(
        @NotNull UUID eventId, //for idempotency
        @NotNull UUID orderId,
        @NotNull
        @PastOrPresent Instant occurredAt,
        @Min(1) int version, //for schema evolution
        @NotBlank
        @Email
        String customerEmail,
        @NotBlank String customerName,
        @NotNull
        @PositiveOrZero
        @Digits(integer = 15, fraction = 2)
        BigDecimal totalAmount,

        @Pattern(regexp = "^[A-Z]{3}$")
        @NotBlank
        String currency,
        @NotEmpty
        List<@Valid  OrderItem> items,
        @NotBlank String billingAddress,
        @NotBlank String shippingAddress

) {
    public record OrderItem(
            @NotBlank String sku,
            @NotBlank String name,
            @Positive int quantity,
            @NotNull
            @PositiveOrZero
            @Digits(integer = 15, fraction = 2)
            BigDecimal unitPrice,
            @NotNull
            @PositiveOrZero
            @Digits(integer = 15, fraction = 2)
            BigDecimal lineTotal
    ) {}
}
