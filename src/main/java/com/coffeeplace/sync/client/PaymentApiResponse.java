package com.coffeeplace.sync.client;

public record PaymentApiResponse(
        String paymentId,
        String coffeeType,
        double price,
        String currency,
        String loyaltyCardId
) {}
