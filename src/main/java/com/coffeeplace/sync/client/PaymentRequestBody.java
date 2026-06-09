package com.coffeeplace.sync.client;

public record PaymentRequestBody(
        String coffeeType,
        double price,
        String currency,
        String loyaltyCardId
) {}
